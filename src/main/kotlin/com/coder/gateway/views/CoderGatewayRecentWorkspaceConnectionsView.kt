@file:Suppress("DialogTitleCapitalization")

package com.coder.gateway.views

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.CoderGatewayConstants
import com.coder.gateway.CoderRemoteConnectionHandle
import com.coder.gateway.cli.CoderCLIManager
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
import com.coder.gateway.util.toURL
import com.coder.gateway.util.withPath
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
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
import java.awt.Component
import java.awt.Dimension
import java.util.Locale
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.event.DocumentEvent

/**
 * DeploymentInfo contains everything needed to query the API for a deployment
 * along with the latest workspace responses.
 */
class DeploymentInfo(
    // Null if unable to create the client.
    var client: CoderRestClient? = null,
    // Null if we have not fetched workspaces yet.
    var items: List<WorkspaceAgentListModel>? = null,
    // Null if there have not been any errors yet.
    var error: String? = null,
) {
    fun didFetch(): Boolean {
        return items != null
    }
}

class CoderGatewayRecentWorkspaceConnectionsView(private val setContentCallback: (Component) -> Unit) : GatewayRecentConnections, Disposable {
    private val settings = service<CoderSettingsService>()
    private val recentConnectionsService = service<CoderRecentWorkspaceConnectionsService>()
    private val cs = CoroutineScope(Dispatchers.Main)

    private val recentWorkspacesContentPanel = JBScrollPane()

    private lateinit var searchBar: SearchTextField
    private var filterString: String? = null

    override val id = CoderGatewayConstants.GATEWAY_RECENT_CONNECTIONS_ID

    override val recentsIcon = CoderIcons.LOGO_16

    /**
     * API clients and workspaces grouped by deployment and keyed by their
     * config directory.
     */
    private var deployments: Map<String, DeploymentInfo> = emptyMap()
    private var poller: Job? = null

