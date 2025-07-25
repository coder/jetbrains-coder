package com.coder.gateway.views.steps

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.CoderSupportedVersions
import com.coder.gateway.cli.CoderCLIManager
import com.coder.gateway.cli.ensureCLI
import com.coder.gateway.icons.CoderIcons
import com.coder.gateway.models.WorkspaceAgentListModel
import com.coder.gateway.sdk.CoderRestClient
import com.coder.gateway.sdk.ex.APIResponseException
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceAgent
import com.coder.gateway.sdk.v2.models.WorkspaceStatus
import com.coder.gateway.sdk.v2.models.toAgentList
import com.coder.gateway.services.CoderRestClientService
import com.coder.gateway.services.CoderSettingsService
import com.coder.gateway.services.CoderSettingsStateService
import com.coder.gateway.settings.Source
import com.coder.gateway.util.DialogUi
import com.coder.gateway.util.InvalidVersionException
import com.coder.gateway.util.OS
import com.coder.gateway.util.SemVer
import com.coder.gateway.util.WebUrlValidationResult
import com.coder.gateway.util.humanizeConnectionError
import com.coder.gateway.util.isCancellation
import com.coder.gateway.util.toURL
import com.coder.gateway.util.validateStrictWebUrl
import com.coder.gateway.util.withoutNull
import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.rd.util.launchUnderBackgroundProgress
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.ui.setEmptyState
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.AnActionButton
import com.intellij.ui.RelativeFont
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
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
import java.awt.Component
import java.awt.Dimension
import java.net.URL
import java.time.Duration
import java.util.UUID
import javax.swing.Icon
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

// Used to store the most recently used URL and token (if any).
private const val CODER_URL_KEY = "coder-url"
private const val SESSION_TOKEN_KEY = "session-token"

/**
 * Form fields used in the step for the user to fill out.
 */
private data class CoderWorkspacesFormFields(
    var coderURL: String = "",
    var token: Pair<String, Source>? = null,
    var useExistingToken: Boolean = false,
)

/**
 * The data gathered by this step.
 */
data class CoderWorkspacesStepSelection(
    // The workspace and agent we want to view.
    val agent: WorkspaceAgent,
    val workspace: Workspace,
    // This step needs the client and cliManager to configure SSH.
    val cliManager: CoderCLIManager,
    val client: CoderRestClient,
    // Pass along the latest workspaces so we can configure the CLI a bit
    // faster, otherwise this step would have to fetch the workspaces again.
    val workspaces: List<Workspace>,
    val remoteProjectPath: String? = null
)

/**
 * A list of agents/workspaces belonging to a deployment.  Has inputs for
 * connecting and authorizing to different deployments.
 */
