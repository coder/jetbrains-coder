package com.coder.gateway.views.steps

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.icons.CoderIcons
import com.coder.gateway.models.CoderWorkspacesWizardModel
import com.coder.gateway.models.WorkspaceAgentModel
import com.coder.gateway.sdk.Arch
import com.coder.gateway.sdk.CoderCLIManager
import com.coder.gateway.sdk.CoderRestClientService
import com.coder.gateway.sdk.OS
import com.coder.gateway.sdk.humanizeDuration
import com.coder.gateway.sdk.isCancellation
import com.coder.gateway.sdk.isWorkerTimeout
import com.coder.gateway.sdk.suspendingRetryWithExponentialBackOff
import com.coder.gateway.sdk.toURL
import com.coder.gateway.sdk.withPath
import com.coder.gateway.toWorkspaceParams
import com.coder.gateway.views.LazyBrowserLink
import com.coder.gateway.withConfigDirectory
import com.coder.gateway.withName
import com.coder.gateway.withProjectPath
import com.coder.gateway.withWebTerminalLink
import com.coder.gateway.withWorkspaceHostname
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.remote.AuthType
import com.intellij.remote.RemoteCredentialsHolder
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.jetbrains.gateway.api.GatewayUI
import com.jetbrains.gateway.ssh.CachingProductsJsonWrapper
import com.jetbrains.gateway.ssh.DeployTargetOS
import com.jetbrains.gateway.ssh.DeployTargetOS.OSArch
import com.jetbrains.gateway.ssh.DeployTargetOS.OSKind
import com.jetbrains.gateway.ssh.HighLevelHostAccessor
import com.jetbrains.gateway.ssh.IdeStatus
import com.jetbrains.gateway.ssh.IdeWithStatus
import com.jetbrains.gateway.ssh.IntelliJPlatformProduct
import com.jetbrains.gateway.ssh.deploy.DeployException
import com.jetbrains.gateway.ssh.util.validateRemotePath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.schmizz.sshj.common.SSHException
import net.schmizz.sshj.connection.ConnectionException
import java.awt.Component
import java.awt.FlowLayout
import java.util.Locale
import java.util.concurrent.TimeoutException
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent

class CoderLocateRemoteProjectStepView(private val setNextButtonEnabled: (Boolean) -> Unit) : CoderWorkspacesWizardStep, Disposable {
    private val cs = CoroutineScope(Dispatchers.Main)
    private val clientService: CoderRestClientService = ApplicationManager.getApplication().getService(CoderRestClientService::class.java)

    private var ideComboBoxModel = DefaultComboBoxModel<IdeWithStatus>()

    private lateinit var titleLabel: JLabel
    private lateinit var cbIDE: IDEComboBox
    private lateinit var cbIDEComment: JLabel
    private var tfProject = JBTextField()
    private lateinit var terminalLink: LazyBrowserLink
    private lateinit var ideResolvingJob: Job
    private val pathValidationJobs = MergingUpdateQueue("remote-path-validation", 1000, true, tfProject)

    override val component = panel {
        row {
            titleLabel = label("").applyToComponent {
                font = JBFont.h3().asBold()
                icon = CoderIcons.LOGO_16
            }.component
        }.topGap(TopGap.SMALL)
        row {
            label("IDE:")
            cbIDE = cell(IDEComboBox(ideComboBoxModel).apply {
                addActionListener {
                    setNextButtonEnabled(this.selectedItem != null)
                    ApplicationManager.getApplication().invokeLater {
                        logger.info("Selected IDE: ${this.selectedItem}")
                        cbIDEComment.foreground = UIUtil.getContextHelpForeground()
                        when (this.selectedItem?.status) {
                            IdeStatus.ALREADY_INSTALLED ->
                                cbIDEComment.text =
                                    CoderGatewayBundle.message("gateway.connector.view.coder.remoteproject.ide.installed.comment")

                            IdeStatus.DOWNLOAD ->
                                cbIDEComment.text =
                                    CoderGatewayBundle.message("gateway.connector.view.coder.remoteproject.ide.download.comment")

                            else ->
                                cbIDEComment.text =
                                    CoderGatewayBundle.message("gateway.connector.view.coder.remoteproject.ide.none.comment")
                        }
                    }
                }
            }).resizableColumn().align(AlignX.FILL).component
        }.topGap(TopGap.NONE).bottomGap(BottomGap.NONE).layout(RowLayout.PARENT_GRID)
        row {
            cell() // Empty cell for alignment.
            cbIDEComment = cell(
                ComponentPanelBuilder.createCommentComponent(
                    CoderGatewayBundle.message("gateway.connector.view.coder.remoteproject.ide.none.comment"),
                    false, -1, true
                )
            ).resizableColumn().align(AlignX.FILL).component
        }.topGap(TopGap.NONE).bottomGap(BottomGap.NONE).layout(RowLayout.PARENT_GRID)
        row {
            label("Project directory:")
            cell(tfProject).resizableColumn().align(AlignX.FILL).component
        }.topGap(TopGap.NONE).bottomGap(BottomGap.NONE).layout(RowLayout.PARENT_GRID)
        row {
            cell() // Empty cell for alignment.
            terminalLink = cell(
                LazyBrowserLink(
                    CoderIcons.OPEN_TERMINAL,
                    "Open Terminal"
                )
            ).component
        }.topGap(TopGap.NONE).layout(RowLayout.PARENT_GRID)
        gap(RightGap.SMALL)
    }.apply {
        background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
        border = JBUI.Borders.empty(0, 16)
    }

