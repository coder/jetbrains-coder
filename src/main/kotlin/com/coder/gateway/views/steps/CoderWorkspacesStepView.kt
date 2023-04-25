package com.coder.gateway.views.steps

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.icons.CoderIcons
import com.coder.gateway.models.CoderWorkspacesWizardModel
import com.coder.gateway.models.TokenSource
import com.coder.gateway.models.WorkspaceAgentModel
import com.coder.gateway.models.WorkspaceAgentStatus
import com.coder.gateway.models.WorkspaceAgentStatus.FAILED
import com.coder.gateway.models.WorkspaceAgentStatus.RUNNING
import com.coder.gateway.models.WorkspaceAgentStatus.STOPPED
import com.coder.gateway.models.WorkspaceVersionStatus
import com.coder.gateway.sdk.Arch
import com.coder.gateway.sdk.CoderCLIManager
import com.coder.gateway.sdk.CoderRestClientService
import com.coder.gateway.sdk.CoderSemVer
import com.coder.gateway.sdk.IncompatibleVersionException
import com.coder.gateway.sdk.InvalidVersionException
import com.coder.gateway.sdk.OS
import com.coder.gateway.sdk.ResponseException
import com.coder.gateway.sdk.TemplateIconDownloader
import com.coder.gateway.sdk.ex.AuthenticationResponseException
import com.coder.gateway.sdk.ex.TemplateResponseException
import com.coder.gateway.sdk.ex.WorkspaceResponseException
import com.coder.gateway.sdk.toURL
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.withPath
import com.coder.gateway.services.CoderSettingsState
import com.intellij.ide.ActivityTracker
import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.rd.util.launchUnderBackgroundProgress
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.ui.setEmptyState
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.AnActionButton
import com.intellij.ui.AppIcon
import com.intellij.ui.RelativeFont
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.dialog
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
import com.intellij.util.applyIf
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
import java.awt.Component
import java.awt.Dimension
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.awt.font.TextAttribute
import java.awt.font.TextAttribute.UNDERLINE_ON
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JCheckBox
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer


private const val CODER_URL_KEY = "coder-url"

private const val SESSION_TOKEN = "session-token"

private const val MOUSE_OVER_TEMPLATE_NAME_COLUMN_ON_ROW = "MOUSE_OVER_TEMPLATE_NAME_COLUMN_ON_ROW"

class CoderWorkspacesStepView(val setNextButtonEnabled: (Boolean) -> Unit) : CoderWorkspacesWizardStep, Disposable {
    private val cs = CoroutineScope(Dispatchers.Main)
    private var localWizardModel = CoderWorkspacesWizardModel()
    private val coderClient: CoderRestClientService = service()
    private val iconDownloader: TemplateIconDownloader = service()
    private val settings: CoderSettingsState = service()

    private val appPropertiesService: PropertiesComponent = service()

    private var tfUrl: JTextField? = null
    private var cbExistingToken: JCheckBox? = null
    private var listTableModelOfWorkspaces = ListTableModel<WorkspaceAgentModel>(
        WorkspaceIconColumnInfo(""),
        WorkspaceNameColumnInfo("Name"),
        WorkspaceTemplateNameColumnInfo("Template"),
        WorkspaceVersionColumnInfo("Version"),
        WorkspaceStatusColumnInfo("Status")
    )

