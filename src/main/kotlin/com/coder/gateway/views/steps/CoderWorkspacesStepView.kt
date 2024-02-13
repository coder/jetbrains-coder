package com.coder.gateway.views.steps

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.CoderRemoteConnectionHandle
import com.coder.gateway.CoderSupportedVersions
import com.coder.gateway.icons.CoderIcons
import com.coder.gateway.models.CoderWorkspacesWizardModel
import com.coder.gateway.models.TokenSource
import com.coder.gateway.models.WorkspaceAgentModel
import com.coder.gateway.models.WorkspaceVersionStatus
import com.coder.gateway.sdk.CoderCLIManager
import com.coder.gateway.sdk.CoderRestClientService
import com.coder.gateway.util.SemVer
import com.coder.gateway.util.InvalidVersionException
import com.coder.gateway.util.OS
import com.coder.gateway.sdk.ResponseException
import com.coder.gateway.sdk.TemplateIconDownloader
import com.coder.gateway.sdk.ensureCLI
import com.coder.gateway.sdk.ex.AuthenticationResponseException
import com.coder.gateway.sdk.ex.TemplateResponseException
import com.coder.gateway.sdk.ex.WorkspaceResponseException
import com.coder.gateway.util.isCancellation
import com.coder.gateway.util.toURL
import com.coder.gateway.sdk.v2.models.WorkspaceStatus
import com.coder.gateway.sdk.v2.models.toAgentModels
import com.coder.gateway.services.CoderSettingsService
import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.rd.util.launchUnderBackgroundProgress
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.ui.setEmptyState
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.AnActionButton
import com.intellij.ui.RelativeFont
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.table.IconTableCellRenderer
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.zeroturnaround.exec.InvalidExitValueException
import java.awt.Component
import java.awt.Dimension
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import javax.swing.Icon
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

private const val CODER_URL_KEY = "coder-url"

private const val SESSION_TOKEN = "session-token"

class CoderWorkspacesStepView(val setNextButtonEnabled: (Boolean) -> Unit) : CoderWorkspacesWizardStep, Disposable {
    private val cs = CoroutineScope(Dispatchers.Main)
    private var localWizardModel = CoderWorkspacesWizardModel()
    private val clientService: CoderRestClientService = service()
    private var cliManager: CoderCLIManager? = null
    private val iconDownloader: TemplateIconDownloader = service()
    private val settings: CoderSettingsService = service()

    private val appPropertiesService: PropertiesComponent = service()

    private var tfUrl: JTextField? = null
    private var tfUrlComment: JLabel? = null
    private var cbExistingToken: JCheckBox? = null

