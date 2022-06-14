package com.coder.gateway.views

import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.jetbrains.gateway.api.GatewayUI
import com.jetbrains.gateway.ssh.IntelliJPlatformProduct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Component
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer

class CoderRecentWorkspaceConnectionsTreeRenderer : TreeCellRenderer {
    private val cs = CoroutineScope(Dispatchers.Main)

    override fun getTreeCellRendererComponent(tree: JTree?, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): Component {
        if (value == null || value !is DefaultMutableTreeNode) {
            return panel { }
        }

        when {
            value is HostnameTreeNode -> {
                return panel {
                    indent {
                        row {
                            if (value.hostname != null) {
                                label(value.hostname).applyToComponent {
                                    font = JBFont.h3().asBold()
                                }.horizontalAlign(HorizontalAlign.LEFT)
                                cell()
                            }
                        }
                    }
                }
            }
            value is ProjectTreeNode -> {
                val product = IntelliJPlatformProduct.fromProductCode(value.connectionDetails.ideProductCode!!)!!
                return panel {
                    indent {
                        row {
                            icon(product.icon)
                            cell(ActionLink(value.connectionDetails.projectPath!!) {
                                cs.launch {
                                    GatewayUI.getInstance().connect(
                                        mapOf(
                                            "type" to "coder",
                                            "coder_workspace_hostname" to "coder.${value.connectionDetails.coderWorkspaceHostname}",
                                            "project_path" to value.connectionDetails.projectPath!!,
                                            "ide_product_code" to "${product.productCode}",
                                            "ide_build_number" to "${value.connectionDetails.ideBuildNumber}",
                                            "ide_download_link" to "${value.connectionDetails.downloadSource}"
                                        )
                                    )
                                }
                            })
                            cell()
                            label("Last opened: ${value.connectionDetails.lastOpened}").horizontalAlign(HorizontalAlign.RIGHT).applyToComponent {
                                foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
                                font = ComponentPanelBuilder.getCommentFont(font)
                            }
                        }.layout(RowLayout.PARENT_GRID).bottomGap(BottomGap.MEDIUM)
                    }
                }
            }
        }
        return panel { }
    }
}