    override fun createRecentsView(lifetime: Lifetime): JComponent {
        return panel {
            indent {
                row {
                    label(CoderGatewayBundle.message("gateway.connector.recent-connections.title")).applyToComponent {
                        font = JBFont.h3().asBold()
                    }
                    panel {
                        indent {
                            row {
                                cell(JLabel()).resizableColumn().align(AlignX.FILL)
                                searchBar = cell(SearchTextField(false)).resizableColumn().align(AlignX.FILL).applyToComponent {
                                    minimumSize = Dimension(350, -1)
                                    textEditor.border = JBUI.Borders.empty(2, 5, 2, 0)
                                    addDocumentListener(object : DocumentAdapter() {
                                        override fun textChanged(e: DocumentEvent) {
                                            filterString = this@applyToComponent.text.trim()
                                            updateContentView()
                                        }
                                    })
                                }.component

                                actionButton(
                                    object : DumbAwareAction(CoderGatewayBundle.message("gateway.connector.recent-connections.new.wizard.button.tooltip"), null, AllIcons.General.Add) {
                                        override fun actionPerformed(e: AnActionEvent) {
                                            setContentCallback(CoderGatewayConnectorWizardWrapperView().component)
                                        }
                                    },
                                ).gap(RightGap.SMALL)
                            }
                        }
                    }
                }.bottomGap(BottomGap.MEDIUM)
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
    }

    override fun getRecentsTitle() = CoderGatewayBundle.message("gateway.connector.title")

    override fun updateRecentView() {
        triggerWorkspacePolling()
        updateContentView()
    }

    private fun updateContentView() {
        val connectionsByDeployment = recentConnectionsService.getAllRecentConnections()
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
            // Filter by the search.
            .filter { matchesFilter(it) }
            // Group by the deployment.
            .groupBy { it.deploymentURL }
            // Group the connections in each deployment by workspace.
            .mapValues { (deploymentURL, connections) ->
                connections
                    .groupBy { it.name.split(".", limit = 2).first() }
                    // Find the matching workspace in the query response.
                    .mapValues { (workspaceName, connections) ->
                        val deployment = deployments[deploymentURL]
                        val workspaceWithAgent = deployment?.items?.firstOrNull { it.workspace.name == workspaceName }
                        Pair(workspaceWithAgent, connections)
                    }
                    // Remove connections to workspaces that no longer exist.
                    .filter {
                        if (it.value.first == null && deployments[deploymentURL]?.didFetch() == true) {
                            logger.info("Removing recent connections for deleted workspace ${it.key} (found ${it.value.second.size})")
                            it.value.second.forEach { conn ->
                                recentConnectionsService.removeConnection(conn.toRecentWorkspaceConnection())
                            }
                            false
                        } else {
                            true
                        }
                    }
            }
        recentWorkspacesContentPanel.viewport.view = panel {
            connectionsByDeployment.forEach { (deploymentURL, connectionsByWorkspace) ->
                val deployment = deployments[deploymentURL]
                val deploymentError = deployment?.error
                connectionsByWorkspace.forEach { (workspaceName, value) ->
                    val (workspaceWithAgent, connections) = value
                    val status = if (workspaceWithAgent != null) {
                        Triple(workspaceWithAgent.status.icon, workspaceWithAgent.status.statusColor(), workspaceWithAgent.status.description)
                    } else if (deploymentError != null) {
                        Triple(UIUtil.getBalloonErrorIcon(), UIUtil.getErrorForeground(), deploymentError)
                    } else {
                        Triple(AnimatedIcon.Default.INSTANCE, UIUtil.getContextHelpForeground(), "Querying workspace status...")
                    }
                    row {
                        icon(status.first).applyToComponent {
                            foreground = status.second
                        }.align(AlignX.LEFT).gap(RightGap.SMALL).applyToComponent {
                            size = Dimension(JBUI.scale(16), JBUI.scale(16))
                        }
                        label(workspaceName).applyToComponent {
                            font = JBFont.h3().asBold()
                        }.align(AlignX.LEFT).gap(RightGap.SMALL)
                        label(deploymentURL).applyToComponent {
                            foreground = UIUtil.getContextHelpForeground()
                            font = ComponentPanelBuilder.getCommentFont(font)
                        }
                        label("").resizableColumn().align(AlignX.FILL)
                        actionButton(object : DumbAwareAction(CoderGatewayBundle.message("gateway.connector.recent-connections.start.button.tooltip"), "", CoderIcons.RUN) {
                            override fun actionPerformed(e: AnActionEvent) {
                                if (workspaceWithAgent != null) {
                                    deployment?.client?.startWorkspace(workspaceWithAgent.workspace)
                                    cs.launch { fetchWorkspaces() }
                                }
                            }
                        }).applyToComponent { isEnabled = listOf(WorkspaceStatus.STOPPED, WorkspaceStatus.FAILED).contains(workspaceWithAgent?.workspace?.latestBuild?.status) }
                            .gap(RightGap.SMALL)
                        actionButton(object : DumbAwareAction(CoderGatewayBundle.message("gateway.connector.recent-connections.stop.button.tooltip"), "", CoderIcons.STOP) {
                            override fun actionPerformed(e: AnActionEvent) {
                                if (workspaceWithAgent != null) {
                                    deployment?.client?.stopWorkspace(workspaceWithAgent.workspace)
                                    cs.launch { fetchWorkspaces() }
                                }
                            }
                        }).applyToComponent { isEnabled = workspaceWithAgent?.workspace?.latestBuild?.status == WorkspaceStatus.RUNNING }
                            .gap(RightGap.SMALL)
                        actionButton(object : DumbAwareAction(CoderGatewayBundle.message("gateway.connector.recent-connections.terminal.button.tooltip"), "", CoderIcons.OPEN_TERMINAL) {
                            override fun actionPerformed(e: AnActionEvent) {
                                if (workspaceWithAgent != null) {
                                    val link = deployment?.client?.url?.withPath("/me/${workspaceWithAgent.name}/terminal")
                                    BrowserUtil.browse(link?.toString() ?: "")
                                }
                            }
                        })
                    }.topGap(TopGap.MEDIUM)
                    row {
                        label(status.third).applyToComponent { foreground = status.second }
                    }
                    connections.forEach { workspaceProjectIDE ->
                        row {
                            icon(workspaceProjectIDE.ideProductCode.icon)
                            cell(ActionLink(workspaceProjectIDE.projectPathDisplay) {
                                CoderRemoteConnectionHandle().connect{ workspaceProjectIDE }
                                GatewayUI.getInstance().reset()
                            })
                            label("").resizableColumn().align(AlignX.FILL)
                            label(workspaceProjectIDE.ideName).applyToComponent {
                                foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
                                font = ComponentPanelBuilder.getCommentFont(font)
                            }
                            label(workspaceProjectIDE.lastOpened.toString()).applyToComponent {
                                foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
                                font = ComponentPanelBuilder.getCommentFont(font)
                            }
                            actionButton(object : DumbAwareAction(CoderGatewayBundle.message("gateway.connector.recent-connections.remove.button.tooltip"), "", CoderIcons.DELETE) {
                                override fun actionPerformed(e: AnActionEvent) {
                                    recentConnectionsService.removeConnection(workspaceProjectIDE.toRecentWorkspaceConnection())
                                    updateRecentView()
                                }
                            })
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
     * Return true if the connection matches the current filter.
     */
    private fun matchesFilter(connection: WorkspaceProjectIDE): Boolean {
        return filterString.let {
            it.isNullOrBlank()
                    || connection.hostname.lowercase(Locale.getDefault()).contains(it)
                    || connection.projectPath.lowercase(Locale.getDefault()).contains(it)
        }
    }

    /**
     * Start polling for workspaces if not already started.
     */
    private fun triggerWorkspacePolling() {
        deployments = recentConnectionsService.getAllRecentConnections()
            .mapNotNull { it.deploymentURL }.toSet()
            .associateWith { deploymentURL ->
                deployments[deploymentURL] ?: try {
                    val cli = CoderCLIManager(deploymentURL.toURL())
                    val (url, token) = settings.readConfig(cli.coderConfigPath)
                    val client = CoderRestClientService(url?.toURL() ?: deploymentURL.toURL(), token)
                    DeploymentInfo(client)
                } catch (e: Exception) {
                    logger.error("Unable to create client for $deploymentURL", e)
                    DeploymentInfo(error = "Error connecting to $deploymentURL: ${e.message}")
                }
            }

        if (poller?.isActive == true) {
            logger.info("Refusing to start already-started poller")
            return
        }

        logger.info("Starting poll loop")
        poller = cs.launch {
            while (isActive) {
                if (recentWorkspacesContentPanel.isShowing) {
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
            deployments.values
                .filter { it.error == null && it.client != null}
                .forEach { deployment ->
                    val url = deployment.client!!.url
                    try {
                        deployment.items = deployment.client!!
                            .workspaces().flatMap { it.toAgentList() }
                    } catch (e: Exception) {
                        logger.error("Failed to fetch workspaces from $url", e)
                        deployment.error = e.message ?: "Request failed without further details"
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
        logger.info("Disposing recent view")
        cs.cancel()
        poller?.cancel()
    }

    companion object {
        val logger = Logger.getInstance(CoderGatewayRecentWorkspaceConnectionsView::class.java.simpleName)
    }
}
