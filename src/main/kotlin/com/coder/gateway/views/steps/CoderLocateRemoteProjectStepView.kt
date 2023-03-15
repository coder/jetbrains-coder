package com.coder.gateway.views.steps

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.icons.CoderIcons
import com.coder.gateway.models.CoderWorkspacesWizardModel
import com.coder.gateway.models.WorkspaceAgentModel
import com.coder.gateway.sdk.Arch
import com.coder.gateway.sdk.CoderRestClientService
import com.coder.gateway.sdk.OS
import com.coder.gateway.sdk.withPath
import com.coder.gateway.toWorkspaceParams
import com.coder.gateway.views.LazyBrowserLink
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
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.remote.AuthType
import com.intellij.remote.RemoteCredentialsHolder
import com.intellij.ssh.SshException
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBFont
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
import com.jetbrains.gateway.ssh.util.validateRemotePath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.withTimeout
import kotlinx.coroutines.withContext
import java.awt.Component
import java.awt.FlowLayout
import java.time.Duration
import java.util.*
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent

class CoderLocateRemoteProjectStepView(private val disableNextAction: () -> Unit) : CoderWorkspacesWizardStep, Disposable {
    private val cs = CoroutineScope(Dispatchers.Main)
    private val coderClient: CoderRestClientService = ApplicationManager.getApplication().getService(CoderRestClientService::class.java)

    private var ideComboBoxModel = DefaultComboBoxModel<IdeWithStatus>()

    private lateinit var titleLabel: JLabel
    private lateinit var wizard: CoderWorkspacesWizardModel
    private lateinit var cbIDE: IDEComboBox
    private var tfProject = JBTextField()
    private lateinit var terminalLink: LazyBrowserLink
    private lateinit var ideResolvingJob: Job
    private val pathValidationJobs = MergingUpdateQueue("remote-path-validation", 1000, true, tfProject)

    override val component = panel {
        indent {
            row {
                titleLabel = label("").applyToComponent {
                    font = JBFont.h3().asBold()
                    icon = CoderIcons.LOGO_16
                }.component
            }.bottomGap(BottomGap.MEDIUM)

            row {
                label("IDE:")
                cbIDE = cell(IDEComboBox(ideComboBoxModel).apply {
                    renderer = IDECellRenderer()
                }).resizableColumn().align(AlignX.FILL).comment("The IDE will be downloaded from jetbrains.com").component
                cell()
            }.topGap(TopGap.NONE).layout(RowLayout.PARENT_GRID)

            row {
                label("Project directory:")
                cell(tfProject).resizableColumn().align(AlignX.FILL).component
                cell()
            }.topGap(TopGap.NONE).bottomGap(BottomGap.NONE).layout(RowLayout.PARENT_GRID)
            row {
                cell()
                terminalLink = cell(
                    LazyBrowserLink(
                        CoderIcons.OPEN_TERMINAL,
                        "Open Terminal"
                    )
                ).component
            }.topGap(TopGap.NONE).layout(RowLayout.PARENT_GRID)
        }
    }.apply { background = WelcomeScreenUIManager.getMainAssociatedComponentBackground() }

    override val previousActionText = IdeBundle.message("button.back")
    override val nextActionText = CoderGatewayBundle.message("gateway.connector.view.coder.remoteproject.next.text")

