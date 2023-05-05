@file:Suppress("DialogTitleCapitalization")

package com.coder.gateway.views

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.CoderGatewayConstants
import com.coder.gateway.icons.CoderIcons
import com.coder.gateway.models.RecentWorkspaceConnection
import com.coder.gateway.services.CoderRecentWorkspaceConnectionsService
import com.coder.gateway.toWorkspaceParams
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.jetbrains.gateway.api.GatewayRecentConnections
import com.jetbrains.gateway.api.GatewayUI
import com.jetbrains.gateway.ssh.IntelliJPlatformProduct
import com.jetbrains.rd.util.lifetime.Lifetime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.awt.Component
import java.awt.Dimension
import java.util.*
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.event.DocumentEvent

class CoderGatewayRecentWorkspaceConnectionsView(private val setContentCallback: (Component) -> Unit) : GatewayRecentConnections, Disposable {
    private val recentConnectionsService = service<CoderRecentWorkspaceConnectionsService>()
    private val cs = CoroutineScope(Dispatchers.Main)

    private val recentWorkspacesContentPanel = JBScrollPane()

    private lateinit var searchBar: SearchTextField

    override val id = CoderGatewayConstants.GATEWAY_RECENT_CONNECTIONS_ID

    override val recentsIcon = CoderIcons.LOGO_16

    override fun createRecentsView(lifetime: Lifetime): JComponent {
        return panel {
            indent {
                row {
                    label(CoderGatewayBundle.message("gateway.connector.recentconnections.title")).applyToComponent {
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
                                            val toSearchFor = this@applyToComponent.text
                                            val filteredConnections = recentConnectionsService.getAllRecentConnections()
                                                .filter { it.coderWorkspaceHostname != null }
                                                .filter { it.coderWorkspaceHostname!!.lowercase(Locale.getDefault()).contains(toSearchFor) || it.projectPath?.lowercase(Locale.getDefault())?.contains(toSearchFor) ?: false }
                                            updateContentView(filteredConnections.groupBy { it.coderWorkspaceHostname!! })
                                        }
                                    })
                                }.component

                                actionButton(
                                    object : DumbAwareAction(CoderGatewayBundle.message("gateway.connector.recentconnections.new.wizard.button.tooltip"), null, AllIcons.General.Add) {
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
        val groupedConnections = recentConnectionsService.getAllRecentConnections()
            .filter { it.coderWorkspaceHostname != null }
            .groupBy { it.coderWorkspaceHostname!! }
        updateContentView(groupedConnections)
    }

    private fun updateContentView(groupedConnections: Map<String, List<RecentWorkspaceConnection>>) {
        recentWorkspacesContentPanel.viewport.view = panel {
            groupedConnections.entries.forEach { (hostname, recentConnections) ->
                row {
                    label(hostname).applyToComponent {
                        font = JBFont.h3().asBold()
                    }.align(AlignX.LEFT).gap(RightGap.SMALL)
                    actionButton(object : DumbAwareAction(CoderGatewayBundle.message("gateway.connector.recentconnections.terminal.button.tooltip"), "", CoderIcons.OPEN_TERMINAL) {
                        override fun actionPerformed(e: AnActionEvent) {
                            BrowserUtil.browse(recentConnections[0].webTerminalLink ?: "")
                        }
                    })
                }.topGap(TopGap.MEDIUM)

                recentConnections.forEach { connectionDetails ->
                    val product = IntelliJPlatformProduct.fromProductCode(connectionDetails.ideProductCode!!)!!
                    row {
                        icon(product.icon)
                        cell(ActionLink(connectionDetails.projectPath!!) {
                            cs.launch {
                                GatewayUI.getInstance().connect(connectionDetails.toWorkspaceParams())
                            }
                        })
                        label("").resizableColumn().align(AlignX.FILL)
                        label("Last opened: ${connectionDetails.lastOpened}").applyToComponent {
                            foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
                            font = ComponentPanelBuilder.getCommentFont(font)
                        }
                        actionButton(object : DumbAwareAction(CoderGatewayBundle.message("gateway.connector.recentconnections.remove.button.tooltip"), "", CoderIcons.DELETE) {
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
            border = JBUI.Borders.empty(12, 0, 0, 12)
        }
    }

    override fun dispose() {
        cs.cancel()
    }
}