package com.coder.gateway.views

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.icons.CoderIcons
import com.coder.gateway.services.CoderRecentWorkspaceConnectionsService
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.jetbrains.gateway.api.GatewayRecentConnections
import com.jetbrains.gateway.api.GatewayUI
import com.jetbrains.gateway.ssh.IntelliJPlatformProduct
import com.jetbrains.rd.util.lifetime.Lifetime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.swing.JComponent

class CoderGatewayRecentWorkspaceConnectionsView : GatewayRecentConnections {
    private val recentConnectionsService = service<CoderRecentWorkspaceConnectionsService>()
    private val cs = CoroutineScope(Dispatchers.Main)

    private var contentPanel = JBScrollPane()

    override val id = "CoderGatewayRecentConnections"

    override val recentsIcon = CoderIcons.LOGO_16

    override fun createRecentsView(lifetime: Lifetime): JComponent {
        updateContentView()
        return contentPanel
    }

    override fun getRecentsTitle() = CoderGatewayBundle.message("gateway.connector.title")

    override fun updateRecentView() {
        updateContentView()
    }

    private fun updateContentView() {
        val groupedConnections = recentConnectionsService.getAllRecentConnections().groupBy { it.coderWorkspaceHostname }
        contentPanel.viewport.view = panel {
            indent {
                row {
                    label(CoderGatewayBundle.message("gateway.connector.recentconnections.title")).applyToComponent {
                        font = JBFont.h3().asBold()
                    }
                }.bottomGap(BottomGap.MEDIUM)
                groupedConnections.entries.forEach { (hostname, recentConnections) ->
                    row {
                        if (hostname != null) {
                            label(hostname).applyToComponent {
                                font = JBFont.h3().asBold()
                            }.horizontalAlign(HorizontalAlign.LEFT)
                            cell()
                        }
                    }.topGap(TopGap.MEDIUM)

                    recentConnections.forEach { connectionDetails ->
                        val product = IntelliJPlatformProduct.fromProductCode(connectionDetails.ideProductCode!!)!!
                        row {
                            icon(product.icon)
                            cell(ActionLink(connectionDetails.projectPath!!) {
                                cs.launch {
                                    GatewayUI.getInstance().connect(
                                        mapOf(
                                            "type" to "coder",
                                            "coder_workspace_hostname" to "${connectionDetails.coderWorkspaceHostname}",
                                            "project_path" to connectionDetails.projectPath!!,
                                            "ide_product_code" to "${product.productCode}",
                                            "ide_build_number" to "${connectionDetails.ideBuildNumber}",
                                            "ide_download_link" to "${connectionDetails.downloadSource}"
                                        )
                                    )
                                }
                            })
                            label("").resizableColumn().horizontalAlign(HorizontalAlign.FILL)
                            label("Last opened: ${connectionDetails.lastOpened}").horizontalAlign(HorizontalAlign.RIGHT).applyToComponent {
                                foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
                                font = ComponentPanelBuilder.getCommentFont(font)
                            }
                        }
                    }
                }
            }
        }.apply {
            background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
            border = JBUI.Borders.empty(0, 0, 0, 12)
        }
    }
}