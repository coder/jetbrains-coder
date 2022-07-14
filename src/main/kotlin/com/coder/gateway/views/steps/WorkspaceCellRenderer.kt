package com.coder.gateway.views.steps

import com.coder.gateway.icons.CoderIcons
import com.coder.gateway.models.WorkspaceAgentModel
import com.coder.gateway.sdk.OS
import com.coder.gateway.sdk.v2.models.ProvisionerJobStatus
import com.coder.gateway.sdk.v2.models.WorkspaceBuildTransition
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer

class WorkspaceCellRenderer : ListCellRenderer<WorkspaceAgentModel> {

    override fun getListCellRendererComponent(list: JList<out WorkspaceAgentModel>, workspace: WorkspaceAgentModel, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
        return panel {
            indent {
                row {
                    icon(workspace.icon())
                    label(workspace.name).applyToComponent {
                        font = JBFont.h3()
                    }.resizableColumn().horizontalAlign(HorizontalAlign.FILL)
                    panel {
                        row {
                            label(workspace.statusLabel()).applyToComponent {
                                font = JBFont.h3()
                                foreground = workspace.statusColor()
                            }
                            cell()
                        }
                    }
                }
            }
        }.apply {
            border = JBUI.Borders.customLine(
                WelcomeScreenUIManager.getSeparatorColor(),
                0, 0, 1, 0
            )

            if (isSelected) {
                background = list.selectionBackground
                foreground = list.selectionForeground
            } else {
                background = list.background
                foreground = list.selectionForeground
            }
        }
    }

    private fun WorkspaceAgentModel.icon() = when (this.agentOS) {
        OS.LINUX -> CoderIcons.LINUX
        OS.WINDOWS -> CoderIcons.WINDOWS
        OS.MAC -> CoderIcons.MACOS
        else -> CoderIcons.UNKNOWN
    }

    private fun WorkspaceAgentModel.statusColor() = when (this.jobStatus) {
        ProvisionerJobStatus.SUCCEEDED -> if (this.buildTransition == WorkspaceBuildTransition.START) Color.GREEN else Color.RED
        ProvisionerJobStatus.RUNNING -> when (this.buildTransition) {
            WorkspaceBuildTransition.START, WorkspaceBuildTransition.STOP, WorkspaceBuildTransition.DELETE -> Color.GRAY
        }

        else -> Color.RED
    }

    private fun WorkspaceAgentModel.statusLabel() = when (this.jobStatus) {
        ProvisionerJobStatus.PENDING -> "◍ Queued"
        ProvisionerJobStatus.RUNNING -> when (this.buildTransition) {
            WorkspaceBuildTransition.START -> "⦿ Starting"
            WorkspaceBuildTransition.STOP -> "◍ Stopping"
            WorkspaceBuildTransition.DELETE -> "⦸ Deleting"
        }

        ProvisionerJobStatus.SUCCEEDED -> when (this.buildTransition) {
            WorkspaceBuildTransition.START -> "⦿ Running"
            WorkspaceBuildTransition.STOP -> "◍ Stopped"
            WorkspaceBuildTransition.DELETE -> "⦸ Deleted"
        }

        ProvisionerJobStatus.CANCELING -> "◍ Canceling action"
        ProvisionerJobStatus.CANCELED -> "◍ Canceled action"
        ProvisionerJobStatus.FAILED -> "ⓧ Failed"
    }
}