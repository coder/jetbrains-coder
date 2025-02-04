package com.coder.gateway.views.steps

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.cli.CoderCLIManager
import com.coder.gateway.icons.CoderIcons
import com.coder.gateway.models.WorkspaceProjectIDE
import com.coder.gateway.models.toIdeWithStatus
import com.coder.gateway.models.withWorkspaceProject
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceAgent
import com.coder.gateway.services.CoderSettingsService
import com.coder.gateway.util.Arch
import com.coder.gateway.util.OS
import com.coder.gateway.util.humanizeDuration
import com.coder.gateway.util.isCancellation
import com.coder.gateway.util.isWorkerTimeout
import com.coder.gateway.util.suspendingRetryWithExponentialBackOff
import com.coder.gateway.util.withPath
import com.coder.gateway.util.withoutNull
import com.coder.gateway.views.LazyBrowserLink
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.schmizz.sshj.common.SSHException
import net.schmizz.sshj.connection.ConnectionException
import java.awt.Component
import java.awt.Dimension
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

// Just extracting the way we display the IDE info into a helper function.
private fun displayIdeWithStatus(ideWithStatus: IdeWithStatus): String = "${ideWithStatus.product.productCode} ${ideWithStatus.presentableVersion} ${ideWithStatus.buildNumber} | ${ideWithStatus.status.name.lowercase(
    Locale.getDefault(),
)}"

/**
 * View for a single workspace.  In particular, show available IDEs and a button
 * to select an IDE and project to run on the workspace.
 */