    private val notificationBanner = NotificationBanner()
    private var tableOfWorkspaces = TableView(listTableModelOfWorkspaces).apply {
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
        setEmptyState("Disconnected")
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        selectionModel.addListSelectionListener {
            setNextButtonEnabled(selectedObject != null && selectedObject?.agentStatus == RUNNING && selectedObject?.agentOS == OS.LINUX)
            if (selectedObject?.agentStatus == RUNNING && selectedObject?.agentOS != OS.LINUX) {
                notificationBanner.apply {
                    component.isVisible = true
                    showInfo(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.unsupported.os.info"))
                }
            } else {
                notificationBanner.component.isVisible = false
            }
            updateWorkspaceActions()
        }

        addMouseListener(object : MouseListener {
            override fun mouseClicked(e: MouseEvent?) {
                if (e?.source is TableView<*>) {
                    val tblView = e.source as TableView<WorkspaceAgentModel>
                    val col = tblView.selectedColumn
                    val workspace = tblView.selectedObject

                    if (col == 2 && workspace != null) {
                        BrowserUtil.browse(coderClient.coderURL.toURI().resolve("/templates/${workspace.templateName}"))
                    }
                }
            }

            override fun mousePressed(e: MouseEvent?) {
            }

            override fun mouseReleased(e: MouseEvent?) {
            }

            override fun mouseEntered(e: MouseEvent?) {
            }

            override fun mouseExited(e: MouseEvent?) {
            }
        })
        addMouseMotionListener(object : MouseMotionListener {
            override fun mouseMoved(e: MouseEvent?) {
                if (e?.source is TableView<*>) {
                    val tblView = e.source as TableView<WorkspaceAgentModel>
                    val row = tblView.rowAtPoint(e.point)
                    if (tblView.columnAtPoint(e.point) == 2 && row in 0 until tblView.listTableModel.rowCount) {
                        tblView.putClientProperty(MOUSE_OVER_TEMPLATE_NAME_COLUMN_ON_ROW, row)
                    } else {
                        tblView.putClientProperty(MOUSE_OVER_TEMPLATE_NAME_COLUMN_ON_ROW, -1)
                    }
                }

            }

            override fun mouseDragged(e: MouseEvent?) {
            }
        })
    }

    private val goToDashboardAction = GoToDashboardAction()
    private val startWorkspaceAction = StartWorkspaceAction()
    private val stopWorkspaceAction = StopWorkspaceAction()
    private val updateWorkspaceTemplateAction = UpdateWorkspaceTemplateAction()
    private val createWorkspaceAction = CreateWorkspaceAction()

    private val toolbar = ToolbarDecorator.createDecorator(tableOfWorkspaces)
        .disableAddAction()
        .disableRemoveAction()
        .disableUpDownActions()
        .addExtraActions(goToDashboardAction, startWorkspaceAction, stopWorkspaceAction, updateWorkspaceTemplateAction, createWorkspaceAction as AnAction)


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
        border = JBUI.Borders.empty(0, 16, 0, 16)
    }

    override val previousActionText = IdeBundle.message("button.back")
    override val nextActionText = CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.next.text")