class CoderWorkspacesStepView :
    CoderWizardStep<CoderWorkspacesStepSelection>(
        CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.next.text"),
    ) {
    private val state: CoderSettingsStateService = service()
    private val settings: CoderSettingsService = service<CoderSettingsService>()
    private val dialogUi = DialogUi(settings)
    private val cs = CoroutineScope(Dispatchers.Main)
    private val jobs: MutableMap<UUID, Job> = mutableMapOf()
    private val appPropertiesService: PropertiesComponent = service()
    private var poller: Job? = null

    private val fields = CoderWorkspacesFormFields()
    private var client: CoderRestClient? = null
    private var cliManager: CoderCLIManager? = null

    private var tfUrl: JTextField? = null
    private var tfUrlComment: JLabel? = null
    private var cbExistingToken: JCheckBox? = null

    private val notificationBanner = NotificationBanner()
    private var tableOfWorkspaces =
        WorkspacesTable().apply {
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
                nextButton.isEnabled =
                    selectedObject?.status?.ready() == true && selectedObject?.agent?.operatingSystem == OS.LINUX
                if (selectedObject?.status?.ready() == true && selectedObject?.agent?.operatingSystem != OS.LINUX) {
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

    private val toolbar =
        ToolbarDecorator.createDecorator(tableOfWorkspaces)
            .disableAddAction()
            .disableRemoveAction()
            .disableUpDownActions()
            .addExtraActions(
                goToDashboardAction,
                startWorkspaceAction,
                stopWorkspaceAction,
                updateWorkspaceTemplateAction,
                createWorkspaceAction,
                goToTemplateAction as AnAction,
            )

    private val component =
        panel {
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
                        true,
                    ),
                )
            }
            row {
                browserLink(
                    CoderGatewayBundle.message("gateway.connector.view.login.documentation.action"),
                    "https://coder.com/docs/user-guides/workspace-management",
                )
            }
            row(CoderGatewayBundle.message("gateway.connector.view.login.url.label")) {
                tfUrl =
                    textField().resizableColumn().align(AlignX.FILL).gap(RightGap.SMALL)
                        .bindText(fields::coderURL).applyToComponent {
                            addActionListener {
                                // Reconnect when the enter key is pressed.
                                maybeAskTokenThenConnect()
                            }
                            // Add document listener to clear error when user types
                            document.addDocumentListener(object : DocumentListener {
                                override fun insertUpdate(e: DocumentEvent?) = clearErrorState()
                                override fun removeUpdate(e: DocumentEvent?) = clearErrorState()
                                override fun changedUpdate(e: DocumentEvent?) = clearErrorState()

                                private fun clearErrorState() {
                                    tfUrlComment?.apply {
                                        foreground = UIUtil.getContextHelpForeground()
                                        if (tfUrl?.text.equals(client?.url?.toString())) {
                                            text =
                                                CoderGatewayBundle.message(
                                                    "gateway.connector.view.coder.workspaces.connect.text.connected",
                                                    client!!.url.host,
                                                )
                                        } else {
                                            text = CoderGatewayBundle.message(
                                                "gateway.connector.view.coder.workspaces.connect.text.comment",
                                                CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.connect.text"),
                                            )
                                        }
                                        icon = null
                                    }
                                }
                            })
                        }.component
                button(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.connect.text")) {
                    // Reconnect when the connect button is pressed.
                    maybeAskTokenThenConnect()
                }.applyToComponent {
                    background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
                }
            }.layout(RowLayout.PARENT_GRID)
            row {
                cell() // Empty cells for alignment.
                tfUrlComment =
                    cell(
                        ComponentPanelBuilder.createCommentComponent(
                            CoderGatewayBundle.message(
                                "gateway.connector.view.coder.workspaces.connect.text.comment",
                                CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.connect.text"),
                            ),
                            false,
                            -1,
                            true,
                        ),
                    ).resizableColumn().align(AlignX.FILL).component
            }.layout(RowLayout.PARENT_GRID)
            if (settings.requireTokenAuth) {
                row {
                    cell() // Empty cell for alignment.
                    cbExistingToken =
                        checkBox(CoderGatewayBundle.message("gateway.connector.view.login.existing-token.label"))
                            .bindSelected(fields::useExistingToken)
                            .component
                }.layout(RowLayout.PARENT_GRID)
                row {
                    cell() // Empty cell for alignment.
                    cell(
                        ComponentPanelBuilder.createCommentComponent(
                            CoderGatewayBundle.message(
                                "gateway.connector.view.login.existing-token.tooltip",
                                CoderGatewayBundle.message("gateway.connector.view.login.existing-token.label"),
                                CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.connect.text"),
                            ),
                            false,
                            -1,
                            true,
                        ),
                    )
                }.layout(RowLayout.PARENT_GRID)
            }
            row {
                cell() // For alignment.
                checkBox(CoderGatewayBundle.message("gateway.connector.settings.fallback-on-coder-for-signatures.title"))
                    .bindSelected(state::fallbackOnCoderForSignatures).applyToComponent {
                        addActionListener { event ->
                            state.fallbackOnCoderForSignatures = (event.source as JBCheckBox).isSelected
                        }
                    }
                    .comment(
                        CoderGatewayBundle.message("gateway.connector.settings.fallback-on-coder-for-signatures.comment"),
                    )

            }.layout(RowLayout.PARENT_GRID)
            row {
                scrollCell(
                    toolbar.createPanel().apply {
                        add(notificationBanner.component.apply { isVisible = false }, "South")
                    },
                ).resizableColumn().align(AlignX.FILL).align(AlignY.FILL)
            }.topGap(TopGap.NONE).bottomGap(BottomGap.NONE).resizableRow()
        }.apply {
            background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
            border = JBUI.Borders.empty(0, 16)
        }

    private inner class GoToDashboardAction :
        AnActionButton(
            CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.dashboard.text"),
            CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.dashboard.description"),
            CoderIcons.HOME,
        ) {
        override fun actionPerformed(p0: AnActionEvent) {
            withoutNull(client) { BrowserUtil.browse(it.url) }
        }
    }

    private inner class GoToTemplateAction :
        AnActionButton(
            CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.template.text"),
            CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.template.description"),
            AllIcons.Nodes.Template,
        ) {
        override fun actionPerformed(p0: AnActionEvent) {
            withoutNull(client, tableOfWorkspaces.selectedObject?.workspace) { c, workspace ->
                BrowserUtil.browse(c.url.toURI().resolve("/templates/${workspace.templateName}"))
            }
        }
    }

    private inner class StartWorkspaceAction :
        AnActionButton(
            CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.start.text"),
            CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.start.description"),
            CoderIcons.RUN,
        ) {
        override fun actionPerformed(p0: AnActionEvent) {
            withoutNull(cliManager, tableOfWorkspaces.selectedObject?.workspace) { cliManager, workspace ->
                jobs[workspace.id]?.cancel()
                jobs[workspace.id] =
                    cs.launch(ModalityState.current().asContextElement()) {
                        withContext(Dispatchers.IO) {
                            try {
                                cliManager.startWorkspace(workspace.ownerName, workspace.name)
                                loadWorkspaces()
                            } catch (e: Exception) {
                                logger.error("Could not start workspace ${workspace.name}", e)
                            }
                        }
                    }
            }
        }
    }

    private inner class UpdateWorkspaceTemplateAction :
        AnActionButton(
            CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.update.text"),
            CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.update.description"),
            CoderIcons.UPDATE,
        ) {
        override fun actionPerformed(p0: AnActionEvent) {
            withoutNull(client, tableOfWorkspaces.selectedObject?.workspace) { c, workspace ->
                jobs[workspace.id]?.cancel()
                jobs[workspace.id] =
                    cs.launch(ModalityState.current().asContextElement()) {
                        withContext(Dispatchers.IO) {
                            try {
                                // Stop the workspace first if it is running.
                                if (workspace.latestBuild.status == WorkspaceStatus.RUNNING) {
                                    logger.info("Waiting for ${workspace.name} to stop before updating")
                                    c.stopWorkspace(workspace)
                                    loadWorkspaces()
                                    var elapsed = Duration.ofSeconds(0)
                                    val timeout = Duration.ofSeconds(5)
                                    val maxWait = Duration.ofMinutes(10)
                                    while (isActive) { // Wait for the workspace to fully stop.
                                        delay(timeout.toMillis())
                                        val found =
                                            tableOfWorkspaces.items.firstOrNull { it.workspace.id == workspace.id }
                                        when (val status = found?.workspace?.latestBuild?.status) {
                                            WorkspaceStatus.PENDING, WorkspaceStatus.STOPPING, WorkspaceStatus.RUNNING -> {
                                                logger.info("Still waiting for ${workspace.name} to stop before updating")
                                            }

                                            WorkspaceStatus.STARTING, WorkspaceStatus.FAILED,
                                            WorkspaceStatus.CANCELING, WorkspaceStatus.CANCELED,
                                            WorkspaceStatus.DELETING, WorkspaceStatus.DELETED,
                                                -> {
                                                logger.warn("Canceled ${workspace.name} update due to status change to $status")
                                                break
                                            }

                                            null -> {
                                                logger.warn("Canceled ${workspace.name} update because it no longer exists")
                                                break
                                            }

                                            WorkspaceStatus.STOPPED -> {
                                                logger.info("${workspace.name} has stopped; updating now")
                                                c.updateWorkspace(workspace)
                                                break
                                            }
                                        }
                                        elapsed += timeout
                                        if (elapsed > maxWait) {
                                            logger.error(
                                                "Canceled ${workspace.name} update because it took took longer than ${maxWait.toMinutes()} minutes to stop",
                                            )
                                            break
                                        }
                                    }
                                } else {
                                    c.updateWorkspace(workspace)
                                    loadWorkspaces()
                                }
                            } catch (e: Exception) {
                                logger.error("Could not update workspace ${workspace.name}", e)
                            }
                        }
                    }
            }
        }
    }

    private inner class StopWorkspaceAction :
        AnActionButton(
            CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.stop.text"),
            CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.stop.description"),
            CoderIcons.STOP,
        ) {
        override fun actionPerformed(p0: AnActionEvent) {
            withoutNull(client, tableOfWorkspaces.selectedObject?.workspace) { c, workspace ->
                jobs[workspace.id]?.cancel()
                jobs[workspace.id] =
                    cs.launch(ModalityState.current().asContextElement()) {
                        withContext(Dispatchers.IO) {
                            try {
                                c.stopWorkspace(workspace)
                                loadWorkspaces()
                            } catch (e: Exception) {
                                logger.error("Could not stop workspace ${workspace.name}", e)
                            }
                        }
                    }
            }
        }
    }

    private inner class CreateWorkspaceAction :
        AnActionButton(
            CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.create.text"),
            CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.create.description"),
            CoderIcons.CREATE,
        ) {
        override fun actionPerformed(p0: AnActionEvent) {
            withoutNull(client) { BrowserUtil.browse(it.url.toURI().resolve("/templates")) }
        }
    }

    init {
        updateWorkspaceActions()
        addToCenter(component)
    }

    /**
     * Authorize the client and start polling for workspaces if we can.
     */
    fun init() {
        // After each poll, the workspace list will be updated.
        triggerWorkspacePolling()
        // If we already have a client, we are done.  Otherwise try to set one
        // up from storage or config and automatically connect.  Place the
        // values in the fields, so they can be seen and edited if necessary.
        if (client == null || cliManager == null) {
            // Try finding a URL and matching token to use.
            val lastUrl = appPropertiesService.getValue(CODER_URL_KEY)
            val lastToken = appPropertiesService.getValue(SESSION_TOKEN_KEY)
            val url =
                if (!lastUrl.isNullOrBlank()) {
                    lastUrl to Source.LAST_USED
                } else {
                    settings.defaultURL()
                }
            val token =
                if (settings.requireTokenAuth && !lastUrl.isNullOrBlank() && !lastToken.isNullOrBlank()) {
                    lastToken to Source.LAST_USED
                } else if (url != null) {
                    try {
                        settings.token(URL(url.first))
                    } catch (ex: Exception) {
                        null
                    }
                } else {
                    null
                }
            // Set them into the fields.
            if (url != null) {
                fields.coderURL = url.first
                tfUrl?.text = url.first
                logger.info("Using deployment found in ${url.second}")
            }
            if (token != null) {
                fields.token = token
                logger.info("Using token found in ${token.second}")
            }
            // Maybe connect.
            if (url != null && (!settings.requireTokenAuth || token != null)) {
                connect(url.first.toURL(), token?.first)
            }
        }
    }

    /**
     * Enable/disable action buttons based on whether we have a client and the
     * status of the selected workspace (if any).
     */
    private fun updateWorkspaceActions() {
        goToDashboardAction.isEnabled = client != null
        createWorkspaceAction.isEnabled = client != null
        goToTemplateAction.isEnabled = tableOfWorkspaces.selectedObject != null
        when (tableOfWorkspaces.selectedObject?.workspace?.latestBuild?.status) {
            WorkspaceStatus.RUNNING -> {
                startWorkspaceAction.isEnabled = false
                stopWorkspaceAction.isEnabled = true
                updateWorkspaceTemplateAction.isEnabled = tableOfWorkspaces.selectedObject?.workspace?.outdated == true
            }

            WorkspaceStatus.STOPPED, WorkspaceStatus.FAILED -> {
                startWorkspaceAction.isEnabled = true
                stopWorkspaceAction.isEnabled = false
                updateWorkspaceTemplateAction.isEnabled = tableOfWorkspaces.selectedObject?.workspace?.outdated == true
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
     * Ask for a new token if token auth is required (regardless of whether we
     * already have a token), place it in the local fields model, then connect.
     *
     * If the token is invalid try again until the user aborts or we get a valid
     * token.  Any other error will not be retried.
     */
    private fun maybeAskTokenThenConnect(error: String? = null) {
        val oldURL = fields.coderURL
        component.apply() // Force bindings to be filled.
        if (settings.requireTokenAuth) {
            val result = fields.coderURL.validateStrictWebUrl()
            val newURL = fields.coderURL.toURL()
            if (result is WebUrlValidationResult.Invalid) {
                tfUrlComment.apply {
                    this?.foreground = UIUtil.getErrorForeground()
                    this?.text = result.reason
                    this?.icon = UIUtil.getBalloonErrorIcon()
                }
                return
            }
            val pastedToken =
                dialogUi.askToken(
                    newURL,
                    // If this is a new URL there is no point in trying to use the same
                    // token.
                    if (oldURL == newURL.toString()) fields.token else null,
                    fields.useExistingToken,
                    error,
                ) ?: return // User aborted.
            fields.token = pastedToken
            connect(newURL, pastedToken.first) {
                maybeAskTokenThenConnect(it)
            }
        } else {
            connect(fields.coderURL.toURL(), null)
        }
    }

    /**
     * Connect to the provided deployment using the provided token (if required)
     * and if successful store the deployment's URL and token (if provided) for
     * use as the default in subsequent launches then load workspaces into the
     * table and keep it updated with a poll.
     *
     * Existing workspaces will be immediately cleared before attempting to
     * connect to the new deployment.
     *
     * If the token is invalid invoke onAuthFailure.
     *
     * The main effect of this method is to provide a working `cliManager` and
     * `client`.
     */
    private fun connect(
        deploymentURL: URL,
        token: String?,
        onAuthFailure: ((error: String) -> Unit)? = null,
    ): Job {
        tfUrlComment?.foreground = UIUtil.getContextHelpForeground()
        tfUrlComment?.text =
            CoderGatewayBundle.message(
                "gateway.connector.view.coder.workspaces.connect.text.connecting",
                deploymentURL.host,
            )
        tableOfWorkspaces.setEmptyState(
            CoderGatewayBundle.message(
                "gateway.connector.view.coder.workspaces.connect.text.connecting",
                deploymentURL.host
            ),
        )

        tableOfWorkspaces.listTableModel.items = emptyList()
        cliManager = null
        client = null

        // Authenticate and load in a background process with progress.
        return LifetimeDefinition().launchUnderBackgroundProgress(
            CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.cli.downloader.dialog.title"),
        ) {
            try {
                this.indicator.text = "Authenticating client..."
                val authedClient = authenticate(deploymentURL, token)

                // Remember these in order to default to them for future attempts.
                appPropertiesService.setValue(CODER_URL_KEY, deploymentURL.toString())
                appPropertiesService.setValue(SESSION_TOKEN_KEY, token ?: "")

                val cli =
                    ensureCLI(
                        deploymentURL,
                        authedClient.buildVersion,
                        settings,
                    ) {
                        this.indicator.text = it
                    }

                // We only need to log the cli in if we have token-based auth.
                // Otherwise, we assume it is set up in the same way the plugin
                // is with mTLS.
                if (authedClient.token != null) {
                    this.indicator.text = "Authenticating Coder CLI..."
                    cli.login(authedClient.token)
                }

                cliManager = cli
                client = authedClient

                tableOfWorkspaces.setEmptyState(
                    CoderGatewayBundle.message(
                        "gateway.connector.view.coder.workspaces.connect.text.connected",
                        deploymentURL.host
                    ),
                )
                tfUrlComment?.text =
                    CoderGatewayBundle.message(
                        "gateway.connector.view.coder.workspaces.connect.text.connected",
                        deploymentURL.host,
                    )

                this.indicator.text = "Retrieving workspaces..."
                loadWorkspaces()
            } catch (e: Exception) {
                if (isCancellation(e)) {
                    tfUrlComment?.text =
                        CoderGatewayBundle.message(
                            "gateway.connector.view.coder.workspaces.connect.text.comment",
                            CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.connect.text"),
                        )
                    tableOfWorkspaces.setEmptyState(
                        CoderGatewayBundle.message(
                            "gateway.connector.view.workspaces.connect.canceled",
                            deploymentURL.host,
                        ),
                    )
                    logger.info("Connection canceled due to ${e.javaClass.simpleName}")
                } else {
                    val msg = humanizeConnectionError(deploymentURL, settings.requireTokenAuth, e)
                    // It would be nice to place messages directly into the table,
                    // but it does not support wrapping or markup so place it in the
                    // comment field of the URL input instead.
                    tfUrlComment?.foreground = UIUtil.getErrorForeground()
                    tfUrlComment?.text = msg
                    tableOfWorkspaces.setEmptyState(
                        CoderGatewayBundle.message(
                            "gateway.connector.view.workspaces.connect.failed",
                            deploymentURL.host,
                        ),
                    )
                    logger.error(msg, e)

                    if (e is APIResponseException && e.isUnauthorized && onAuthFailure != null) {
                        onAuthFailure.invoke(msg)
                    }
                }
            }
        }
    }

    /**
     * Start polling for workspace changes if not already started.
     */
    private fun triggerWorkspacePolling() {
        if (poller?.isActive == true) {
            logger.info("Refusing to start already-started poller")
            return
        }
        poller =
            cs.launch(ModalityState.current().asContextElement()) {
                while (isActive) {
                    loadWorkspaces()
                    delay(1000)
                }
            }
    }

    /**
     * Authenticate the Coder client with the provided URL and token (if
     * required).  On failure throw an error.  On success display warning
     * banners if versions do not match.  Return the authenticated client.
     */
    private fun authenticate(
        url: URL,
        token: String?,
    ): CoderRestClient {
        logger.info("Authenticating to $url...")
        val tryClient = CoderRestClientService(url, token)
        tryClient.authenticate()

        try {
            logger.info("Checking compatibility with Coder version ${tryClient.buildVersion}...")
            val ver = SemVer.parse(tryClient.buildVersion)
            if (ver in CoderSupportedVersions.minCompatibleCoderVersion..CoderSupportedVersions.maxCompatibleCoderVersion) {
                logger.info("${tryClient.buildVersion} is compatible")
            } else {
                logger.warn("${tryClient.buildVersion} is not compatible")
                notificationBanner.apply {
                    component.isVisible = true
                    showWarning(
                        CoderGatewayBundle.message(
                            "gateway.connector.view.coder.workspaces.unsupported.coder.version",
                            tryClient.buildVersion,
                        ),
                    )
                }
            }
        } catch (e: InvalidVersionException) {
            logger.warn(e)
            notificationBanner.apply {
                component.isVisible = true
                showWarning(
                    CoderGatewayBundle.message(
                        "gateway.connector.view.coder.workspaces.invalid.coder.version",
                        tryClient.buildVersion,
                    ),
                )
            }
        }

        logger.info("Authenticated successfully")
        return tryClient
    }

    /**
     * Request workspaces then update the table.
     */
    private suspend fun loadWorkspaces() {
        val ws =
            withContext(Dispatchers.IO) {
                val timeBeforeRequestingWorkspaces = System.currentTimeMillis()
                val clientNow = client ?: return@withContext emptySet()
                try {
                    val ws = clientNow.workspaces()
                    val ams = ws.flatMap { it.toAgentList() }
                    ams.forEach {
                        cs.launch(Dispatchers.IO) {
                            it.icon = clientNow.loadIcon(it.workspace.templateIcon, it.workspace.name)
                            withContext(Dispatchers.Main) {
                                tableOfWorkspaces.updateUI()
                            }
                        }
                    }
                    val timeAfterRequestingWorkspaces = System.currentTimeMillis()
                    logger.info("Retrieving the workspaces took: ${timeAfterRequestingWorkspaces - timeBeforeRequestingWorkspaces} millis")
                    return@withContext ams
                } catch (e: Exception) {
                    logger.error("Could not retrieve workspaces for ${clientNow.me.username} on ${clientNow.url}", e)
                    emptySet()
                }
            }
        withContext(Dispatchers.Main) {
            val selectedWorkspace = tableOfWorkspaces.selectedObject
            tableOfWorkspaces.listTableModel.items = ws.toList()
            tableOfWorkspaces.selectItem(selectedWorkspace)
        }
    }

    /**
     * Return the selected agent.  Throw if not configured.
     */
    override fun data(): CoderWorkspacesStepSelection {
        val selected = tableOfWorkspaces.selectedObject
        return withoutNull(client, cliManager, selected?.agent, selected?.workspace) { client, cli, agent, workspace ->
            val name = CoderCLIManager.getWorkspaceParts(workspace, agent)
            logger.info("Returning data for $name")
            CoderWorkspacesStepSelection(
                agent = agent,
                workspace = workspace,
                cliManager = cli,
                client = client,
                workspaces = tableOfWorkspaces.items.map { it.workspace },
            )
        }
    }

    override fun stop() {
        poller?.cancel()
        jobs.forEach { it.value.cancel() }
        jobs.clear()
    }

    override fun dispose() {
        stop()
        cs.cancel()
    }

    companion object {
        val logger = Logger.getInstance(CoderWorkspacesStepView::class.java.simpleName)
    }
}

class WorkspacesTableModel :
    ListTableModel<WorkspaceAgentListModel>(
        WorkspaceIconColumnInfo(""),
        WorkspaceNameColumnInfo("Name"),
        WorkspaceOwnerColumnInfo("Owner"),
        WorkspaceTemplateNameColumnInfo("Template"),
        WorkspaceVersionColumnInfo("Version"),
        WorkspaceStatusColumnInfo("Status"),
    ) {
    private class WorkspaceIconColumnInfo(columnName: String) :
        ColumnInfo<WorkspaceAgentListModel, String>(columnName) {
        override fun valueOf(item: WorkspaceAgentListModel?): String? = item?.workspace?.templateName

        override fun getRenderer(item: WorkspaceAgentListModel?): TableCellRenderer {
            return object : IconTableCellRenderer<String>() {
                override fun getText(): String = ""

                override fun getIcon(
                    value: String,
                    table: JTable?,
                    row: Int,
                ): Icon = item?.icon ?: CoderIcons.UNKNOWN

                override fun isCenterAlignment() = true

                override fun getTableCellRendererComponent(
                    table: JTable?,
                    value: Any?,
                    selected: Boolean,
                    focus: Boolean,
                    row: Int,
                    column: Int,
                ): Component {
                    super.getTableCellRendererComponent(table, value, selected, focus, row, column).apply {
                        border = JBUI.Borders.empty(8)
                    }
                    return this
                }
            }
        }
    }

    private class WorkspaceNameColumnInfo(columnName: String) :
        ColumnInfo<WorkspaceAgentListModel, String>(columnName) {
        override fun valueOf(item: WorkspaceAgentListModel?): String? = item?.name

        override fun getComparator(): Comparator<WorkspaceAgentListModel> = Comparator { a, b ->
            a.name.compareTo(b.name, ignoreCase = true)
        }

        override fun getRenderer(item: WorkspaceAgentListModel?): TableCellRenderer {
            return object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    table: JTable,
                    value: Any,
                    isSelected: Boolean,
                    hasFocus: Boolean,
                    row: Int,
                    column: Int,
                ): Component {
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

    private class WorkspaceOwnerColumnInfo(columnName: String) :
        ColumnInfo<WorkspaceAgentListModel, String>(columnName) {
        override fun valueOf(item: WorkspaceAgentListModel?): String? = item?.workspace?.ownerName

        override fun getComparator(): Comparator<WorkspaceAgentListModel> = Comparator { a, b ->
            a.workspace.ownerName.compareTo(b.workspace.ownerName, ignoreCase = true)
        }

        override fun getRenderer(item: WorkspaceAgentListModel?): TableCellRenderer {
            return object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    table: JTable,
                    value: Any,
                    isSelected: Boolean,
                    hasFocus: Boolean,
                    row: Int,
                    column: Int,
                ): Component {
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
        ColumnInfo<WorkspaceAgentListModel, String>(columnName) {
        override fun valueOf(item: WorkspaceAgentListModel?): String? = item?.workspace?.templateName

        override fun getComparator(): java.util.Comparator<WorkspaceAgentListModel> = Comparator { a, b ->
            a.workspace.templateName.compareTo(b.workspace.templateName, ignoreCase = true)
        }

        override fun getRenderer(item: WorkspaceAgentListModel?): TableCellRenderer {
            return object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    table: JTable,
                    value: Any,
                    isSelected: Boolean,
                    hasFocus: Boolean,
                    row: Int,
                    column: Int,
                ): Component {
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

    private class WorkspaceVersionColumnInfo(columnName: String) :
        ColumnInfo<WorkspaceAgentListModel, String>(columnName) {
        override fun valueOf(workspace: WorkspaceAgentListModel?): String? = if (workspace == null) {
            "Unknown"
        } else if (workspace.workspace.outdated) {
            "Outdated"
        } else {
            "Up to date"
        }

        override fun getRenderer(item: WorkspaceAgentListModel?): TableCellRenderer {
            return object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    table: JTable,
                    value: Any,
                    isSelected: Boolean,
                    hasFocus: Boolean,
                    row: Int,
                    column: Int,
                ): Component {
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

    private class WorkspaceStatusColumnInfo(columnName: String) :
        ColumnInfo<WorkspaceAgentListModel, String>(columnName) {
        override fun valueOf(item: WorkspaceAgentListModel?): String? = item?.status?.label

        override fun getComparator(): java.util.Comparator<WorkspaceAgentListModel> = Comparator { a, b ->
            a.status.label.compareTo(b.status.label, ignoreCase = true)
        }

        override fun getRenderer(item: WorkspaceAgentListModel?): TableCellRenderer {
            return object : DefaultTableCellRenderer() {
                private val item = item

                override fun getTableCellRendererComponent(
                    table: JTable,
                    value: Any,
                    isSelected: Boolean,
                    hasFocus: Boolean,
                    row: Int,
                    column: Int,
                ): Component {
                    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                    if (value is String) {
                        text = value
                        foreground = this.item?.status?.statusColor()
                        toolTipText = this.item?.status?.description
                    }
                    font = table.tableHeader.font
                    border = JBUI.Borders.empty(0, 8)
                    return this
                }
            }
        }
    }
}

class WorkspacesTable : TableView<WorkspaceAgentListModel>(WorkspacesTableModel()) {
    /**
     * Given either a workspace or an agent select in order of preference:
     * 1. That same agent or workspace.
     * 2. The first match for the workspace (workspace itself or first agent).
     */
    fun selectItem(workspace: WorkspaceAgentListModel?) {
        val index = getNewSelection(workspace)
        if (index > -1) {
            selectionModel.addSelectionInterval(convertRowIndexToView(index), convertRowIndexToView(index))
            // Fix cell selection case.
            columnModel.selectionModel.addSelectionInterval(0, columnCount - 1)
        }
    }

    /**
     * If a row becomes unselected because the workspace turned on, find the
     * first agent row and select that.
     *
     * If a row becomes unselected because the workspace turned off, find the
     * workspace row and select that.
     */
    private fun getNewSelection(oldSelection: WorkspaceAgentListModel?): Int {
        if (oldSelection == null) {
            return -1
        }
        // Both cases are handled by just looking for the ID, since we only ever
        // show agents or a workspace but never both.
        return listTableModel.items.indexOfFirst { it.workspace.id == oldSelection.workspace.id }
    }
}
