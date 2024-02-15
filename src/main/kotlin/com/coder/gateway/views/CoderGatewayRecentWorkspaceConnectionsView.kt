@file:Suppress("DialogTitleCapitalization")

package com.coder.gateway.views

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.CoderGatewayConstants
import com.coder.gateway.CoderRemoteConnectionHandle
import com.coder.gateway.icons.CoderIcons
import com.coder.gateway.models.RecentWorkspaceConnection
import com.coder.gateway.models.WorkspaceAgentModel
import com.coder.gateway.sdk.BaseCoderRestClient
import com.coder.gateway.sdk.CoderRestClient
import com.coder.gateway.util.toURL
import com.coder.gateway.sdk.v2.models.WorkspaceStatus
import com.coder.gateway.sdk.v2.models.toAgentModels
import com.coder.gateway.services.CoderRecentWorkspaceConnectionsService
import com.coder.gateway.toWorkspaceParams
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
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.gateway.api.GatewayRecentConnections
import com.jetbrains.gateway.api.GatewayUI
import com.jetbrains.gateway.ssh.IntelliJPlatformProduct
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
import java.nio.file.Path
import java.util.Locale
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.event.DocumentEvent

/**
 * DeploymentInfo contains everything needed to query the API for a deployment
 * along with the latest workspace responses.
 */
data class DeploymentInfo(
    // Null if unable to create the client (config directory did not exist).
    var client: BaseCoderRestClient? = null,
    // Null if we have not fetched workspaces yet.
    var workspaces: List<WorkspaceAgentModel>? = null,
    // Null if there have not been any errors yet.
    var error: String? = null,
)