    private inner class GoToDashboardAction :
        AnActionButton(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.dashboard.text"), CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.dashboard.text"), CoderIcons.HOME) {
        override fun actionPerformed(p0: AnActionEvent) {
            BrowserUtil.browse(coderClient.coderURL)
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
                            coderClient.startWorkspace(workspace.workspaceID, workspace.workspaceName)
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
                            coderClient.updateWorkspace(workspace.workspaceID, workspace.workspaceName, workspace.lastBuildTransition, workspace.templateID)
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
                            coderClient.stopWorkspace(workspace.workspaceID, workspace.workspaceName)
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
            BrowserUtil.browse(coderClient.coderURL.toURI().resolve("/templates"))
        }
    }

    override fun onInit(wizardModel: CoderWorkspacesWizardModel) {
        listTableModelOfWorkspaces.items = emptyList()
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
        return CoderCLIManager.readConfig()
    }

    private fun updateWorkspaceActions() {
        goToDashboardAction.isEnabled = coderClient.isReady
        createWorkspaceAction.isEnabled = coderClient.isReady
        when (tableOfWorkspaces.selectedObject?.agentStatus) {
            RUNNING -> {
                startWorkspaceAction.isEnabled = false
                stopWorkspaceAction.isEnabled = true
                when (tableOfWorkspaces.selectedObject?.status) {
                    WorkspaceVersionStatus.OUTDATED -> updateWorkspaceTemplateAction.isEnabled = true
                    else -> updateWorkspaceTemplateAction.isEnabled = false
                }

            }

            STOPPED, FAILED -> {
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
        val pastedToken = askToken(
            newURL,
            // If this is a new URL there is no point in trying to use the same
            // token.
            if (oldURL == newURL) localWizardModel.token else null,
            isRetry,
            localWizardModel.useExistingToken,
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
        poller?.cancel()
        tableOfWorkspaces.setEmptyState("Connecting to $deploymentURL...")
        listTableModelOfWorkspaces.items = emptyList()

        // Authenticate and load in a background process with progress.
        // TODO: Make this cancelable.
        return LifetimeDefinition().launchUnderBackgroundProgress(
            CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.cli.downloader.dialog.title"),
            canBeCancelled = false,
            isIndeterminate = true
        ) {
            val cliManager = CoderCLIManager(
                deploymentURL,
                if (settings.binaryDestination.isNotBlank()) Path.of(settings.binaryDestination)
                else CoderCLIManager.getDataDir(),
                settings.binarySource,
            )
            try {
                this.indicator.text = "Authenticating client..."
                authenticate(deploymentURL, token.first)
                // Remember these in order to default to them for future attempts.
                appPropertiesService.setValue(CODER_URL_KEY, deploymentURL.toString())
                appPropertiesService.setValue(SESSION_TOKEN, token.first)

                this.indicator.text = "Downloading Coder CLI..."
                cliManager.downloadCLI()

                this.indicator.text = "Authenticating Coder CLI..."
                cliManager.login(token.first)

                this.indicator.text = "Retrieving workspaces..."
                loadWorkspaces()

                updateWorkspaceActions()
                triggerWorkspacePolling(false)

                tableOfWorkspaces.setEmptyState("Connected to $deploymentURL")
            } catch (e: Exception) {
                val errorSummary = e.message ?: "No reason was provided"
                var msg = CoderGatewayBundle.message(
                    "gateway.connector.view.workspaces.connect.failed",
                    deploymentURL,
                    errorSummary,
                )
                when (e) {
                    is AuthenticationResponseException -> {
                        msg = CoderGatewayBundle.message(
                            "gateway.connector.view.workspaces.connect.unauthorized",
                            deploymentURL,
                        )
                        cs.launch { onAuthFailure?.invoke() }
                    }

                    is SocketTimeoutException -> {
                        msg = CoderGatewayBundle.message(
                            "gateway.connector.view.workspaces.connect.timeout",
                            deploymentURL,
                        )
                    }

                    is ResponseException, is ConnectException -> {
                        msg = CoderGatewayBundle.message(
                            "gateway.connector.view.workspaces.connect.download-failed",
                            cliManager.remoteBinaryURL,
                            errorSummary,
                        )
                    }
                }
                tableOfWorkspaces.setEmptyState(msg)
                logger.error(msg, e)
            }
        }
    }

    /**
     * Open a dialog for providing the token.  Show any existing token so the
     * user can validate it if a previous connection failed.  If we are not
     * retrying and the user has not checked the existing token box then open a
     * browser to the auth page.  If the user has checked the existing token box
     * then populate the dialog with the token on disk (this will overwrite any
     * other existing token) unless this is a retry to avoid clobbering the
     * token that just failed.  Return the token submitted by the user.
     */
    private fun askToken(
        url: URL,
        token: Pair<String, TokenSource>?,
        isRetry: Boolean,
        useExisting: Boolean,
    ): Pair<String, TokenSource>? {
        var (existingToken, tokenSource) = token ?: Pair("", TokenSource.USER)
        val getTokenUrl = url.withPath("/login?redirect=%2Fcli-auth")
        if (!isRetry && !useExisting) {
            BrowserUtil.browse(getTokenUrl)
        } else if (!isRetry && useExisting) {
            val (u, t) = CoderCLIManager.readConfig()
            if (url == u?.toURL() && !t.isNullOrBlank() && t != existingToken) {
                logger.info("Injecting token from CLI config")
                tokenSource = TokenSource.CONFIG
                existingToken = t
            }
        }
        var tokenFromUser: String? = null
        ApplicationManager.getApplication().invokeAndWait({
            lateinit var sessionTokenTextField: JBTextField
            val panel = panel {
                row {
                    browserLink(
                        CoderGatewayBundle.message("gateway.connector.view.login.token.label"),
                        getTokenUrl.toString()
                    )
                    sessionTokenTextField = textField()
                        .applyToComponent {
                            text = existingToken
                            minimumSize = Dimension(520, -1)
                        }.component
                }.layout(RowLayout.PARENT_GRID)
                row {
                    cell() // To align with the text box.
                    cell(
                        ComponentPanelBuilder.createCommentComponent(
                            CoderGatewayBundle.message(
                                if (isRetry) "gateway.connector.view.workspaces.token.rejected"
                                else if (tokenSource == TokenSource.CONFIG) "gateway.connector.view.workspaces.token.injected"
                                else if (existingToken.isNotBlank()) "gateway.connector.view.workspaces.token.comment"
                                else "gateway.connector.view.workspaces.token.none"
                            ),
                            false,
                            -1,
                            true
                        ).applyIf(isRetry) {
                            apply {
                                foreground = UIUtil.getErrorForeground()
                            }
                        }
                    )
                }.layout(RowLayout.PARENT_GRID)
            }
            AppIcon.getInstance().requestAttention(null, true)
            if (!dialog(
                    CoderGatewayBundle.message("gateway.connector.view.login.token.dialog"),
                    panel = panel,
                    focusedComponent = sessionTokenTextField
                ).showAndGet()
            ) {
                return@invokeAndWait
            }
            tokenFromUser = sessionTokenTextField.text
        }, ModalityState.any())
        if (tokenFromUser.isNullOrBlank()) {
            return null
        }
        if (tokenFromUser != existingToken) {
            tokenSource = TokenSource.USER
        }
        return Pair(tokenFromUser!!, tokenSource)
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
        coderClient.initClientSession(url, token)

        try {
            logger.info("Checking compatibility with Coder version ${coderClient.buildVersion}...")
            CoderSemVer.checkVersionCompatibility(coderClient.buildVersion)
            logger.info("${coderClient.buildVersion} is compatible")
        } catch (e: InvalidVersionException) {
            logger.warn(e)
            notificationBanner.apply {
                component.isVisible = true
                showWarning(
                    CoderGatewayBundle.message(
                        "gateway.connector.view.coder.workspaces.invalid.coder.version",
                        coderClient.buildVersion
                    )
                )
            }
        } catch (e: IncompatibleVersionException) {
            logger.warn(e)
            notificationBanner.apply {
                component.isVisible = true
                showWarning(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.unsupported.coder.version", coderClient.buildVersion))
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
                val ws = coderClient.workspaces()
                val ams = ws.flatMap { it.toAgentModels() }.toSet()
                val timeAfterRequestingWorkspaces = System.currentTimeMillis()
                logger.info("Retrieving the workspaces took: ${timeAfterRequestingWorkspaces - timeBeforeRequestingWorkspaces} millis")
                return@withContext ams
            } catch (e: Exception) {
                logger.error("Could not retrieve workspaces for ${coderClient.me.username} on ${coderClient.coderURL}. Reason: $e")
                emptySet()
            }
        }
        withContext(Dispatchers.Main) {
            val selectedWorkspace = tableOfWorkspaces.selectedObject?.name
            listTableModelOfWorkspaces.items = ws.toList()
            if (selectedWorkspace != null) {
                tableOfWorkspaces.selectItem(selectedWorkspace)
            }
        }
    }

    private fun Workspace.toAgentModels(): Set<WorkspaceAgentModel> {
        return when (this.latestBuild.resources.size) {
            0 -> {
                val wm = WorkspaceAgentModel(
                    this.id,
                    this.name,
                    this.name,
                    this.templateID,
                    this.templateName,
                    this.templateIcon,
                    null,
                    WorkspaceVersionStatus.from(this),
                    WorkspaceAgentStatus.from(this),
                    this.latestBuild.transition,
                    null,
                    null,
                    null
                )
                cs.launch(Dispatchers.IO) {
                    wm.templateIcon = iconDownloader.load(wm.templateIconPath, wm.name)
                    withContext(Dispatchers.Main) {
                        tableOfWorkspaces.updateUI()
                    }
                }
                setOf(wm)
            }

            else -> {
                val wam = this.latestBuild.resources.filter { it.agents != null }.flatMap { it.agents!! }.map { agent ->
                    val workspaceWithAgentName = "${this.name}.${agent.name}"
                    val wm = WorkspaceAgentModel(
                        this.id,
                        this.name,
                        workspaceWithAgentName,
                        this.templateID,
                        this.templateName,
                        this.templateIcon,
                        null,
                        WorkspaceVersionStatus.from(this),
                        WorkspaceAgentStatus.from(this),
                        this.latestBuild.transition,
                        OS.from(agent.operatingSystem),
                        Arch.from(agent.architecture),
                        agent.expandedDirectory ?: agent.directory,
                    )
                    cs.launch(Dispatchers.IO) {
                        wm.templateIcon = iconDownloader.load(wm.templateIconPath, wm.name)
                        withContext(Dispatchers.Main) {
                            tableOfWorkspaces.updateUI()
                        }
                    }
                    wm
                }.toSet()

                if (wam.isNullOrEmpty()) {
                    val wm = WorkspaceAgentModel(
                        this.id,
                        this.name,
                        this.name,
                        this.templateID,
                        this.templateName,
                        this.templateIcon,
                        null,
                        WorkspaceVersionStatus.from(this),
                        WorkspaceAgentStatus.from(this),
                        this.latestBuild.transition,
                        null,
                        null,
                        null
                    )
                    cs.launch(Dispatchers.IO) {
                        wm.templateIcon = iconDownloader.load(wm.templateIconPath, wm.name)
                        withContext(Dispatchers.Main) {
                            tableOfWorkspaces.updateUI()
                        }
                    }
                    return setOf(wm)
                }
                return wam
            }
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

        val workspace = tableOfWorkspaces.selectedObject
        if (workspace != null) {
            wizardModel.selectedWorkspace = workspace
            poller?.cancel()

            logger.info("Configuring Coder CLI...")
            val cliManager = CoderCLIManager(
                wizardModel.coderURL.toURL(),
                if (settings.binaryDestination.isNotBlank()) Path.of(settings.binaryDestination)
                else CoderCLIManager.getDataDir(),
                settings.binarySource,
            )
            cliManager.configSsh(listTableModelOfWorkspaces.items)

            logger.info("Opening IDE and Project Location window for ${workspace.name}")
            return true
        }
        return false
    }

    override fun dispose() {
        cs.cancel()
    }

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
                        border = JBUI.Borders.empty(8, 8)
                    }
                    return this
                }
            }
        }
    }

    private inner class WorkspaceNameColumnInfo(columnName: String) : ColumnInfo<WorkspaceAgentModel, String>(columnName) {
        override fun valueOf(workspace: WorkspaceAgentModel?): String? {
            return workspace?.name
        }

        override fun getComparator(): Comparator<WorkspaceAgentModel> {
            return Comparator { a, b ->
                if (a === b) 0
                if (a == null) -1
                if (b == null) 1

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

                    font = RelativeFont.BOLD.derive(this@CoderWorkspacesStepView.tableOfWorkspaces.tableHeader.font)
                    border = JBUI.Borders.empty(0, 8)
                    return this
                }
            }
        }
    }

    private inner class WorkspaceTemplateNameColumnInfo(columnName: String) : ColumnInfo<WorkspaceAgentModel, String>(columnName) {
        override fun valueOf(workspace: WorkspaceAgentModel?): String? {
            return workspace?.templateName
        }

        override fun getComparator(): java.util.Comparator<WorkspaceAgentModel> {
            return Comparator { a, b ->
                if (a === b) 0
                if (a == null) -1
                if (b == null) 1

                a.templateName.compareTo(b.templateName, ignoreCase = true)
            }
        }

        override fun getRenderer(item: WorkspaceAgentModel?): TableCellRenderer {
            val simpleH3 = this@CoderWorkspacesStepView.tableOfWorkspaces.tableHeader.font

            val h3AttributesWithUnderlining = simpleH3.attributes as MutableMap<TextAttribute, Any>
            h3AttributesWithUnderlining[TextAttribute.UNDERLINE] = UNDERLINE_ON
            val underlinedH3 = JBFont.h3().deriveFont(h3AttributesWithUnderlining)
            return object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
                    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                    if (value is String) {
                        text = value
                    }
                    border = JBUI.Borders.empty(0, 8)

                    if (table.getClientProperty(MOUSE_OVER_TEMPLATE_NAME_COLUMN_ON_ROW) != null) {
                        val mouseOverRow = table.getClientProperty(MOUSE_OVER_TEMPLATE_NAME_COLUMN_ON_ROW) as Int
                        if (mouseOverRow >= 0 && mouseOverRow == row) {
                            font = underlinedH3
                            return this
                        }
                    }
                    font = simpleH3
                    return this
                }
            }
        }
    }

    private inner class WorkspaceVersionColumnInfo(columnName: String) : ColumnInfo<WorkspaceAgentModel, String>(columnName) {
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
                    font = this@CoderWorkspacesStepView.tableOfWorkspaces.tableHeader.font
                    border = JBUI.Borders.empty(0, 8)
                    return this
                }
            }
        }
    }

    private inner class WorkspaceStatusColumnInfo(columnName: String) : ColumnInfo<WorkspaceAgentModel, String>(columnName) {
        override fun valueOf(workspace: WorkspaceAgentModel?): String? {
            return workspace?.agentStatus?.label
        }

        override fun getComparator(): java.util.Comparator<WorkspaceAgentModel> {
            return Comparator { a, b ->
                if (a === b) 0
                if (a == null) -1
                if (b == null) 1

                a.agentStatus.label.compareTo(b.agentStatus.label, ignoreCase = true)
            }
        }

        override fun getRenderer(item: WorkspaceAgentModel?): TableCellRenderer {
            return object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
                    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                    if (value is String) {
                        text = value
                        foreground = WorkspaceAgentStatus.from(value).statusColor()
                    }
                    font = this@CoderWorkspacesStepView.tableOfWorkspaces.tableHeader.font
                    border = JBUI.Borders.empty(0, 8)
                    return this
                }
            }
        }
    }

    private fun TableView<WorkspaceAgentModel>.selectItem(workspaceName: String?) {
        if (workspaceName != null) {
            this.items.forEachIndexed { index, workspaceAgentModel ->
                if (workspaceAgentModel.name == workspaceName) {
                    selectionModel.addSelectionInterval(convertRowIndexToView(index), convertRowIndexToView(index))
                    // fix cell selection case
                    columnModel.selectionModel.addSelectionInterval(0, columnCount - 1)
                }
            }
        }
    }

    companion object {
        val logger = Logger.getInstance(CoderWorkspacesStepView::class.java.simpleName)
    }
}