    override val previousActionText = IdeBundle.message("button.back")
    override val nextActionText = CoderGatewayBundle.message("gateway.connector.view.coder.remoteproject.next.text")

    override fun onInit(wizardModel: CoderWorkspacesWizardModel) {
        // Clear contents from the last attempt if any.
        cbIDEComment.foreground = UIUtil.getContextHelpForeground()
        cbIDEComment.text = CoderGatewayBundle.message("gateway.connector.view.coder.remoteproject.ide.none.comment")
        cbIDE.renderer = IDECellRenderer(CoderGatewayBundle.message("gateway.connector.view.coder.retrieve-ides"))
        ideComboBoxModel.removeAllElements()
        setNextButtonEnabled(false)

        val deploymentURL = wizardModel.coderURL.toURL()
        val selectedWorkspace = wizardModel.selectedWorkspace
        if (selectedWorkspace == null) {
            // TODO: Should be impossible, tweak the types/flow to enforce this.
            logger.warn("No workspace was selected. Please go back to the previous step and select a workspace")
            return
        }

        tfProject.text = if (selectedWorkspace.homeDirectory.isNullOrBlank()) "/home" else selectedWorkspace.homeDirectory
        titleLabel.text = CoderGatewayBundle.message("gateway.connector.view.coder.remoteproject.choose.text", selectedWorkspace.name)
        terminalLink.url = clientService.client.url.withPath("/@${clientService.me.username}/${selectedWorkspace.name}/terminal").toString()

        ideResolvingJob = cs.launch {
            try {
                val ides = suspendingRetryWithExponentialBackOff(
                    action = { attempt ->
                        logger.info("Retrieving IDEs... (attempt $attempt)")
                        if (attempt > 1) {
                            cbIDE.renderer = IDECellRenderer(CoderGatewayBundle.message("gateway.connector.view.coder.retrieve.ides.retry", attempt))
                        }
                        val executor = createRemoteExecutor(CoderCLIManager.getHostName(deploymentURL, selectedWorkspace))
                        if (ComponentValidator.getInstance(tfProject).isEmpty) {
                            installRemotePathValidator(executor)
                        }
                        retrieveIDEs(executor, selectedWorkspace)
                    },
                    retryIf = {
                        it is ConnectionException || it is TimeoutException
                                || it is SSHException || it is DeployException
                    },
                    onException = { attempt, nextMs, e ->
                        logger.error("Failed to retrieve IDEs (attempt $attempt; will retry in $nextMs ms)")
                        cbIDEComment.foreground = UIUtil.getErrorForeground()
                        cbIDEComment.text =
                            if (isWorkerTimeout(e)) "Failed to upload worker binary...it may have timed out.  Check the command log for more details."
                            else e.message ?: CoderGatewayBundle.message("gateway.connector.no-details")
                    },
                    onCountdown = { remainingMs ->
                        cbIDE.renderer = IDECellRenderer(CoderGatewayBundle.message("gateway.connector.view.coder.retrieve-ides.failed.retry", humanizeDuration(remainingMs)))
                    },
                )
                withContext(Dispatchers.Main) {
                    ideComboBoxModel.addAll(ides)
                    cbIDE.selectedIndex = 0
                }
            } catch (e: Exception) {
                if (isCancellation(e)) {
                    logger.info("Connection canceled due to ${e.javaClass}")
                } else {
                    logger.error("Failed to retrieve IDEs (will not retry)", e)
                    cbIDEComment.foreground = UIUtil.getErrorForeground()
                    cbIDEComment.text = e.message ?: CoderGatewayBundle.message("gateway.connector.no-details")
                    cbIDE.renderer = IDECellRenderer(CoderGatewayBundle.message("gateway.connector.view.coder.retrieve-ides.failed"), UIUtil.getBalloonErrorIcon())
                }
            }
        }
    }

