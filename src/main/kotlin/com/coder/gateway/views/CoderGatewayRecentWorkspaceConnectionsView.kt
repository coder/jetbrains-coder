package com.coder.gateway.views

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.icons.CoderIcons
import com.coder.gateway.models.RecentWorkspaceConnection
import com.coder.gateway.services.CoderRecentWorkspaceConnectionsService
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.treeStructure.treetable.ListTreeTableModel
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo
import com.intellij.util.ui.JBFont
import com.jetbrains.gateway.api.GatewayRecentConnections
import com.jetbrains.rd.util.lifetime.Lifetime
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

class CoderGatewayRecentWorkspaceConnectionsView : GatewayRecentConnections {
    private val recentConnectionsService = service<CoderRecentWorkspaceConnectionsService>()

    private var top = DefaultMutableTreeNode()
    private var recentConnectionsTeeModel = ListTreeTableModel(top, emptyArray<TreeColumnInfo>())
    private val recentConnectionsView = Tree(recentConnectionsTeeModel)

    override val id = "CoderGatewayRecentConnections"

    override val recentsIcon = CoderIcons.LOGO_16

    override fun createRecentsView(lifetime: Lifetime): JComponent {
        val view = panel {
            indent {
                row {
                    label(CoderGatewayBundle.message("gateway.connector.recentconnections.title")).applyToComponent {
                        font = JBFont.h3().asBold()
                    }
                }
                row {
                    scrollCell(recentConnectionsView).resizableColumn().horizontalAlign(HorizontalAlign.FILL).verticalAlign(VerticalAlign.FILL).applyToComponent {
                        background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
                    }
                    cell()
                }.topGap(TopGap.NONE).resizableRow()
            }
        }.apply {
            background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
        }

        recentConnectionsView.apply {
            isRootVisible = false
            showsRootHandles = false
            rowHeight = -1
            val mouseListeners = mouseListeners
            for (mouseListener in mouseListeners) {
                removeMouseListener(mouseListener)
            }
            cellRenderer = CoderRecentWorkspaceConnectionsTreeRenderer()
        }
        updateTree()
        return view
    }

    override fun getRecentsTitle() = CoderGatewayBundle.message("gateway.connector.title")

    override fun updateRecentView() {
        updateTree()
    }

    private fun updateTree() {
        val groupedConnections = recentConnectionsService.getAllRecentConnections().groupBy { it.coderWorkspaceHostname }
        top.removeAllChildren()
        groupedConnections.entries.forEach { (hostname, recentConnections) ->
            val hostnameTreeNode = HostnameTreeNode(hostname)
            recentConnections.forEach { connectionDetails ->
                hostnameTreeNode.add(ProjectTreeNode(connectionDetails))
            }
            top.add(hostnameTreeNode)
        }

        expandAll(recentConnectionsView, TreePath(top))
    }

    private fun expandAll(tree: JTree, parent: TreePath) {
        val node: TreeNode = parent.lastPathComponent as TreeNode
        if (node.childCount >= 0) {
            val e = node.children()
            while (e.hasMoreElements()) {
                val n: TreeNode = e.nextElement() as TreeNode
                val path: TreePath = parent.pathByAddingChild(n)
                expandAll(tree, path)
            }
        }
        tree.expandPath(parent)
    }
}


class HostnameTreeNode(val hostname: String?) : DefaultMutableTreeNode()

class ProjectTreeNode(val connectionDetails: RecentWorkspaceConnection) : DefaultMutableTreeNode()