    private val notificationBanner = NotificationBanner()
    private var tableOfWorkspaces = WorkspacesTable().apply {
        setEnableAntialiasing(true)
        rowSelectionAllowed = true
        columnSelectionAllowed = false
        tableHeader.reorderingAllowed = false
        showVerticalLines = false
        intercellSpacing = Dimension(0, 0)
        columnModel.getColumn(0).apply {
            maxWidth = JBUI.scale(52)
            minWidth = JBUI.scale(52)
        }
        rowHeight = 48
        setEmptyState(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.connect.text.disconnected"))
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        selectionModel.addListSelectionListener {
            setNextButtonEnabled(selectedObject?.agentStatus?.ready() == true && selectedObject?.agentOS == OS.LINUX)
            if (selectedObject?.agentStatus?.ready() == true && selectedObject?.agentOS != OS.LINUX) {
                notificationBanner.apply {
                    component.isVisible = true
                    showInfo(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.unsupported.os.info"))
                }
            } else {
                notificationBanner.component.isVisible = false
            }
            updateWorkspaceActions()
        }
    }

    private val goToDashboardAction = GoToDashboardAction()
    private val goToTemplateAction = GoToTemplateAction()
    private val startWorkspaceAction = StartWorkspaceAction()
    private val stopWorkspaceAction = StopWorkspaceAction()
    private val updateWorkspaceTemplateAction = UpdateWorkspaceTemplateAction()
    private val createWorkspaceAction = CreateWorkspaceAction()

    private val toolbar = ToolbarDecorator.createDecorator(tableOfWorkspaces)
        .disableAddAction()
        .disableRemoveAction()
        .disableUpDownActions()
        .addExtraActions(goToDashboardAction, startWorkspaceAction, stopWorkspaceAction, updateWorkspaceTemplateAction, createWorkspaceAction, goToTemplateAction as AnAction)


    private var poller: Job? = null

    override val component = panel {
        row {
            label(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.header.text")).applyToComponent {
                font = JBFont.h3().asBold()
                icon = CoderIcons.LOGO_16
            }
        }.topGap(TopGap.SMALL)
        row {
            cell(
                ComponentPanelBuilder.createCommentComponent(
                    CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.comment"),
                    false,
                    -1,
                    true
                )
            )
        }
        row {
            browserLink(
                CoderGatewayBundle.message("gateway.connector.view.login.documentation.action"),
                "https://coder.com/docs/coder-oss/latest/workspaces"
            )
        }
        row(CoderGatewayBundle.message("gateway.connector.view.login.url.label")) {
            tfUrl = textField().resizableColumn().align(AlignX.FILL).gap(RightGap.SMALL)
                .bindText(localWizardModel::coderURL).applyToComponent {
                addActionListener {
                    // Reconnect when the enter key is pressed.
                    askTokenAndConnect()
                }
            }.component
            button(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.connect.text")) {
                // Reconnect when the connect button is pressed.
                askTokenAndConnect()
            }.applyToComponent {
                background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
            }
        }.layout(RowLayout.PARENT_GRID)
        row {
            cell() // Empty cells for alignment.
            tfUrlComment = cell(
                ComponentPanelBuilder.createCommentComponent(
                    CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.connect.text.comment",
                        CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.connect.text")),
                    false, -1, true
                )
            ).resizableColumn().align(AlignX.FILL).component
        }.layout(RowLayout.PARENT_GRID)
        row {
            cell() // Empty cell for alignment.
            cbExistingToken = checkBox(CoderGatewayBundle.message("gateway.connector.view.login.existing-token.label"))
                .bindSelected(localWizardModel::useExistingToken)
                .component
        }.layout(RowLayout.PARENT_GRID)
        row {
            cell() // Empty cell for alignment.
            cell(
                ComponentPanelBuilder.createCommentComponent(
                    CoderGatewayBundle.message(
                        "gateway.connector.view.login.existing-token.tooltip",
                        CoderGatewayBundle.message("gateway.connector.view.login.existing-token.label"),
                        CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.connect.text")
                    ),
                    false, -1, true
                )
            )
        }.layout(RowLayout.PARENT_GRID)
        row {
            scrollCell(toolbar.createPanel().apply {
                add(notificationBanner.component.apply { isVisible = false }, "South")
            }).resizableColumn().align(AlignX.FILL).align(AlignY.FILL)
        }.topGap(TopGap.NONE).bottomGap(BottomGap.NONE).resizableRow()

    }.apply {
        background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
        border = JBUI.Borders.empty(0, 16)
    }

    override val previousActionText = IdeBundle.message("button.back")
    override val nextActionText = CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.next.text")

    private inner class GoToDashboardAction :
        AnActionButton(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.dashboard.text"), CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.dashboard.text"), CoderIcons.HOME) {
        override fun actionPerformed(p0: AnActionEvent) {
            BrowserUtil.browse(clientService.client.url)
        }
    }

    private inner class GoToTemplateAction :
        AnActionButton(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.template.text"), CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.template.text"), AllIcons.Nodes.Template) {
        override fun actionPerformed(p0: AnActionEvent) {
            if (tableOfWorkspaces.selectedObject != null) {
                val workspace = tableOfWorkspaces.selectedObject as WorkspaceAgentModel
                BrowserUtil.browse(clientService.client.url.toURI().resolve("/templates/${workspace.templateName}"))
            }
        }
    }

