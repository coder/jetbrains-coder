package com.coder.gateway.views.steps

import com.coder.gateway.icons.CoderIcons
import com.coder.gateway.icons.CoderIcons.GRAY_CIRCLE
import com.coder.gateway.icons.CoderIcons.GREEN_CIRCLE
import com.coder.gateway.icons.CoderIcons.RED_CIRCLE
import com.coder.gateway.models.WorkspaceAgentModel
import com.coder.gateway.sdk.OS
import com.coder.gateway.sdk.v2.models.ProvisionerJobStatus
import com.coder.gateway.sdk.v2.models.WorkspaceBuildTransition
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.ui.JBFont
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer

class WorkspaceCellRenderer : ListCellRenderer<WorkspaceAgentModel> {

    override fun getListCellRendererComponent(list: JList<out WorkspaceAgentModel>, workspace: WorkspaceAgentModel, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
        return panel {
            indent {
                row {
                    icon(iconForImageTag(workspace))
                    label(workspace.name).applyToComponent {
                        font = JBFont.h3()
                    }.resizableColumn().horizontalAlign(HorizontalAlign.FILL)
                    panel {
                        row {
                            icon(iconForStatus(workspace))
                            label(labelForStatus(workspace))
                            cell()
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

    private fun iconForImageTag(workspace: WorkspaceAgentModel) = when (workspace.agentOS) {
        OS.LINUX -> CoderIcons.LINUX
        OS.WINDOWS -> CoderIcons.WINDOWS
        OS.MAC -> CoderIcons.MACOS
        else -> CoderIcons.UNKNOWN
    }

    private fun iconForStatus(workspace: WorkspaceAgentModel) = when (workspace.jobStatus) {
        ProvisionerJobStatus.SUCCEEDED -> if (workspace.buildTransition == WorkspaceBuildTransition.START) GREEN_CIRCLE else RED_CIRCLE
        ProvisionerJobStatus.RUNNING -> when (workspace.buildTransition) {
            WorkspaceBuildTransition.START, WorkspaceBuildTransition.STOP, WorkspaceBuildTransition.DELETE -> GRAY_CIRCLE
        }

        else -> RED_CIRCLE
    }

    private fun labelForStatus(workspace: WorkspaceAgentModel) = when (workspace.jobStatus) {
        ProvisionerJobStatus.PENDING -> "◍ Queued"
        ProvisionerJobStatus.RUNNING -> when (workspace.buildTransition) {
            WorkspaceBuildTransition.START -> "⦿ Starting"
            WorkspaceBuildTransition.STOP -> "◍ Stopping"
            WorkspaceBuildTransition.DELETE -> "⦸ Deleting"
        }

        ProvisionerJobStatus.SUCCEEDED -> when (workspace.buildTransition) {
            WorkspaceBuildTransition.START -> "⦿ Running"
            WorkspaceBuildTransition.STOP -> "◍ Stopped"
            WorkspaceBuildTransition.DELETE -> "⦸ Deleted"
        }

        ProvisionerJobStatus.CANCELING -> "◍ Canceling action"
        ProvisionerJobStatus.CANCELED -> "◍ Canceled action"
        ProvisionerJobStatus.FAILED -> "ⓧ Failed"
    }
}