    private fun installRemotePathValidator(executor: HighLevelHostAccessor) {
        val disposable = Disposer.newDisposable(ApplicationManager.getApplication(), CoderLocateRemoteProjectStepView::class.java.name)
        ComponentValidator(disposable).installOn(tfProject)

        tfProject.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) {
                pathValidationJobs.queue(Update.create("validate-remote-path") {
                    runBlocking {
                        try {
                            val isPathPresent = validateRemotePath(tfProject.text, executor)
                            if (isPathPresent.pathOrNull == null) {
                                ComponentValidator.getInstance(tfProject).ifPresent {
                                    it.updateInfo(ValidationInfo("Can't find directory: ${tfProject.text}", tfProject))
                                }
                            } else {
                                ComponentValidator.getInstance(tfProject).ifPresent {
                                    it.updateInfo(null)
                                }
                            }
                        } catch (e: Exception) {
                            ComponentValidator.getInstance(tfProject).ifPresent {
                                it.updateInfo(ValidationInfo("Can't validate directory: ${tfProject.text}", tfProject))
                            }
                        }
                    }
                })
            }
        })
    }

    private suspend fun createRemoteExecutor(host: String): HighLevelHostAccessor {
        return HighLevelHostAccessor.create(
            RemoteCredentialsHolder().apply {
                setHost(host)
                userName = "coder"
                port = 22
                authType = AuthType.OPEN_SSH
            },
            true
        )
    }

    private suspend fun retrieveIDEs(executor: HighLevelHostAccessor, selectedWorkspace: WorkspaceAgentModel): List<IdeWithStatus> {
        logger.info("Retrieving available IDE's for ${selectedWorkspace.name} workspace...")
        val workspaceOS = if (selectedWorkspace.agentOS != null && selectedWorkspace.agentArch != null) toDeployedOS(selectedWorkspace.agentOS, selectedWorkspace.agentArch) else withContext(Dispatchers.IO) {
            executor.guessOs()
        }

        logger.info("Resolved OS and Arch for ${selectedWorkspace.name} is: $workspaceOS")
        val installedIdesJob = cs.async(Dispatchers.IO) {
            executor.getInstalledIDEs().map { ide -> IdeWithStatus(ide.product, ide.buildNumber, IdeStatus.ALREADY_INSTALLED, null, ide.pathToIde, ide.presentableVersion, ide.remoteDevType) }
        }
        val idesWithStatusJob = cs.async(Dispatchers.IO) {
            IntelliJPlatformProduct.values()
                .filter { it.showInGateway }
                .flatMap { CachingProductsJsonWrapper.getInstance().getAvailableIdes(it, workspaceOS) }
                .map { ide -> IdeWithStatus(ide.product, ide.buildNumber, IdeStatus.DOWNLOAD, ide.download, null, ide.presentableVersion, ide.remoteDevType) }
        }

        val installedIdes = installedIdesJob.await()
        val idesWithStatus = idesWithStatusJob.await()
        if (installedIdes.isEmpty()) {
            logger.info("No IDE is installed in workspace ${selectedWorkspace.name}")
        }
        if (idesWithStatus.isEmpty()) {
            logger.warn("Could not resolve any IDE for workspace ${selectedWorkspace.name}, probably $workspaceOS is not supported by Gateway")
        }
        return installedIdes + idesWithStatus
    }

    private fun toDeployedOS(os: OS, arch: Arch): DeployTargetOS {
        return when (os) {
            OS.LINUX -> when (arch) {
                Arch.AMD64 -> DeployTargetOS(OSKind.Linux, OSArch.X86_64)
                Arch.ARM64 -> DeployTargetOS(OSKind.Linux, OSArch.ARM_64)
                Arch.ARMV7 -> DeployTargetOS(OSKind.Linux, OSArch.UNKNOWN)
            }

            OS.WINDOWS -> when (arch) {
                Arch.AMD64 -> DeployTargetOS(OSKind.Windows, OSArch.X86_64)
                Arch.ARM64 -> DeployTargetOS(OSKind.Windows, OSArch.ARM_64)
                Arch.ARMV7 -> DeployTargetOS(OSKind.Windows, OSArch.UNKNOWN)
            }

            OS.MAC -> when (arch) {
                Arch.AMD64 -> DeployTargetOS(OSKind.MacOs, OSArch.X86_64)
                Arch.ARM64 -> DeployTargetOS(OSKind.MacOs, OSArch.ARM_64)
                Arch.ARMV7 -> DeployTargetOS(OSKind.MacOs, OSArch.UNKNOWN)
            }
        }
    }

    override fun onNext(wizardModel: CoderWorkspacesWizardModel): Boolean {
        val selectedIDE = cbIDE.selectedItem ?: return false
        logger.info("Going to launch the IDE")
        val deploymentURL = wizardModel.coderURL.toURL()
        val selectedWorkspace = wizardModel.selectedWorkspace
        if (selectedWorkspace == null) {
            // TODO: Should be impossible, tweak the types/flow to enforce this.
            logger.warn("No workspace was selected. Please go back to the previous step and select a workspace")
            return false
        }
        cs.launch {
            GatewayUI.getInstance().connect(
                selectedIDE
                    .toWorkspaceParams()
                    .withWorkspaceHostname(CoderCLIManager.getHostName(deploymentURL, selectedWorkspace))
                    .withProjectPath(tfProject.text)
                    .withWebTerminalLink("${terminalLink.url}")
                    .withConfigDirectory(wizardModel.configDirectory)
                    .withName(selectedWorkspace.name)
            )
        }
        return true
    }

    override fun onPrevious() {
        super.onPrevious()
        logger.info("Going back to Workspace view")
        cs.launch {
            ideResolvingJob.cancelAndJoin()
        }
    }

    override fun dispose() {
        cs.cancel()
    }

    private class IDEComboBox(model: ComboBoxModel<IdeWithStatus>) : ComboBox<IdeWithStatus>(model) {

        init {
            putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true)
        }

        override fun getSelectedItem(): IdeWithStatus? {
            return super.getSelectedItem() as IdeWithStatus?
        }
    }

    private class IDECellRenderer(message: String, cellIcon: Icon = AnimatedIcon.Default.INSTANCE) : ListCellRenderer<IdeWithStatus> {
        private val loadingComponentRenderer: ListCellRenderer<IdeWithStatus> = object : ColoredListCellRenderer<IdeWithStatus>() {
            override fun customizeCellRenderer(list: JList<out IdeWithStatus>, value: IdeWithStatus?, index: Int, isSelected: Boolean, cellHasFocus: Boolean) {
                background = UIUtil.getListBackground(isSelected, cellHasFocus)
                icon = cellIcon
                append(message)
            }
        }

        override fun getListCellRendererComponent(list: JList<out IdeWithStatus>?, ideWithStatus: IdeWithStatus?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
            return if (ideWithStatus == null && index == -1) {
                loadingComponentRenderer.getListCellRendererComponent(list, null, -1, isSelected, cellHasFocus)
            } else if (ideWithStatus != null) {
                JPanel().apply {
                    layout = FlowLayout(FlowLayout.LEFT)
                    add(JLabel(ideWithStatus.product.ideName, ideWithStatus.product.icon, SwingConstants.LEFT))
                    add(JLabel("${ideWithStatus.product.productCode} ${ideWithStatus.presentableVersion} ${ideWithStatus.buildNumber} | ${ideWithStatus.status.name.lowercase(Locale.getDefault())}").apply {
                        foreground = UIUtil.getLabelDisabledForeground()
                    })
                    background = UIUtil.getListBackground(isSelected, cellHasFocus)
                }
            } else {
                panel { }
            }
        }
    }

    companion object {
        val logger = Logger.getInstance(CoderLocateRemoteProjectStepView::class.java.simpleName)
    }
}