class CoderGatewayRecentWorkspaceConnectionsView(private val setContentCallback: (Component) -> Unit) : GatewayRecentConnections, Disposable {
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
        val connections = recentConnectionsService.getAllRecentConnections()
            .filter { it.coderWorkspaceHostname != null }
            .filter { matchesFilter(it) }
            .groupBy { it.coderWorkspaceHostname!! }
        recentWorkspacesContentPanel.viewport.view = panel {
            connections.forEach { (hostname, connections) ->
                // The config directory and name will not exist on connections
                // made with 2.3.0 and earlier.
                val name = connections.firstNotNullOfOrNull { it.name }
                val workspaceName = name?.split(".", limit = 2)?.first()
                val configDirectory = connections.firstNotNullOfOrNull { it.configDirectory }
                val deployment = deployments[configDirectory]
                val workspace = deployment?.workspaces
                    ?.firstOrNull { it.name == name || it.workspaceName == workspaceName  }
                row {
                    (if (workspace != null) {
                        icon(workspace.agentStatus.icon).applyToComponent {
                            foreground = workspace.agentStatus.statusColor()
                            toolTipText = workspace.agentStatus.description
                        }
                    } else if (configDirectory == null || workspaceName == null) {
                        icon(CoderIcons.UNKNOWN).applyToComponent {
                            toolTipText = "Unable to determine workspace status because the configuration directory and/or name were not recorded. To fix, add the connection again."
                        }
                    } else if (deployment?.error != null) {
                        icon(UIUtil.getBalloonErrorIcon()).applyToComponent {
                            toolTipText = deployment.error
                        }
                    } else if (deployment?.workspaces != null) {
                        icon(UIUtil.getBalloonErrorIcon()).applyToComponent {
                            toolTipText = "Workspace $workspaceName does not exist"
                        }
                    } else {
                        icon(AnimatedIcon.Default.INSTANCE).applyToComponent {
                            toolTipText = "Querying workspace status..."
                        }
                    }).align(AlignX.LEFT).gap(RightGap.SMALL).applyToComponent {
                        size = Dimension(JBUI.scale(16), JBUI.scale(16))
                    }
                    label(hostname.removePrefix("coder-jetbrains--")).applyToComponent {
                        font = JBFont.h3().asBold()
                    }.align(AlignX.LEFT).gap(RightGap.SMALL)
                    label("").resizableColumn().align(AlignX.FILL)
                    actionButton(object : DumbAwareAction(CoderGatewayBundle.message("gateway.connector.recent-connections.start.button.tooltip"), "", CoderIcons.RUN) {
                        override fun actionPerformed(e: AnActionEvent) {
                            if (workspace != null) {
                                deployment.client?.startWorkspace(workspace.workspaceID, workspace.workspaceName)
                                cs.launch { fetchWorkspaces() }
                            }
                        }
                    }).applyToComponent { isEnabled = listOf(WorkspaceStatus.STOPPED, WorkspaceStatus.FAILED).contains(workspace?.workspaceStatus) }.gap(RightGap.SMALL)
                    actionButton(object : DumbAwareAction(CoderGatewayBundle.message("gateway.connector.recent-connections.stop.button.tooltip"), "", CoderIcons.STOP) {
                        override fun actionPerformed(e: AnActionEvent) {
                            if (workspace != null) {
                                deployment.client?.stopWorkspace(workspace.workspaceID, workspace.workspaceName)
                                cs.launch { fetchWorkspaces() }
                            }
                        }
                    }).applyToComponent { isEnabled = workspace?.workspaceStatus == WorkspaceStatus.RUNNING }.gap(RightGap.SMALL)
                    actionButton(object : DumbAwareAction(CoderGatewayBundle.message("gateway.connector.recent-connections.terminal.button.tooltip"), "", CoderIcons.OPEN_TERMINAL) {
                        override fun actionPerformed(e: AnActionEvent) {
                            BrowserUtil.browse(connections[0].webTerminalLink ?: "")
                        }
                    })
                }.topGap(TopGap.MEDIUM)

                connections.forEach { connectionDetails ->
                    val product = IntelliJPlatformProduct.fromProductCode(connectionDetails.ideProductCode!!)!!
                    row {
                        icon(product.icon)
                        cell(ActionLink(connectionDetails.projectPath!!) {
                            CoderRemoteConnectionHandle().connect{ connectionDetails.toWorkspaceParams() }
                            GatewayUI.getInstance().reset()
                        })
                        label("").resizableColumn().align(AlignX.FILL)
                        label("Last opened: ${connectionDetails.lastOpened}").applyToComponent {
                            foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
                            font = ComponentPanelBuilder.getCommentFont(font)
                        }
                        actionButton(object : DumbAwareAction(CoderGatewayBundle.message("gateway.connector.recent-connections.remove.button.tooltip"), "", CoderIcons.DELETE) {
                            override fun actionPerformed(e: AnActionEvent) {
                                recentConnectionsService.removeConnection(connectionDetails)
                                updateRecentView()
                            }
                        })
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
    private fun matchesFilter(connection: RecentWorkspaceConnection): Boolean {
        return filterString.isNullOrBlank()
                || connection.coderWorkspaceHostname?.lowercase(Locale.getDefault())?.contains(filterString!!) == true
                || connection.projectPath?.lowercase(Locale.getDefault())?.contains(filterString!!) == true
    }

    /**
     * Start polling for workspaces if not already started.
     */
    private fun triggerWorkspacePolling() {
        deployments = recentConnectionsService.getAllRecentConnections()
            .mapNotNull { it.configDirectory }.toSet()
            .associateWith { dir ->
                deployments[dir] ?: try {
                    val url = Path.of(dir).resolve("url").toFile().readText()
                    val token = Path.of(dir).resolve("session").toFile().readText()
                    DeploymentInfo(CoderRestClient(url.toURL(), token))
                } catch (e: Exception) {
                    logger.error("Unable to create client from $dir", e)
                    DeploymentInfo(error = "Error trying to read $dir: ${e.message}")
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
                        deployment.workspaces = deployment.client!!
                            .workspaces().flatMap { it.toAgentModels() }
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
