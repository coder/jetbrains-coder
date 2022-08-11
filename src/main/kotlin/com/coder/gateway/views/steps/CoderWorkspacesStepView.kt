package com.coder.gateway.views.steps

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.icons.CoderIcons
import com.coder.gateway.models.CoderWorkspacesWizardModel
import com.coder.gateway.models.WorkspaceAgentModel
import com.coder.gateway.models.WorkspaceAgentStatus
import com.coder.gateway.models.WorkspaceAgentStatus.DELETING
import com.coder.gateway.models.WorkspaceAgentStatus.RUNNING
import com.coder.gateway.models.WorkspaceAgentStatus.STARTING
import com.coder.gateway.models.WorkspaceAgentStatus.STOPPING
import com.coder.gateway.sdk.Arch
import com.coder.gateway.sdk.CoderCLIManager
import com.coder.gateway.sdk.CoderRestClientService
import com.coder.gateway.sdk.OS
import com.coder.gateway.sdk.ex.AuthenticationResponseException
import com.coder.gateway.sdk.getOS
import com.coder.gateway.sdk.toURL
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.withPath
import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.AppIcon
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bindText
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.zeroturnaround.exec.ProcessExecutor
import java.awt.Component
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer


class CoderWorkspacesStepView(val enableNextButtonCallback: (Boolean) -> Unit) : CoderWorkspacesWizardStep, Disposable {
    private val cs = CoroutineScope(Dispatchers.Main)
    private var wizardModel = CoderWorkspacesWizardModel()
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
        selectionModel.addListSelectionListener {
            enableNextButtonCallback(selectedObject != null && selectedObject?.agentStatus == RUNNING)
        }
    }

    private var poller: Job? = null

    override val component = panel {
        indent {
            row {
                label(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.header.text")).applyToComponent {
                    font = JBFont.h3().asBold()
                    icon = CoderIcons.LOGO_16
                }
            }.topGap(TopGap.SMALL).bottomGap(BottomGap.MEDIUM)
            row {
                cell(ComponentPanelBuilder.createCommentComponent(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.comment"), false, -1, true))
            }
            row {
                browserLink(CoderGatewayBundle.message("gateway.connector.view.login.documentation.action"), "https://coder.com/docs/coder-oss/latest/workspaces")
            }.bottomGap(BottomGap.MEDIUM)
            row(CoderGatewayBundle.message("gateway.connector.view.login.url.label")) {
                textField().resizableColumn().horizontalAlign(HorizontalAlign.FILL).gap(RightGap.SMALL).bindText(wizardModel::coderURL).applyToComponent {
                    addActionListener {
                        poller?.cancel()
                        loginAndLoadWorkspace()
                    }
                }
                button(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.connect.text")) {
                    poller?.cancel()
                    loginAndLoadWorkspace()
                }.applyToComponent {
                    background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
                }
                cell()
            }
            row {
                scrollCell(tableOfWorkspaces).resizableColumn().horizontalAlign(HorizontalAlign.FILL).verticalAlign(VerticalAlign.FILL)
                cell()
            }.topGap(TopGap.NONE).resizableRow()

        }
    }.apply { background = WelcomeScreenUIManager.getMainAssociatedComponentBackground() }

    override val previousActionText = IdeBundle.message("button.back")
    override val nextActionText = CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.next.text")

    override fun onInit(wizardModel: CoderWorkspacesWizardModel) {
        enableNextButtonCallback(false)
    }

    private fun loginAndLoadWorkspace() {
        // force bindings to be filled
        component.apply()

        BrowserUtil.browse(wizardModel.coderURL.toURL().withPath("/login?redirect=%2Fcli-auth"))
        val pastedToken = askToken()

        if (pastedToken.isNullOrBlank()) {
            return
        }
        try {
            coderClient.initClientSession(wizardModel.coderURL.toURL(), pastedToken)
        } catch (e: AuthenticationResponseException) {
            logger.error("Could not authenticate on ${wizardModel.coderURL}. Reason $e")
            return
        }
        wizardModel.apply {
            token = pastedToken
            buildVersion = coderClient.buildVersion
        }

        val authTask = object : Task.Modal(null, CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.cli.downloader.dialog.title"), false) {
            override fun run(pi: ProgressIndicator) {

                pi.apply {
                    isIndeterminate = false
                    text = "Downloading coder cli..."
                    fraction = 0.1
                }

                val cliManager = CoderCLIManager(wizardModel.coderURL.toURL(), wizardModel.buildVersion)
                val cli = cliManager.download() ?: throw IllegalStateException("Could not download coder binary")
                if (getOS() != OS.WINDOWS) {
                    pi.fraction = 0.4
                    val chmodOutput = ProcessExecutor().command("chmod", "+x", cli.toAbsolutePath().toString()).readOutput(true).execute().outputUTF8()
                    logger.info("chmod +x ${cli.toAbsolutePath()} $chmodOutput")
                }
                pi.apply {
                    text = "Configuring coder cli..."
                    fraction = 0.5
                }

                val loginOutput = ProcessExecutor().command(cli.toAbsolutePath().toString(), "login", wizardModel.coderURL, "--token", wizardModel.token).readOutput(true).execute().outputUTF8()
                logger.info("coder-cli login output: $loginOutput")
                pi.fraction = 0.8
                val sshConfigOutput = ProcessExecutor().command(cli.toAbsolutePath().toString(), "config-ssh", "--yes", "--use-previous-options").readOutput(true).execute().outputUTF8()
                logger.info("Result of `${cli.toAbsolutePath()} config-ssh --yes --use-previous-options`: $sshConfigOutput")
                pi.fraction = 1.0
            }
        }

        wizardModel.apply {
            coderURL = wizardModel.coderURL
            token = wizardModel.token
        }
        ProgressManager.getInstance().run(authTask)
        loadWorkspaces()
    }

    private fun askToken(): String? {
        return invokeAndWaitIfNeeded(ModalityState.any()) {
            lateinit var sessionTokenTextField: JBTextField

            val panel = panel {
                row {
                    label(CoderGatewayBundle.message("gateway.connector.view.login.token.label"))
                    sessionTokenTextField = textField().applyToComponent {
                        minimumSize = Dimension(320, -1)
                    }.component
                }
            }

            AppIcon.getInstance().requestAttention(null, true)
            if (!dialog(CoderGatewayBundle.message("gateway.connector.view.login.token.dialog"), panel = panel, focusedComponent = sessionTokenTextField).showAndGet()) {
                return@invokeAndWaitIfNeeded null
            }
            return@invokeAndWaitIfNeeded sessionTokenTextField.text
        }
    }

    private fun loadWorkspaces() {
        poller = cs.launch {
            while (isActive) {
                val workspaceList = withContext(Dispatchers.IO) {
                    try {
                        return@withContext coderClient.workspaces().collectAgents()
                    } catch (e: Exception) {
                        logger.error("Could not retrieve workspaces for ${coderClient.me.username} on ${coderClient.coderURL}. Reason: $e")
                        emptyList()
                    }
                }

                val selectedWorkspace = withContext(Dispatchers.Main) {
                    tableOfWorkspaces.selectedObject
                }

                // if we just run the update on the main dispatcher, the code will block because it cant get some AWT locks
                ApplicationManager.getApplication().invokeLater {
                    listTableModelOfWorkspaces.updateItems(workspaceList)
                    if (selectedWorkspace != null) {
                        tableOfWorkspaces.selectItem(selectedWorkspace)
                    }
                }
            }
            delay(5000)
        }
    }

    private fun List<Workspace>.collectAgents(): List<WorkspaceAgentModel> {
        return this.flatMap { it.agentModels() }.toList()
    }

    private fun Workspace.agentModels(): List<WorkspaceAgentModel> {
        return try {
            val agents = coderClient.workspaceAgents(this)
            when (agents.size) {
                0 -> {
                    listOf(
                        WorkspaceAgentModel(
                            this.name,
                            this.templateName,
                            WorkspaceAgentStatus.from(this),
                            null,
                            null,
                            null
                        )
                    )
                }

                1 -> {
                    listOf(
                        WorkspaceAgentModel(
                            this.name,
                            this.templateName,
                            WorkspaceAgentStatus.from(this),
                            OS.from(agents[0].operatingSystem),
                            Arch.from(agents[0].architecture),
                            agents[0].directory
                        )
                    )
                }

                else -> agents.map { agent ->
                    val workspaceName = "${this.name}.${agent.name}"
                    WorkspaceAgentModel(
                        workspaceName,
                        this.templateName,
                        WorkspaceAgentStatus.from(this),
                        OS.from(agent.operatingSystem),
                        Arch.from(agent.architecture),
                        agent.directory
                    )
                }
                    .toList()
            }
        } catch (e: Exception) {
            logger.error("Skipping workspace ${this.name} because we could not retrieve the agent(s). Reason: $e")
            emptyList()
        }
    }

    override fun onPrevious() {
        super.onPrevious()
        poller?.cancel()
    }

    override fun onNext(wizardModel: CoderWorkspacesWizardModel): Boolean {
        val workspace = tableOfWorkspaces.selectedObject
        if (workspace != null) {
            wizardModel.selectedWorkspace = workspace
            poller?.cancel()
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
            return workspace?.agentStatus?.label
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

        private fun WorkspaceAgentModel.statusColor() = when (this.agentStatus) {
            RUNNING -> JBColor.GREEN
            STARTING, STOPPING, DELETING -> if (JBColor.isBright()) JBColor.LIGHT_GRAY else JBColor.DARK_GRAY
            else -> JBColor.RED
        }
    }


    private fun ListTableModel<WorkspaceAgentModel>.updateItems(workspaces: Collection<WorkspaceAgentModel>) {
        while (this.rowCount > 0) this.removeRow(0)
        this.addRows(workspaces)
    }

    private fun TableView<WorkspaceAgentModel>.selectItem(workspace: WorkspaceAgentModel) {
        this.items.forEachIndexed { index, workspaceAgentModel ->
            if (workspaceAgentModel.name == workspace.name)
                this.setRowSelectionInterval(index, index)
        }
    }

    companion object {
        val logger = Logger.getInstance(CoderWorkspacesStepView::class.java.simpleName)
    }
}