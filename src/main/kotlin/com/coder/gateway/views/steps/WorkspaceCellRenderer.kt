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
        ProvisionerJobStatus.SUCCEEDED -> if (workspace.latestBuild.workspaceTransition == WorkspaceBuildTransition.START) GREEN_CIRCLE else RED_CIRCLE
        ProvisionerJobStatus.RUNNING -> when (workspace.latestBuild.workspaceTransition) {
            WorkspaceBuildTransition.START, WorkspaceBuildTransition.STOP, WorkspaceBuildTransition.DELETE -> GRAY_CIRCLE
        }
        else -> RED_CIRCLE
    }

    private fun labelForStatus(workspace: Workspace) = when (workspace.latestBuild.job.status) {
        ProvisionerJobStatus.PENDING -> "◍ Queued"
        ProvisionerJobStatus.RUNNING -> when (workspace.latestBuild.workspaceTransition) {
            WorkspaceBuildTransition.START -> "⦿ Starting"
            WorkspaceBuildTransition.STOP -> "◍ Stopping"
            WorkspaceBuildTransition.DELETE -> "⦸ Deleting"
        }
        ProvisionerJobStatus.SUCCEEDED -> when (workspace.latestBuild.workspaceTransition) {
            WorkspaceBuildTransition.START -> "⦿ Running"
            WorkspaceBuildTransition.STOP -> "◍ Stopped"
            WorkspaceBuildTransition.DELETE -> "⦸ Deleted"
        }
        ProvisionerJobStatus.CANCELING -> "◍ Canceling action"
        ProvisionerJobStatus.CANCELED -> "◍ Canceled action"
        ProvisionerJobStatus.FAILED -> "ⓧ Failed"
    }
}