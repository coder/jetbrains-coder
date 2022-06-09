package com.coder.gateway.views.steps

import com.coder.gateway.icons.CoderIcons.CENTOS
import com.coder.gateway.icons.CoderIcons.GRAY_CIRCLE
import com.coder.gateway.icons.CoderIcons.GREEN_CIRCLE
import com.coder.gateway.icons.CoderIcons.LINUX
import com.coder.gateway.icons.CoderIcons.RED_CIRCLE
import com.coder.gateway.icons.CoderIcons.UBUNTU
import com.coder.gateway.sdk.v2.models.ProvisionerJobStatus
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceBuildTransition
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBFont
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer

class WorkspaceCellRenderer : ListCellRenderer<Workspace> {

    override fun getListCellRendererComponent(list: JList<out Workspace>, workspace: Workspace, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
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
                }
            }
        }.apply {
            if (isSelected) {
                background = list.selectionBackground
                foreground = list.selectionForeground
            } else {
                background = list.background
                foreground = list.selectionForeground
            }
        }
    }

    private fun iconForImageTag(workspace: Workspace) = when (workspace.templateName) {
        "ubuntu" -> UBUNTU
        "centos" -> CENTOS
        else -> LINUX
    }

    private fun iconForStatus(workspace: Workspace) = when (workspace.latestBuild.job.status) {
        ProvisionerJobStatus.succeeded -> if (workspace.latestBuild.workspaceTransition == WorkspaceBuildTransition.start) GREEN_CIRCLE else RED_CIRCLE
        ProvisionerJobStatus.running -> when (workspace.latestBuild.workspaceTransition) {
            WorkspaceBuildTransition.start, WorkspaceBuildTransition.stop, WorkspaceBuildTransition.delete -> GRAY_CIRCLE
        }
        else -> RED_CIRCLE
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
}