    override fun onInit(wizardModel: CoderWorkspacesWizardModel) {
        cbIDE.renderer = IDECellRenderer()
        ideComboBoxModel.removeAllElements()
        wizard = wizardModel
        val selectedWorkspace = wizardModel.selectedWorkspace
        if (selectedWorkspace == null) {
            logger.warn("No workspace was selected. Please go back to the previous step and select a Coder Workspace")
            return
        }

        tfProject.text = if (selectedWorkspace.homeDirectory.isNullOrBlank()) "/home" else selectedWorkspace.homeDirectory
        titleLabel.text = CoderGatewayBundle.message("gateway.connector.view.coder.remoteproject.choose.text", selectedWorkspace.name)
        terminalLink.url = coderClient.coderURL.withPath("/@${coderClient.me.username}/${selectedWorkspace.name}/terminal").toString()

        ideResolvingJob = cs.launch {
            try {
                val executor = withTimeout(Duration.ofSeconds(60)) { createRemoteExecutor(selectedWorkspace) }
                retrieveIDES(executor, selectedWorkspace)
                if (ComponentValidator.getInstance(tfProject).isEmpty) {
                    installRemotePathValidator(executor)
                }
            } catch (e: Exception) {
                when (e) {
                    is InterruptedException -> Unit
                    is CancellationException -> Unit
                    is TimeoutCancellationException,
                    is SshException -> {
                        logger.error("Can't connect to workspace ${selectedWorkspace.name}. Reason: $e")
                        withContext(Dispatchers.Main) {
                            disableNextAction()
                            cbIDE.renderer = object : ColoredListCellRenderer<IdeWithStatus>() {
                                override fun customizeCellRenderer(list: JList<out IdeWithStatus>, value: IdeWithStatus?, index: Int, isSelected: Boolean, cellHasFocus: Boolean) {
                                    background = UIUtil.getListBackground(isSelected, cellHasFocus)
                                    icon = UIUtil.getBalloonErrorIcon()
                                    append(CoderGatewayBundle.message("gateway.connector.view.coder.remoteproject.ssh.error.text"))
                                }
                            }
                        }
                    }

                    else -> {
                        logger.error("Could not resolve any IDE for workspace ${selectedWorkspace.name}. Reason: $e")
                        withContext(Dispatchers.Main) {
                            disableNextAction()
                            cbIDE.renderer = object : ColoredListCellRenderer<IdeWithStatus>() {
                                override fun customizeCellRenderer(list: JList<out IdeWithStatus>, value: IdeWithStatus?, index: Int, isSelected: Boolean, cellHasFocus: Boolean) {
                                    background = UIUtil.getListBackground(isSelected, cellHasFocus)
                                    icon = UIUtil.getBalloonErrorIcon()
                                    append(CoderGatewayBundle.message("gateway.connector.view.coder.remoteproject.ide.error.text"))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun installRemotePathValidator(executor: HighLevelHostAccessor) {
        var disposable = Disposer.newDisposable(ApplicationManager.getApplication(), CoderLocateRemoteProjectStepView.javaClass.name)
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

    private suspend fun createRemoteExecutor(selectedWorkspace: WorkspaceAgentModel): HighLevelHostAccessor {
        return HighLevelHostAccessor.create(
            RemoteCredentialsHolder().apply {
                setHost("coder.${selectedWorkspace.name}")
                userName = "coder"
                port = 22
                authType = AuthType.OPEN_SSH
            },
            true
        )
    }

    private suspend fun retrieveIDES(executor: HighLevelHostAccessor, selectedWorkspace: WorkspaceAgentModel) {
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
        } else {
            withContext(Dispatchers.Main) {
                ideComboBoxModel.addAll(installedIdes)
                cbIDE.selectedIndex = 0
            }
        }

        if (idesWithStatus.isEmpty()) {
            logger.warn("Could not resolve any IDE for workspace ${selectedWorkspace.name}, probably $workspaceOS is not supported by Gateway")
        } else {
            withContext(Dispatchers.Main) {
                ideComboBoxModel.addAll(idesWithStatus)
                cbIDE.selectedIndex = 0
            }
        }
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
        cs.launch {
            GatewayUI.getInstance().connect(
                selectedIDE
                    .toWorkspaceParams()
                    .withWorkspaceHostname("coder.${wizardModel.selectedWorkspace?.name}")
                    .withProjectPath(tfProject.text)
                    .withWebTerminalLink("${terminalLink.url}")
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

    private class IDECellRenderer : ListCellRenderer<IdeWithStatus> {
        private val loadingComponentRenderer: ListCellRenderer<IdeWithStatus> = object : ColoredListCellRenderer<IdeWithStatus>() {
            override fun customizeCellRenderer(list: JList<out IdeWithStatus>, value: IdeWithStatus?, index: Int, isSelected: Boolean, cellHasFocus: Boolean) {
                background = UIUtil.getListBackground(isSelected, cellHasFocus)
                icon = AnimatedIcon.Default.INSTANCE
                append(CoderGatewayBundle.message("gateway.connector.view.coder.remoteproject.loading.text"))
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
