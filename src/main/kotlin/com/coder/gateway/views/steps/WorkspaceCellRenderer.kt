package com.coder.gateway.views.steps

import com.coder.gateway.sdk.v2.models.ProvisionerJobStatus
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceBuildTransition
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

    private fun iconForImageTag(workspace: Workspace) = when (workspace.templateName) {
        "ubuntu" -> UBUNTU_ICON
        "centos" -> CENTOS_ICON
        else -> LINUX_ICON
    }

    private fun iconForStatus(workspace: Workspace) = when (workspace.latestBuild.job.status) {
        ProvisionerJobStatus.succeeded -> if (workspace.latestBuild.workspaceTransition == WorkspaceBuildTransition.start) GREEN_CIRCLE_ICON else RED_CIRCLE_ICON
        ProvisionerJobStatus.running -> when (workspace.latestBuild.workspaceTransition) {
            WorkspaceBuildTransition.start, WorkspaceBuildTransition.stop, WorkspaceBuildTransition.delete -> GRAY_CIRCLE_ICON
        }
        else -> RED_CIRCLE_ICON
    }

    private fun labelForStatus(workspace: Workspace) = when (workspace.latestBuild.job.status) {
        ProvisionerJobStatus.pending -> "◍ Queued"
        ProvisionerJobStatus.running -> when (workspace.latestBuild.workspaceTransition) {
            WorkspaceBuildTransition.start -> "⦿ Starting"
            WorkspaceBuildTransition.stop -> "◍ Stopping"
            WorkspaceBuildTransition.delete -> "⦸ Deleting"
        }
        ProvisionerJobStatus.succeeded -> when (workspace.latestBuild.workspaceTransition) {
            WorkspaceBuildTransition.start -> "⦿ Running"
            WorkspaceBuildTransition.stop -> "◍ Stopped"
            WorkspaceBuildTransition.delete -> "⦸ Deleted"
        }
        ProvisionerJobStatus.canceling -> "◍ Canceling action"
        ProvisionerJobStatus.canceled -> "◍ Canceled action"
        ProvisionerJobStatus.failed -> "ⓧ Failed"
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