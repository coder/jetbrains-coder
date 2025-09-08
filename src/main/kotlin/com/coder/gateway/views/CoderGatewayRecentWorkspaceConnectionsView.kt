@file:Suppress("DialogTitleCapitalization")

package com.coder.gateway.views

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.CoderGatewayConstants
import com.coder.gateway.CoderRemoteConnectionHandle
import com.coder.gateway.cli.ensureCLI
import com.coder.gateway.icons.CoderIcons
import com.coder.gateway.models.WorkspaceAgentListModel
import com.coder.gateway.models.WorkspaceProjectIDE
import com.coder.gateway.models.toWorkspaceProjectIDE
import com.coder.gateway.sdk.CoderRestClient
import com.coder.gateway.sdk.v2.models.WorkspaceStatus
import com.coder.gateway.sdk.v2.models.toAgentList
import com.coder.gateway.services.CoderRecentWorkspaceConnectionsService
import com.coder.gateway.services.CoderRestClientService
import com.coder.gateway.services.CoderSettingsService
import com.coder.gateway.util.humanizeConnectionError
import com.coder.gateway.util.toURL
import com.coder.gateway.util.withoutNull
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.actionButton
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.gateway.api.GatewayRecentConnections
import com.jetbrains.gateway.api.GatewayUI
import com.jetbrains.rd.util.lifetime.Lifetime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.util.Locale
import java.util.UUID
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

/**
 * DeploymentInfo contains everything needed to query the API for a deployment
 * along with the latest workspace responses.
 */
data class DeploymentInfo(
    // Null if unable to create the client.
    var client: CoderRestClient? = null,
    // Null if we have not fetched workspaces yet.
    var items: List<WorkspaceAgentListModel>? = null,
    // Null if there have not been any errors yet.
    var error: String? = null,
)