    private inner class StartWorkspaceAction :
        AnActionButton(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.start.text"), CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.start.text"), CoderIcons.RUN) {
        override fun actionPerformed(p0: AnActionEvent) {
            if (tableOfWorkspaces.selectedObject != null) {
                val workspace = tableOfWorkspaces.selectedObject as WorkspaceAgentModel
                cs.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            clientService.client.startWorkspace(workspace.workspaceID, workspace.workspaceName)
                            loadWorkspaces()
                        } catch (e: WorkspaceResponseException) {
                            logger.warn("Could not build workspace ${workspace.name}, reason: $e")
                        }
                    }
                }
            }
        }
    }

    private inner class UpdateWorkspaceTemplateAction :
        AnActionButton(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.update.text"), CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.update.text"), CoderIcons.UPDATE) {
        override fun actionPerformed(p0: AnActionEvent) {
            if (tableOfWorkspaces.selectedObject != null) {
                val workspace = tableOfWorkspaces.selectedObject as WorkspaceAgentModel
                cs.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            clientService.client.updateWorkspace(workspace.workspaceID, workspace.workspaceName, workspace.lastBuildTransition, workspace.templateID)
                            loadWorkspaces()
                        } catch (e: WorkspaceResponseException) {
                            logger.warn("Could not update workspace ${workspace.name}, reason: $e")
                        } catch (e: TemplateResponseException) {
                            logger.warn("Could not update workspace ${workspace.name}, reason: $e")
                        }
                    }
                }
            }
        }
    }

    private inner class StopWorkspaceAction :
        AnActionButton(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.stop.text"), CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.stop.text"), CoderIcons.STOP) {
        override fun actionPerformed(p0: AnActionEvent) {
            if (tableOfWorkspaces.selectedObject != null) {
                val workspace = tableOfWorkspaces.selectedObject as WorkspaceAgentModel
                cs.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            clientService.client.stopWorkspace(workspace.workspaceID, workspace.workspaceName)
                            loadWorkspaces()
                        } catch (e: WorkspaceResponseException) {
                            logger.warn("Could not stop workspace ${workspace.name}, reason: $e")
                        }
                    }
                }
            }
        }
    }

    private inner class CreateWorkspaceAction :
        AnActionButton(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.create.text"), CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.create.text"), CoderIcons.CREATE) {
        override fun actionPerformed(p0: AnActionEvent) {
            BrowserUtil.browse(clientService.client.url.toURI().resolve("/templates"))
        }
    }

    override fun onInit(wizardModel: CoderWorkspacesWizardModel) {
        tableOfWorkspaces.listTableModel.items = emptyList()
        if (localWizardModel.coderURL.isNotBlank() && localWizardModel.token != null) {
            triggerWorkspacePolling(true)
        } else {
            val (url, token) = readStorageOrConfig()
            if (!url.isNullOrBlank()) {
                localWizardModel.coderURL = url
                tfUrl?.text = url
            }
            if (!token.isNullOrBlank()) {
                localWizardModel.token = Pair(token, TokenSource.CONFIG)
            }
            if (!url.isNullOrBlank() && !token.isNullOrBlank()) {
                connect(url.toURL(), Pair(token, TokenSource.CONFIG))
            }
        }
        updateWorkspaceActions()
    }

    /**
     * Return the URL and token from storage or the CLI config.
     */
    private fun readStorageOrConfig(): Pair<String?, String?> {
        val url = appPropertiesService.getValue(CODER_URL_KEY)
        val token = appPropertiesService.getValue(SESSION_TOKEN)
        if (!url.isNullOrBlank() && !token.isNullOrBlank()) {
            return url to token
        }
        return settings.readConfig(settings.coderConfigDir)
    }

    private fun updateWorkspaceActions() {
        goToDashboardAction.isEnabled = clientService.isReady
        createWorkspaceAction.isEnabled = clientService.isReady
        goToTemplateAction.isEnabled = tableOfWorkspaces.selectedObject != null
        when (tableOfWorkspaces.selectedObject?.workspaceStatus) {
            WorkspaceStatus.RUNNING -> {
                startWorkspaceAction.isEnabled = false
                stopWorkspaceAction.isEnabled = true
                when (tableOfWorkspaces.selectedObject?.status) {
                    WorkspaceVersionStatus.OUTDATED -> updateWorkspaceTemplateAction.isEnabled = true
                    else -> updateWorkspaceTemplateAction.isEnabled = false
                }

            }

            WorkspaceStatus.STOPPED, WorkspaceStatus.FAILED -> {
                startWorkspaceAction.isEnabled = true
                stopWorkspaceAction.isEnabled = false
                when (tableOfWorkspaces.selectedObject?.status) {
                    WorkspaceVersionStatus.OUTDATED -> updateWorkspaceTemplateAction.isEnabled = true
                    else -> updateWorkspaceTemplateAction.isEnabled = false
                }
            }

            else -> {
                startWorkspaceAction.isEnabled = false
                stopWorkspaceAction.isEnabled = false
                updateWorkspaceTemplateAction.isEnabled = false
            }
        }
        ActivityTracker.getInstance().inc()
    }

    /**
     * Ask for a new token (regardless of whether we already have a token),
     * place it in the local model, then connect.
     *
     * If the token is invalid abort and start over from askTokenAndConnect()
     * unless retry is false.
     */
    private fun askTokenAndConnect(isRetry: Boolean = false) {
        val oldURL = localWizardModel.coderURL.toURL()
        component.apply() // Force bindings to be filled.
        val newURL = localWizardModel.coderURL.toURL()
        val pastedToken = CoderRemoteConnectionHandle.askToken(
            newURL,
            // If this is a new URL there is no point in trying to use the same
            // token.
            if (oldURL == newURL) localWizardModel.token else null,
            isRetry,
            localWizardModel.useExistingToken,
            settings,
        ) ?: return // User aborted.
        localWizardModel.token = pastedToken
        connect(newURL, pastedToken) {
            askTokenAndConnect(true)
        }
    }

    /**
     * Connect to the deployment in the local model and if successful store the
     * URL and token for use as the default in subsequent launches then load
     * workspaces into the table and keep it updated with a poll.
     *
     * Existing workspaces will be immediately cleared before attempting to
     * connect to the new deployment.
     *
     * If the token is invalid invoke onAuthFailure.
     */
    private fun connect(
        deploymentURL: URL,
        token: Pair<String, TokenSource>,
        onAuthFailure: (() -> Unit)? = null,
    ): Job {
        // Clear out old deployment details.
        cliManager = null
        poller?.cancel()
        tfUrlComment?.foreground = UIUtil.getContextHelpForeground()
        tfUrlComment?.text = CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.connect.text.connecting", deploymentURL.host)
        tableOfWorkspaces.setEmptyState(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.connect.text.connecting", deploymentURL.host))
        tableOfWorkspaces.listTableModel.items = emptyList()

        // Authenticate and load in a background process with progress.
        return LifetimeDefinition().launchUnderBackgroundProgress(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.cli.downloader.dialog.title")) {
            try {
                this.indicator.text = "Authenticating client..."
                authenticate(deploymentURL, token.first)
                // Remember these in order to default to them for future attempts.
                appPropertiesService.setValue(CODER_URL_KEY, deploymentURL.toString())
                appPropertiesService.setValue(SESSION_TOKEN, token.first)

                val cli = ensureCLI(
                    deploymentURL,
                    clientService.buildVersion,
                    settings,
                    this.indicator,
                )

                this.indicator.text = "Authenticating Coder CLI..."
                cli.login(token.first)

                this.indicator.text = "Retrieving workspaces..."
                loadWorkspaces()

                updateWorkspaceActions()
                triggerWorkspacePolling(false)

                cliManager = cli
                tableOfWorkspaces.setEmptyState(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.connect.text.connected", deploymentURL.host))
                tfUrlComment?.text = CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.connect.text.connected", deploymentURL.host)
            } catch (e: Exception) {
                if (isCancellation(e)) {
                    tfUrlComment?.text = CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.connect.text.comment",
                        CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.connect.text"))
                    tableOfWorkspaces.setEmptyState(CoderGatewayBundle.message(
                        "gateway.connector.view.workspaces.connect.canceled",
                        deploymentURL.host,
                    ))
                    logger.info("Connection canceled due to ${e.javaClass.simpleName}")
                } else {
                    val reason = e.message ?: CoderGatewayBundle.message("gateway.connector.view.workspaces.connect.no-reason")
                    val msg = when (e) {
                        is java.nio.file.AccessDeniedException -> CoderGatewayBundle.message("gateway.connector.view.workspaces.connect.access-denied", e.file)
                        is UnknownHostException -> CoderGatewayBundle.message("gateway.connector.view.workspaces.connect.unknown-host", e.message ?: deploymentURL.host)
                        is InvalidExitValueException -> CoderGatewayBundle.message("gateway.connector.view.workspaces.connect.unexpected-exit", e.exitValue)
                        is AuthenticationResponseException -> {
                            CoderGatewayBundle.message(
                                "gateway.connector.view.workspaces.connect.unauthorized",
                                deploymentURL,
                            )
                        }
                        is SocketTimeoutException -> {
                            CoderGatewayBundle.message(
                                "gateway.connector.view.workspaces.connect.timeout",
                                deploymentURL,
                            )
                        }
                        is ResponseException, is ConnectException -> {
                            CoderGatewayBundle.message(
                                "gateway.connector.view.workspaces.connect.download-failed",
                                reason,
                            )
                        }
                        is SSLHandshakeException -> {
                            CoderGatewayBundle.message(
                                "gateway.connector.view.workspaces.connect.ssl-error",
                                deploymentURL.host,
                                reason,
                            )
                        }
                        else -> reason
                    }
                    // It would be nice to place messages directly into the table
                    // but it does not support wrapping or markup so place it in the
                    // comment field of the URL input instead.
                    tfUrlComment?.foreground = UIUtil.getErrorForeground()
                    tfUrlComment?.text = msg
                    tableOfWorkspaces.setEmptyState(CoderGatewayBundle.message(
                        "gateway.connector.view.workspaces.connect.failed",
                        deploymentURL.host,
                    ))
                    logger.error(msg, e)

                    if (e is AuthenticationResponseException) {
                        cs.launch { onAuthFailure?.invoke() }
                    }
                }
            }
        }
    }

    private fun triggerWorkspacePolling(fetchNow: Boolean) {
        poller?.cancel()

        poller = cs.launch {
            if (fetchNow) {
                loadWorkspaces()
            }
            while (isActive) {
                delay(5000)
                loadWorkspaces()
            }
        }
    }

    /**
     * Authenticate the Coder client with the provided token and URL.  On
     * failure throw an error.  On success display warning banners if versions
     * do not match.
     */
    private fun authenticate(url: URL, token: String) {
        logger.info("Authenticating to $url...")
        clientService.initClientSession(url, token)

        try {
            logger.info("Checking compatibility with Coder version ${clientService.buildVersion}...")
            val ver = SemVer.parse(clientService.buildVersion)
            if (ver in CoderSupportedVersions.minCompatibleCoderVersion..CoderSupportedVersions.maxCompatibleCoderVersion) {
                logger.info("${clientService.buildVersion} is compatible")
            } else {
                logger.warn("${clientService.buildVersion} is not compatible")
                notificationBanner.apply {
                    component.isVisible = true
                    showWarning(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.unsupported.coder.version", clientService.buildVersion))
                }
            }
        } catch (e: InvalidVersionException) {
            logger.warn(e)
            notificationBanner.apply {
                component.isVisible = true
                showWarning(
                    CoderGatewayBundle.message(
                        "gateway.connector.view.coder.workspaces.invalid.coder.version",
                        clientService.buildVersion
                    )
                )
            }
        }

        logger.info("Authenticated successfully")
    }

    /**
     * Request workspaces then update the table.
     */
    private suspend fun loadWorkspaces() {
        val ws = withContext(Dispatchers.IO) {
            val timeBeforeRequestingWorkspaces = System.currentTimeMillis()
            try {
                val ws = clientService.client.workspaces()
                val ams = ws.flatMap { it.toAgentModels() }
                ams.forEach {
                    cs.launch(Dispatchers.IO) {
                        it.templateIcon = iconDownloader.load(it.templateIconPath, it.name)
                        withContext(Dispatchers.Main) {
                            tableOfWorkspaces.updateUI()
                        }
                    }
                }
                val timeAfterRequestingWorkspaces = System.currentTimeMillis()
                logger.info("Retrieving the workspaces took: ${timeAfterRequestingWorkspaces - timeBeforeRequestingWorkspaces} millis")
                return@withContext ams
            } catch (e: Exception) {
                logger.error("Could not retrieve workspaces for ${clientService.me.username} on ${clientService.client.url}. Reason: $e")
                emptySet()
            }
        }
        withContext(Dispatchers.Main) {
            val selectedWorkspace = tableOfWorkspaces.selectedObject
            tableOfWorkspaces.listTableModel.items = ws.toList()
            tableOfWorkspaces.selectItem(selectedWorkspace)
        }
    }

    override fun onPrevious() {
        super.onPrevious()
        logger.info("Going back to the main view")
        poller?.cancel()
    }

    override fun onNext(wizardModel: CoderWorkspacesWizardModel): Boolean {
        wizardModel.apply {
            coderURL = localWizardModel.coderURL
            token = localWizardModel.token
        }

        // These being null would be a developer error.
        val workspace = tableOfWorkspaces.selectedObject
        val cli = cliManager
        if (workspace == null) {
            logger.error("No selected workspace")
            return false
        } else if (cli == null) {
            logger.error("No configured CLI")
            return false
        }

        wizardModel.selectedWorkspace = workspace
        poller?.cancel()

        logger.info("Configuring Coder CLI...")
        val workspaces = clientService.client.workspaces()
        cli.configSsh(clientService.client.agents(workspaces).map { it.name })

        // The config directory can be used to pull the URL and token in
        // order to query this workspace's status in other flows, for
        // example from the recent connections screen.
        wizardModel.configDirectory = cli.coderConfigPath.toString()

        logger.info("Opening IDE and Project Location window for ${workspace.name}")
        return true
    }

    override fun dispose() {
        cs.cancel()
    }

    companion object {
        val logger = Logger.getInstance(CoderWorkspacesStepView::class.java.simpleName)
    }
}

class WorkspacesTableModel : ListTableModel<WorkspaceAgentModel>(
    WorkspaceIconColumnInfo(""),
    WorkspaceNameColumnInfo("Name"),
    WorkspaceTemplateNameColumnInfo("Template"),
    WorkspaceVersionColumnInfo("Version"),
    WorkspaceStatusColumnInfo("Status")
) {
    private class WorkspaceIconColumnInfo(columnName: String) : ColumnInfo<WorkspaceAgentModel, String>(columnName) {
        override fun valueOf(workspace: WorkspaceAgentModel?): String? {
            return workspace?.templateName
        }

        override fun getRenderer(item: WorkspaceAgentModel?): TableCellRenderer {
            return object : IconTableCellRenderer<String>() {
                override fun getText(): String {
                    return ""
                }

                override fun getIcon(value: String, table: JTable?, row: Int): Icon {
                    return item?.templateIcon ?: CoderIcons.UNKNOWN
                }

                override fun isCenterAlignment() = true

                override fun getTableCellRendererComponent(table: JTable?, value: Any?, selected: Boolean, focus: Boolean, row: Int, column: Int): Component {
                    super.getTableCellRendererComponent(table, value, selected, focus, row, column).apply {
                        border = JBUI.Borders.empty(8)
                    }
                    return this
                }
            }
        }
    }

    private class WorkspaceNameColumnInfo(columnName: String) : ColumnInfo<WorkspaceAgentModel, String>(columnName) {
        override fun valueOf(workspace: WorkspaceAgentModel?): String? {
            return workspace?.name
        }

        override fun getComparator(): Comparator<WorkspaceAgentModel> {
            return Comparator { a, b ->
                a.name.compareTo(b.name, ignoreCase = true)
            }
        }

        override fun getRenderer(item: WorkspaceAgentModel?): TableCellRenderer {
            return object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
                    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                    if (value is String) {
                        text = value
                    }

                    font = RelativeFont.BOLD.derive(table.tableHeader.font)
                    border = JBUI.Borders.empty(0, 8)
                    return this
                }
            }
        }
    }

    private class WorkspaceTemplateNameColumnInfo(columnName: String) :
        ColumnInfo<WorkspaceAgentModel, String>(columnName) {
        override fun valueOf(workspace: WorkspaceAgentModel?): String? {
            return workspace?.templateName
        }

        override fun getComparator(): java.util.Comparator<WorkspaceAgentModel> {
            return Comparator { a, b ->
                a.templateName.compareTo(b.templateName, ignoreCase = true)
            }
        }

        override fun getRenderer(item: WorkspaceAgentModel?): TableCellRenderer {
            return object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
                    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                    if (value is String) {
                        text = value
                    }
                    font = table.tableHeader.font
                    border = JBUI.Borders.empty(0, 8)
                    return this
                }
            }
        }
    }

    private class WorkspaceVersionColumnInfo(columnName: String) : ColumnInfo<WorkspaceAgentModel, String>(columnName) {
        override fun valueOf(workspace: WorkspaceAgentModel?): String? {
            return workspace?.status?.label
        }

        override fun getRenderer(item: WorkspaceAgentModel?): TableCellRenderer {
            return object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
                    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                    if (value is String) {
                        text = value
                    }
                    font = table.tableHeader.font
                    border = JBUI.Borders.empty(0, 8)
                    return this
                }
            }
        }
    }

    private class WorkspaceStatusColumnInfo(columnName: String) : ColumnInfo<WorkspaceAgentModel, String>(columnName) {
        override fun valueOf(workspace: WorkspaceAgentModel?): String? {
            return workspace?.agentStatus?.label
        }

        override fun getComparator(): java.util.Comparator<WorkspaceAgentModel> {
            return Comparator { a, b ->
                a.agentStatus.label.compareTo(b.agentStatus.label, ignoreCase = true)
            }
        }

        override fun getRenderer(item: WorkspaceAgentModel?): TableCellRenderer {
            return object : DefaultTableCellRenderer() {
                private val workspace = item
                override fun getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
                    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                    if (value is String) {
                        text = value
                        foreground = workspace?.agentStatus?.statusColor()
                        toolTipText = workspace?.agentStatus?.description
                    }
                    font = table.tableHeader.font
                    border = JBUI.Borders.empty(0, 8)
                    return this
                }
            }
        }
    }
}

class WorkspacesTable : TableView<WorkspaceAgentModel>(WorkspacesTableModel()) {
    /**
     * Given either a workspace or an agent select in order of preference:
     * 1. That same agent or workspace.
     * 2. The first match for the workspace (workspace itself or first agent).
     */
    fun selectItem(workspace: WorkspaceAgentModel?) {
        val index = getNewSelection(workspace)
        if (index > -1) {
            selectionModel.addSelectionInterval(convertRowIndexToView(index), convertRowIndexToView(index))
            // Fix cell selection case.
            columnModel.selectionModel.addSelectionInterval(0, columnCount - 1)
        }
    }

    fun getNewSelection(oldSelection: WorkspaceAgentModel?): Int {
        if (oldSelection == null) {
            return -1
        }
        val index = listTableModel.items.indexOfFirst {
            it.name == oldSelection.name && it.workspaceName == oldSelection.workspaceName
        }
        if (index > -1) {
            return index
        }
        return listTableModel.items.indexOfFirst { it.workspaceName == oldSelection.workspaceName }
    }
}
