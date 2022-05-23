package com.coder.gateway.views.steps

import com.coder.gateway.models.Workspace
import com.intellij.ui.IconManager
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.ui.JBFont
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer

class WorkspaceCellRenderer : ListCellRenderer<Workspace> {

    override fun getListCellRendererComponent(list: JList<out Workspace>?, workspace: Workspace, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
        return panel {
            indent {
                row {
                    icon(iconForImageTag(workspace))
                    label(workspace.name).applyToComponent {
                        font = JBFont.h3()
                    }
                    panel {
                        row {
                            icon(iconForStatus(workspace))
                            label(labelForStatus(workspace))
                        }
                    }

                    button("Open", {}).horizontalAlign(HorizontalAlign.RIGHT)
                }
            }
        }
    }

    private fun iconForImageTag(workspace: Workspace) = when (workspace.imageTag) {
        "ubuntu" -> UBUNTU_ICON
        "centos" -> CENTOS_ICON
        else -> LINUX_ICON
    }

    private fun iconForStatus(workspace: Workspace) = when (workspace.latestStat.container_status) {
        "ON" -> GREEN_CIRCLE_ICON
        "OFF" -> GRAY_CIRCLE_ICON
        else -> RED_CIRCLE_ICON
    }

    private fun labelForStatus(workspace: Workspace) = when (workspace.latestStat.container_status) {
        "ON" -> "Running"
        "OFF" -> "Off"
        else -> "Unknown status"
    }

    companion object {
        val UBUNTU_ICON = IconManager.getInstance().getIcon("ubuntu.svg", this::class.java)
        val CENTOS_ICON = IconManager.getInstance().getIcon("centos.svg", this::class.java)
        val LINUX_ICON = IconManager.getInstance().getIcon("linux.svg", this::class.java)


        val GREEN_CIRCLE_ICON = IconManager.getInstance().getIcon("green_circle.svg", this::class.java)
        val GRAY_CIRCLE_ICON = IconManager.getInstance().getIcon("gray_circle.svg", this::class.java)
        val RED_CIRCLE_ICON = IconManager.getInstance().getIcon("red_circle.svg", this::class.java)
    }
}