class CoderWorkspaceProjectIDEStepView(
    private val showTitle: Boolean = true,
) : CoderWizardStep<WorkspaceProjectIDE>(
    CoderGatewayBundle.message("gateway.connector.view.coder.remoteproject.next.text"),
) {
    private val settings: CoderSettingsService = service<CoderSettingsService>()

    private val cs = CoroutineScope(Dispatchers.IO)
    private var ideComboBoxModel = DefaultComboBoxModel<IdeWithStatus>()
    private var state: CoderWorkspacesStepSelection? = null

    private lateinit var titleLabel: JLabel
    private lateinit var cbIDE: IDEComboBox
    private lateinit var cbIDEComment: JLabel
    private var tfProject = JBTextField()
    private lateinit var terminalLink: LazyBrowserLink
    private var ideResolvingJob: Job? = null
    private val pathValidationJobs = MergingUpdateQueue("remote-path-validation", 1000, true, tfProject)

    private val component =
        panel {
            row {
                titleLabel =
                    label("").applyToComponent {
                        font = JBFont.h3().asBold()
                        icon = CoderIcons.LOGO_16
                    }.component
            }.topGap(TopGap.SMALL).bottomGap(BottomGap.NONE)
            row {
                label("IDE:")
                cbIDE =
                    cell(
                        IDEComboBox(ideComboBoxModel).apply {
                            addActionListener {
                                nextButton.isEnabled = this.selectedItem != null
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
                        },
                    ).resizableColumn().align(AlignX.FILL).component
            }.topGap(TopGap.SMALL).bottomGap(BottomGap.NONE).layout(RowLayout.PARENT_GRID)
            row {
                cell() // Empty cell for alignment.
                cbIDEComment =
                    cell(
                        ComponentPanelBuilder.createCommentComponent(
                            CoderGatewayBundle.message("gateway.connector.view.coder.remoteproject.ide.none.comment"),
                            false,
                            -1,
                            true,
                        ),
                    ).resizableColumn().align(AlignX.FILL).component
            }.topGap(TopGap.NONE).bottomGap(BottomGap.NONE).layout(RowLayout.PARENT_GRID)
            row {
                label("Project directory:")
                cell(tfProject).resizableColumn().align(AlignX.FILL).applyToComponent {
                    minimumSize = Dimension(520, -1)
                }.component
            }.topGap(TopGap.NONE).bottomGap(BottomGap.NONE).layout(RowLayout.PARENT_GRID)
            row {
                cell() // Empty cell for alignment.
                terminalLink =
                    cell(
                        LazyBrowserLink(
                            CoderIcons.OPEN_TERMINAL,
                            "Open Terminal",
                        ),
                    ).component
            }.topGap(TopGap.NONE).layout(RowLayout.PARENT_GRID)
            gap(RightGap.SMALL)
        }.apply {
            background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
            border = JBUI.Borders.empty(0, 16)
        }

    init {
        addToCenter(component)
    }

    /**
     * Query the workspaces for IDEs.
     */
    fun init(data: CoderWorkspacesStepSelection) {
        // Clear contents from the last run, if any.
        cbIDEComment.foreground = UIUtil.getContextHelpForeground()
        cbIDEComment.text = CoderGatewayBundle.message("gateway.connector.view.coder.remoteproject.ide.none.comment")
        ideComboBoxModel.removeAllElements()

        // We use this when returning the connection params from data().
        state = data
        val name = CoderCLIManager.getWorkspaceParts(data.workspace, data.agent)
        logger.info("Initializing workspace step for $name")

        val homeDirectory = data.agent.expandedDirectory ?: data.agent.directory
        tfProject.text = if (homeDirectory.isNullOrBlank()) "/home" else homeDirectory
        titleLabel.text = CoderGatewayBundle.message("gateway.connector.view.coder.remoteproject.choose.text", name)
        titleLabel.isVisible = showTitle
        terminalLink.url = data.client.url.withPath("/$name/terminal").toString()

        ideResolvingJob =
            cs.launch(ModalityState.current().asContextElement()) {
                try {
                    logger.info("Configuring Coder CLI...")
                    cbIDE.renderer = IDECellRenderer("Configuring Coder CLI...")
                    withContext(Dispatchers.IO) {
                        if (data.cliManager.features.wildcardSSH) {
                            data.cliManager.configSsh(emptySet(), data.client.me)
                        } else {
                            data.cliManager.configSsh(data.client.withAgents(data.workspaces), data.client.me)
                        }
                    }

                    val ides =
                        suspendingRetryWithExponentialBackOff(
                            action = { attempt ->
                                logger.info("Connecting with SSH and uploading worker if missing... (attempt $attempt)")
                                cbIDE.renderer =
                                    if (attempt > 1) {
                                        IDECellRenderer(
                                            CoderGatewayBundle.message("gateway.connector.view.coder.connect-ssh.retry", attempt),
                                        )
                                    } else {
                                        IDECellRenderer(CoderGatewayBundle.message("gateway.connector.view.coder.connect-ssh"))
                                    }
                                val executor = createRemoteExecutor(CoderCLIManager(data.client.url).getBackgroundHostName(data.workspace, data.client.me, data.agent))

                                if (ComponentValidator.getInstance(tfProject).isEmpty) {
                                    logger.info("Installing remote path validator...")
                                    installRemotePathValidator(executor)
                                }

                                logger.info("Retrieving IDEs... (attempt $attempt)")
                                cbIDE.renderer =
                                    if (attempt > 1) {
                                        IDECellRenderer(
                                            CoderGatewayBundle.message("gateway.connector.view.coder.retrieve-ides.retry", attempt),
                                        )
                                    } else {
                                        IDECellRenderer(CoderGatewayBundle.message("gateway.connector.view.coder.retrieve-ides"))
                                    }
                                retrieveIDEs(executor, data.workspace, data.agent)
                            },
                            retryIf = {
                                it is ConnectionException ||
                                    it is TimeoutException ||
                                    it is SSHException ||
                                    it is DeployException
                            },
                            onException = { attempt, nextMs, e ->
                                logger.error("Failed to retrieve IDEs (attempt $attempt; will retry in $nextMs ms)")
                                cbIDEComment.foreground = UIUtil.getErrorForeground()
                                cbIDEComment.text =
                                    if (isWorkerTimeout(e)) {
                                        "Failed to upload worker binary...it may have timed out.  Check the command log for more details."
                                    } else {
                                        e.message ?: e.javaClass.simpleName
                                    }
                            },
                            onCountdown = { remainingMs ->
                                cbIDE.renderer =
                                    IDECellRenderer(
                                        CoderGatewayBundle.message(
                                            "gateway.connector.view.coder.retrieve-ides.failed.retry",
                                            humanizeDuration(remainingMs),
                                        ),
                                    )
                            },
                        )

                    // Check the provided setting to see if there's a default IDE to set.
                    val defaultIde = ides.find { it ->
                        // Using contains on the displayable version of the ide means they can be as specific or as vague as they want
                        // CL 2023.3.6 233.15619.8 -> a specific Clion build
                        // CL 2023.3.6 -> a specific Clion version
                        // 2023.3.6 -> a specific version (some customers will only have one specific IDE in their list anyway)
                        if (settings.defaultIde.isEmpty()) {
                            false
                        } else {
                            displayIdeWithStatus(it).contains(settings.defaultIde)
                        }
                    }
                    val index = ides.indexOf(defaultIde ?: ides.firstOrNull())

                    withContext(Dispatchers.IO) {
                        ideComboBoxModel.addAll(ides)
                        cbIDE.selectedIndex = index
                    }
                } catch (e: Exception) {
                    if (isCancellation(e)) {
                        logger.info("Connection canceled due to ${e.javaClass.simpleName}")
                    } else {
                        logger.error("Failed to retrieve IDEs (will not retry)", e)
                        cbIDEComment.foreground = UIUtil.getErrorForeground()
                        cbIDEComment.text = e.message ?: e.javaClass.simpleName
                        cbIDE.renderer =
                            IDECellRenderer(
                                CoderGatewayBundle.message("gateway.connector.view.coder.retrieve-ides.failed"),
                                UIUtil.getBalloonErrorIcon(),
                            )
                    }
                }
            }
    }

    /**
     * Validate the remote path whenever it changes.
     */
    private fun installRemotePathValidator(executor: HighLevelHostAccessor) {
        val disposable = Disposer.newDisposable(ApplicationManager.getApplication(), CoderWorkspaceProjectIDEStepView::class.java.name)
        ComponentValidator(disposable).installOn(tfProject)

        tfProject.document.addDocumentListener(
            object : DocumentAdapter() {
                override fun textChanged(event: DocumentEvent) {
                    pathValidationJobs.queue(
                        Update.create("validate-remote-path") {
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
                        },
                    )
                }
            },
        )
    }

    /**
     * Connect to the remote worker via SSH.
     */
    private suspend fun createRemoteExecutor(host: String): HighLevelHostAccessor = HighLevelHostAccessor.create(
        RemoteCredentialsHolder().apply {
            setHost(host)
            userName = "coder"
            port = 22
            authType = AuthType.OPEN_SSH
        },
        true,
    )

    /**
     * Get a list of available IDEs.
     */
    private suspend fun retrieveIDEs(
        executor: HighLevelHostAccessor,
        workspace: Workspace,
        agent: WorkspaceAgent,
    ): List<IdeWithStatus> {
        val name = CoderCLIManager.getWorkspaceParts(workspace, agent)
        logger.info("Retrieving available IDEs for $name...")
        val workspaceOS =
            if (agent.operatingSystem != null && agent.architecture != null) {
                toDeployedOS(agent.operatingSystem, agent.architecture)
            } else {
                withContext(Dispatchers.IO) {
                    executor.guessOs()
                }
            }

        logger.info("Resolved OS and Arch for $name is: $workspaceOS")
        val installedIdesJob =
            cs.async(Dispatchers.IO) {
                executor.getInstalledIDEs().map { it.toIdeWithStatus() }
            }
        val idesWithStatusJob =
            cs.async(Dispatchers.IO) {
                IntelliJPlatformProduct.entries
                    .filter { it.showInGateway }
                    .flatMap { CachingProductsJsonWrapper.getInstance().getAvailableIdes(it, workspaceOS) }
                    .map { it.toIdeWithStatus() }
            }

        val installedIdes = installedIdesJob.await().sorted()
        val idesWithStatus = idesWithStatusJob.await().sorted()
        if (installedIdes.isEmpty()) {
            logger.info("No IDE is installed in $name")
        }
        if (idesWithStatus.isEmpty()) {
            logger.warn("Could not resolve any IDE for $name, probably $workspaceOS is not supported by Gateway")
        }
        return installedIdes + idesWithStatus
    }

    private fun toDeployedOS(
        os: OS,
        arch: Arch,
    ): DeployTargetOS = when (os) {
        OS.LINUX ->
            when (arch) {
                Arch.AMD64 -> DeployTargetOS(OSKind.Linux, OSArch.X86_64)
                Arch.ARM64 -> DeployTargetOS(OSKind.Linux, OSArch.ARM_64)
                Arch.ARMV7 -> DeployTargetOS(OSKind.Linux, OSArch.UNKNOWN)
            }

        OS.WINDOWS ->
            when (arch) {
                Arch.AMD64 -> DeployTargetOS(OSKind.Windows, OSArch.X86_64)
                Arch.ARM64 -> DeployTargetOS(OSKind.Windows, OSArch.ARM_64)
                Arch.ARMV7 -> DeployTargetOS(OSKind.Windows, OSArch.UNKNOWN)
            }

        OS.MAC ->
            when (arch) {
                Arch.AMD64 -> DeployTargetOS(OSKind.MacOs, OSArch.X86_64)
                Arch.ARM64 -> DeployTargetOS(OSKind.MacOs, OSArch.ARM_64)
                Arch.ARMV7 -> DeployTargetOS(OSKind.MacOs, OSArch.UNKNOWN)
            }
    }

    /**
     * Return the selected parameters.  Throw if not configured.
     */
    override fun data(): WorkspaceProjectIDE = withoutNull(cbIDE.selectedItem, state) { selectedIDE, state ->
        selectedIDE.withWorkspaceProject(
            name = CoderCLIManager.getWorkspaceParts(state.workspace, state.agent),
            hostname = CoderCLIManager(state.client.url).getHostName(state.workspace, state.client.me, state.agent),
            projectPath = tfProject.text,
            deploymentURL = state.client.url,
        )
    }

    override fun stop() {
        ideResolvingJob?.cancel()
    }

    override fun dispose() {
        stop()
        cs.cancel()
    }

    private class IDEComboBox(model: ComboBoxModel<IdeWithStatus>) : ComboBox<IdeWithStatus>(model) {
        init {
            putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true)
        }

        override fun getSelectedItem(): IdeWithStatus? = super.getSelectedItem() as IdeWithStatus?
    }

    private class IDECellRenderer(message: String, cellIcon: Icon = AnimatedIcon.Default.INSTANCE) : ListCellRenderer<IdeWithStatus> {
        private val loadingComponentRenderer: ListCellRenderer<IdeWithStatus> =
            object : ColoredListCellRenderer<IdeWithStatus>() {
                override fun customizeCellRenderer(
                    list: JList<out IdeWithStatus>,
                    value: IdeWithStatus?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean,
                ) {
                    background = UIUtil.getListBackground(isSelected, cellHasFocus)
                    icon = cellIcon
                    append(message)
                }
            }

        override fun getListCellRendererComponent(
            list: JList<out IdeWithStatus>?,
            ideWithStatus: IdeWithStatus?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component = if (ideWithStatus == null && index == -1) {
            loadingComponentRenderer.getListCellRendererComponent(list, null, -1, isSelected, cellHasFocus)
        } else if (ideWithStatus != null) {
            JPanel().apply {
                layout = FlowLayout(FlowLayout.LEFT)
                add(JLabel(ideWithStatus.product.ideName, ideWithStatus.product.icon, SwingConstants.LEFT))
                add(
                    JLabel(
                        displayIdeWithStatus(
                            ideWithStatus,
                        ),
                    ).apply {
                        foreground = UIUtil.getLabelDisabledForeground()
                    },
                )
                background = UIUtil.getListBackground(isSelected, cellHasFocus)
            }
        } else {
            panel { }
        }
    }

    companion object {
        val logger = Logger.getInstance(CoderWorkspaceProjectIDEStepView::class.java.simpleName)
    }
}
