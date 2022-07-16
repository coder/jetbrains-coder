package com.coder.gateway.views.steps

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.icons.CoderIcons
import com.coder.gateway.models.CoderWorkspacesWizardModel
import com.coder.gateway.models.WorkspaceAgentModel
import com.coder.gateway.sdk.Arch
import com.coder.gateway.sdk.CoderRestClientService
import com.coder.gateway.sdk.OS
import com.coder.gateway.sdk.v2.models.ProvisionerJobStatus
import com.coder.gateway.sdk.v2.models.WorkspaceBuildTransition
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.table.IconTableCellRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer


class CoderWorkspacesStepView : CoderWorkspacesWizardStep, Disposable {
    private val cs = CoroutineScope(Dispatchers.Main)

    private val coderClient: CoderRestClientService = ApplicationManager.getApplication().getService(CoderRestClientService::class.java)


    private var listTableModelOfWorkspaces = ListTableModel<WorkspaceAgentModel>(WorkspaceIconColumnInfo(""), WorkspaceNameColumnInfo("Name"), WorkspaceTemplateNameColumnInfo("Template"), WorkspaceStatusColumnInfo("Status"))
    private var tableOfWorkspaces = TableView(listTableModelOfWorkspaces).apply {
        rowSelectionAllowed = true
        columnSelectionAllowed = false
        tableHeader.reorderingAllowed = false
        showVerticalLines = false
        intercellSpacing = Dimension(0, 0)
        columnModel.getColumn(0).apply {
            maxWidth = JBUI.scale(52)
            minWidth = JBUI.scale(52)
        }

        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    }

    private lateinit var wizard: CoderWorkspacesWizardModel

    override val component = panel {
        indent {
            row {
                label(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.choose.text")).applyToComponent {
                    font = JBFont.h3().asBold()
                    icon = CoderIcons.LOGO_16
                }
            }.bottomGap(BottomGap.MEDIUM)
            row {
                scrollCell(tableOfWorkspaces).resizableColumn().horizontalAlign(HorizontalAlign.FILL).verticalAlign(VerticalAlign.FILL)
                cell()
            }.topGap(TopGap.NONE).resizableRow()

        }
    }.apply { background = WelcomeScreenUIManager.getMainAssociatedComponentBackground() }

    override val previousActionText = IdeBundle.message("button.back")
    override val nextActionText = CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.next.text")

    override fun onInit(wizardModel: CoderWorkspacesWizardModel) {
        wizard = wizardModel
        cs.launch {
            val workspaceList = withContext(Dispatchers.IO) {
                try {
                    val workspaces = coderClient.workspaces()
                    return@withContext workspaces.flatMap { workspace ->
                        val agents = coderClient.workspaceAgents(workspace)
                        val shouldContainAgentName = agents.size > 1
                        agents.map { agent ->
                            val workspaceName = if (shouldContainAgentName) "${workspace.name}.${agent.name}" else workspace.name
                            WorkspaceAgentModel(
                                workspaceName,
                                workspace.templateName,
                                workspace.latestBuild.job.status,
                                workspace.latestBuild.workspaceTransition,
                                OS.from(agent.operatingSystem),
                                Arch.from(agent.architecture),
                                agent.directory
                            )
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Could not retrieve workspaces for ${coderClient.me.username} on ${coderClient.coderURL}. Reason: $e")
                    emptyList()
                }
            }

            // if we just run the update on the main dispatcher, the code will block because it cant get some AWT locks
            ApplicationManager.getApplication().invokeLater { listTableModelOfWorkspaces.updateItems(workspaceList) }
        }
    }

    override fun onNext(wizardModel: CoderWorkspacesWizardModel): Boolean {
        val workspace = tableOfWorkspaces.selectedObject
        if (workspace != null) {
            wizardModel.selectedWorkspace = workspace
            return true
        }
        return false
    }

    override fun dispose() {
        cs.cancel()
    }

    private class WorkspaceIconColumnInfo(columnName: String) : ColumnInfo<WorkspaceAgentModel, String>(columnName) {
        override fun valueOf(workspace: WorkspaceAgentModel?): String? {
            return workspace?.agentOS?.name
        }

        override fun getRenderer(item: WorkspaceAgentModel?): TableCellRenderer {
            return object : IconTableCellRenderer<String>() {
                override fun getText(): String {
                    return ""
                }

                override fun getIcon(value: String, table: JTable?, row: Int): Icon {
                    return when (OS.from(value)) {
                        OS.LINUX -> CoderIcons.LINUX
                        OS.WINDOWS -> CoderIcons.WINDOWS
                        OS.MAC -> CoderIcons.MACOS
                        else -> CoderIcons.UNKNOWN
                    }
                }

                override fun isCenterAlignment() = true
            }
        }
    }

    private class WorkspaceNameColumnInfo(columnName: String) : ColumnInfo<WorkspaceAgentModel, String>(columnName) {
        override fun valueOf(workspace: WorkspaceAgentModel?): String? {
            return workspace?.name
        }

        override fun getRenderer(item: WorkspaceAgentModel?): TableCellRenderer {
            return object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
                    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                    if (value is String) {
                        text = value
                    }
                    font = JBFont.h3()
                    return this
                }
            }
        }
    }

    private class WorkspaceTemplateNameColumnInfo(columnName: String) : ColumnInfo<WorkspaceAgentModel, String>(columnName) {
        override fun valueOf(workspace: WorkspaceAgentModel?): String? {
            return workspace?.templateName
        }

        override fun getRenderer(item: WorkspaceAgentModel?): TableCellRenderer {
            return object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
                    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                    if (value is String) {
                        text = value
                    }
                    font = JBFont.h3()
                    return this
                }
            }
        }
    }

    private class WorkspaceStatusColumnInfo(columnName: String) : ColumnInfo<WorkspaceAgentModel, String>(columnName) {
        override fun valueOf(workspace: WorkspaceAgentModel?): String? {
            return workspace?.statusLabel()
        }

        override fun getRenderer(item: WorkspaceAgentModel?): TableCellRenderer {
            return object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
                    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                    if (value is String) {
                        text = value
                    }
                    font = JBFont.h3()
                    foreground = (table.model as ListTableModel<WorkspaceAgentModel>).getRowValue(row).statusColor()
                    return this
                }
            }
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

        private fun WorkspaceAgentModel.statusColor() = when (this.jobStatus) {
            ProvisionerJobStatus.SUCCEEDED -> if (this.buildTransition == WorkspaceBuildTransition.START) Color.GREEN else Color.RED
            ProvisionerJobStatus.RUNNING -> when (this.buildTransition) {
                WorkspaceBuildTransition.START, WorkspaceBuildTransition.STOP, WorkspaceBuildTransition.DELETE -> Color.GRAY
            }

            else -> Color.RED
        }
    }


    private fun ListTableModel<WorkspaceAgentModel>.updateItems(workspaces: Collection<WorkspaceAgentModel>) {
        while (this.rowCount > 0) this.removeRow(0)
        this.addRows(workspaces)
    }

    companion object {
        val logger = Logger.getInstance(CoderWorkspacesStepView::class.java.simpleName)
    }
}