class CoderGatewayRecentWorkspaceConnectionsView(private val setContentCallback: (Component) -> Unit) :
    GatewayRecentConnections,
    Disposable {
    private val settings = service<CoderSettingsService>()
    private val recentConnectionsService = service<CoderRecentWorkspaceConnectionsService>()
    private val cs = CoroutineScope(Dispatchers.Main)
    private val jobs: MutableMap<UUID, Job> = mutableMapOf()

    private val recentWorkspacesContentPanel = JBScrollPane()

    private lateinit var searchBar: SearchTextField
    private var filterString: String? = null

    override val id = CoderGatewayConstants.GATEWAY_RECENT_CONNECTIONS_ID

    override val recentsIcon = CoderIcons.LOGO_16

    /**
     * API clients and workspaces grouped by deployment and keyed by their
     * config directory.
     */
    private var deployments: MutableMap<String, DeploymentInfo> = mutableMapOf()
    private var poller: Job? = null

    override fun createRecentsView(lifetime: Lifetime): JComponent = panel {
        indent {
            row {
                label(CoderGatewayBundle.message("gateway.connector.recent-connections.title")).applyToComponent {
                    font = JBFont.h3().asBold()
                }
                searchBar =
                    cell(SearchTextField(false)).resizableColumn().align(AlignX.FILL).applyToComponent {
                        minimumSize = Dimension(350, -1)
                        textEditor.border = JBUI.Borders.empty(2, 5, 2, 0)
                        addDocumentListener(
                            object : DocumentAdapter() {
                                override fun textChanged(e: DocumentEvent) {
                                    filterString = this@applyToComponent.text.trim()
                                    updateContentView()
                                }
                            },
                        )
                    }.component
                actionButton(
                    object : DumbAwareAction(
                        CoderGatewayBundle.message("gateway.connector.recent-connections.new.wizard.button.tooltip"),
                        null,
                        AllIcons.General.Add,
                    ) {
                        override fun actionPerformed(e: AnActionEvent) {
                            setContentCallback(CoderGatewayConnectorWizardWrapperView().component)
                        }
                    },
                ).gap(RightGap.SMALL)
            }.bottomGap(BottomGap.SMALL)
            separator(background = WelcomeScreenUIManager.getSeparatorColor())
            row {
                resizableRow()
                cell(recentWorkspacesContentPanel).resizableColumn().align(AlignX.FILL).align(AlignY.FILL).component
            }
        }
    }.apply {
        background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
        border = JBUI.Borders.empty(12, 0, 0, 12)
    }

    override fun getRecentsTitle() = CoderGatewayBundle.message("gateway.connector.title")

    override fun updateRecentView() {
        // Render immediately so we can display spinners for each connection
        // that we have not fetched a workspace for yet.
        updateContentView()
        // After each poll, the content view will be updated again.
        triggerWorkspacePolling()
    }

    /**
     * Render the most recent connections, matching with fetched workspaces.
     */
    private fun updateContentView() {
        var top = true
        val connectionsByDeployment = getConnectionsByDeployment(true)
        recentWorkspacesContentPanel.viewport.view =
            panel {
                connectionsByDeployment.forEach { (deploymentURL, connectionsByWorkspace) ->
                    var first = true
                    val deployment = deployments[deploymentURL]
                    val deploymentError = deployment?.error
                    connectionsByWorkspace.forEach { (workspaceName, connections) ->
                        // Show the error at the top of each deployment list.
                        val showError = if (first) {
                            first = false
                            true
                        } else {
                            false
                        }
                        val me = deployment?.client?.me?.username
                        val workspaceWithAgent = deployment?.items?.firstOrNull {
                            it.workspace.ownerName + "/" + it.workspace.name == workspaceName ||
                                    (it.workspace.ownerName == me && it.workspace.name == workspaceName)
                        }
                        val status =
                            if (deploymentError != null) {
                                Triple(UIUtil.getErrorForeground(), deploymentError, UIUtil.getBalloonErrorIcon())
                            } else if (workspaceWithAgent != null) {
                                val inLoadingState = listOf(
                                    WorkspaceStatus.STARTING,
                                    WorkspaceStatus.CANCELING,
                                    WorkspaceStatus.DELETING,
                                    WorkspaceStatus.STOPPING
                                ).contains(workspaceWithAgent.workspace.latestBuild.status)

                                Triple(
                                    workspaceWithAgent.status.statusColor(),
                                    workspaceWithAgent.status.description,
                                    if (inLoadingState) {
                                        AnimatedIcon.Default()
                                    } else {
                                        null
                                    },
                                )
                            } else {
                                Triple(
                                    UIUtil.getContextHelpForeground(),
                                    "Querying workspace status...",
                                    AnimatedIcon.Default()
                                )
                            }
                        val gap =
                            if (top) {
                                top = false
                                TopGap.NONE
                            } else {
                                TopGap.MEDIUM
                            }
                        row {
                            label(workspaceName).applyToComponent {
                                font = JBFont.h3().asBold()
                            }.align(AlignX.LEFT).gap(RightGap.SMALL)
                            label(deploymentURL).applyToComponent {
                                foreground = UIUtil.getContextHelpForeground()
                                font = ComponentPanelBuilder.getCommentFont(font)
                            }
                            label("").resizableColumn().align(AlignX.FILL)
                        }.topGap(gap)

                        val enableLinks = listOf(
                            WorkspaceStatus.STOPPED,
                            WorkspaceStatus.CANCELED,
                            WorkspaceStatus.FAILED,
                            WorkspaceStatus.STARTING,
                            WorkspaceStatus.RUNNING
                        ).contains(workspaceWithAgent?.workspace?.latestBuild?.status)

                        // We only display an API error on the first workspace rather than duplicating it on each workspace.
                        if (deploymentError == null || showError) {
                            row {
                                status.third?.let {
                                    icon(it)
                                }
                                label("<html><body style='width:350px;'>" + status.second + "</html>").applyToComponent {
                                    foreground = status.first
                                }
                            }
                        }

                        connections.forEach { workspaceProjectIDE ->
                            row {
                                icon(workspaceProjectIDE.ideProduct.icon)
                                if (enableLinks) {
                                    cell(
                                        ActionLink(workspaceProjectIDE.projectPathDisplay) {
                                            withoutNull(
                                                deployment?.client,
                                                workspaceWithAgent?.workspace
                                            ) { client, workspace ->
                                                CoderRemoteConnectionHandle().connect {
                                                    if (listOf(
                                                            WorkspaceStatus.STOPPED,
                                                            WorkspaceStatus.CANCELED,
                                                            WorkspaceStatus.FAILED
                                                        ).contains(workspace.latestBuild.status)
                                                    ) {
                                                        val cli = ensureCLI(
                                                            deploymentURL.toURL(),
                                                            client.buildInfo().version,
                                                            settings,
                                                        )
                                                        // We only need to log the cli in if we have token-based auth.
                                                        // Otherwise, we assume it is set up in the same way the plugin
                                                        // is with mTLS.
                                                        if (client.token != null) {
                                                            cli.login(client.token)
                                                        }

                                                        cli.startWorkspace(workspace.ownerName, workspace.name)
                                                    }
                                                    workspaceProjectIDE
                                                }
                                                GatewayUI.getInstance().reset()
                                            }
                                        },
                                    )
                                } else {
                                    label(workspaceProjectIDE.projectPathDisplay).applyToComponent {
                                        foreground = Color.GRAY
                                    }
                                }
                                label(workspaceProjectIDE.name.replace("$workspaceName.", "")).resizableColumn()
                                label(workspaceProjectIDE.ideName).applyToComponent {
                                    foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
                                    font = ComponentPanelBuilder.getCommentFont(font)
                                }
                                label(workspaceProjectIDE.lastOpened.toString()).applyToComponent {
                                    foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
                                    font = ComponentPanelBuilder.getCommentFont(font)
                                }
                                actionButton(
                                    object : DumbAwareAction(
                                        CoderGatewayBundle.message("gateway.connector.recent-connections.remove.button.tooltip"),
                                        "",
                                        CoderIcons.DELETE,
                                    ) {
                                        override fun actionPerformed(e: AnActionEvent) {
                                            recentConnectionsService.removeConnection(workspaceProjectIDE.toRecentWorkspaceConnection())
                                            updateRecentView()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }.apply {
                background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
                border = JBUI.Borders.empty(12, 0, 12, 12)
            }
    }

    /**
     * Get valid connections grouped by deployment and workspace name.  The
     * workspace name will be in the form `owner/workspace.agent`, without the agent
     * name, or just `workspace`, if the connection predates when we added owner
     * information, in which case it belongs to the current user.
     */
    private fun getConnectionsByDeployment(filter: Boolean): Map<String, Map<String, List<WorkspaceProjectIDE>>> =
        recentConnectionsService.getAllRecentConnections()
            // Validate and parse connections.
            .mapNotNull {
                try {
                    it.toWorkspaceProjectIDE()
                } catch (e: Exception) {
                    logger.warn("Removing invalid recent connection $it", e)
                    recentConnectionsService.removeConnection(it)
                    null
                }
            }
            .filter { !filter || matchesFilter(it) }
            // Group by the deployment.
            .groupBy { it.deploymentURL.toString() }
            // Group the connections in each deployment by workspace.
            .mapValues { (_, connections) ->
                connections
                    .groupBy { it.name.split(".", limit = 2).first() }
            }

    /**
     * Return true if the connection matches the current filter.
     */
    private fun matchesFilter(connection: WorkspaceProjectIDE): Boolean = filterString.let {
        it.isNullOrBlank() ||
                connection.hostname.lowercase(Locale.getDefault()).contains(it) ||
                connection.projectPath.lowercase(Locale.getDefault()).contains(it)
    }

    /**
     * Start polling for workspaces if not already started.
     */
    private fun triggerWorkspacePolling() {
        if (poller?.isActive == true) {
            logger.info("Refusing to start already-started poller")
            return
        }

        logger.info("Starting poll loop")
        poller =
            cs.launch(ModalityState.current().asContextElement()) {
                while (isActive) {
                    if (recentWorkspacesContentPanel.isShowing) {
                        logger.info("View still visible; fetching workspaces")
                        fetchWorkspaces()
                    } else {
                        logger.info("View not visible; aborting poll")
                        poller?.cancel()
                    }
                    delay(5000)
                }
            }
    }

    /**
     * Update each deployment with their latest workspaces.
     */
    private suspend fun fetchWorkspaces() {
        withContext(Dispatchers.IO) {
            val connectionsByDeployment = getConnectionsByDeployment(false)
            connectionsByDeployment.forEach { (deploymentURL, connectionsByWorkspace) ->
                val deployment = deployments.getOrPut(deploymentURL) { DeploymentInfo() }
                try {
                    val client = deployment.client
                        ?: CoderRestClientService(
                            deploymentURL.toURL(),
                            settings.token(deploymentURL.toURL())?.first,
                        )

                    if (client.token == null && settings.requireTokenAuth) {
                        throw Exception("Unable to make request; token was not found in CLI config.")
                    }

                    // This is purely to populate the current user, which is
                    // used to match workspaces that were not recorded with owner
                    // information.
                    val me = client.authenticate().username

                    // Delete connections that have no workspace.
                    // TODO: Deletion without confirmation seems sketchy.
                    val items = client.workspaces().flatMap { it.toAgentList() }
                    connectionsByWorkspace.forEach { (name, connections) ->
                        if (items.firstOrNull {
                                it.workspace.ownerName + "/" + it.workspace.name == name ||
                                        (it.workspace.ownerName == me && it.workspace.name == name)
                            } == null
                        ) {
                            logger.info("Removing recent connections for deleted workspace $name (found ${connections.size})")
                            connections.forEach { recentConnectionsService.removeConnection(it.toRecentWorkspaceConnection()) }
                        }
                    }

                    deployment.client = client
                    deployment.items = items
                    deployment.error = null
                } catch (e: Exception) {
                    val msg = humanizeConnectionError(deploymentURL.toURL(), settings.requireTokenAuth, e)
                    deployment.client = null
                    deployment.items = null
                    deployment.error = msg
                    logger.error(msg, e)
                    // TODO: Ask for a token and reconfigure the CLI.
                    // if (e is APIResponseException && e.isUnauthorized && settings.requireTokenAuth) {
                    // }
                }
            }
        }
        withContext(Dispatchers.Main) {
            updateContentView()
        }
    }

    // Note that this is *not* called when you navigate away from the page so
    // check for visibility if you want to avoid work while the panel is not
    // displaying.
    override fun dispose() {
        cs.cancel()
        poller?.cancel()
        jobs.forEach { it.value.cancel() }
        jobs.clear()
    }

    companion object {
        val logger = Logger.getInstance(CoderGatewayRecentWorkspaceConnectionsView::class.java.simpleName)
    }
}
