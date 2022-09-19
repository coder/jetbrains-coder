package com.coder.gateway.views.steps

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.icons.CoderIcons
import com.coder.gateway.models.CoderWorkspacesWizardModel
import com.coder.gateway.models.WorkspaceAgentModel
import com.coder.gateway.models.WorkspaceAgentStatus
import com.coder.gateway.models.WorkspaceAgentStatus.DELETING
import com.coder.gateway.models.WorkspaceAgentStatus.FAILED
import com.coder.gateway.models.WorkspaceAgentStatus.RUNNING
import com.coder.gateway.models.WorkspaceAgentStatus.STARTING
import com.coder.gateway.models.WorkspaceAgentStatus.STOPPED
import com.coder.gateway.models.WorkspaceAgentStatus.STOPPING
import com.coder.gateway.models.WorkspaceVersionStatus
import com.coder.gateway.sdk.Arch
import com.coder.gateway.sdk.CoderCLIDownloader
import com.coder.gateway.sdk.CoderCLIManager
import com.coder.gateway.sdk.CoderRestClientService
import com.coder.gateway.sdk.OS
import com.coder.gateway.sdk.ex.AuthenticationResponseException
import com.coder.gateway.sdk.ex.TemplateResponseException
import com.coder.gateway.sdk.ex.WorkspaceResponseException
import com.coder.gateway.sdk.getOS
import com.coder.gateway.sdk.toURL
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.withPath
import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.AnActionButton
import com.intellij.ui.AppIcon
import com.intellij.ui.JBColor
import com.intellij.ui.ToolbarDecorator
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
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer


private const val CODER_URL_KEY = "coder-url"

private const val SESSION_TOKEN = "session-token"

class CoderWorkspacesStepView(val enableNextButtonCallback: (Boolean) -> Unit) : CoderWorkspacesWizardStep, Disposable {
    private val cs = CoroutineScope(Dispatchers.Main)
    private var localWizardModel = CoderWorkspacesWizardModel()
    private val coderClient: CoderRestClientService = service()
    private val appPropertiesService: PropertiesComponent = service()

    private var tfUrl: JTextField? = null
    private var listTableModelOfWorkspaces = ListTableModel<WorkspaceAgentModel>(
        WorkspaceIconColumnInfo(""),
        WorkspaceNameColumnInfo("Name"),
        WorkspaceTemplateNameColumnInfo("Template"),
        WorkspaceVersionColumnInfo("Version"),
        WorkspaceStatusColumnInfo("Status")
    )

    private val notificationBand = JLabel()
    private var tableOfWorkspaces = TableView(listTableModelOfWorkspaces).apply {
        setEnableAntialiasing(true)
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
            enableNextButtonCallback(selectedObject != null && selectedObject?.agentStatus == RUNNING && selectedObject?.agentOS == OS.LINUX)
            if (selectedObject?.agentOS != OS.LINUX) {
                notificationBand.apply {
                    isVisible = true
                    icon = AllIcons.General.Information
                    text = CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.unsupported.os.info")
                }
            } else {
                notificationBand.isVisible = false
            }
            updateWorkspaceActions()
        }
    }

    private val startWorkspaceAction = StartWorkspaceAction()
    private val stopWorkspaceAction = StopWorkspaceAction()
    private val updateWorkspaceTemplateAction = UpdateWorkspaceTemplateAction()

    private val toolbar = ToolbarDecorator.createDecorator(tableOfWorkspaces)
        .disableAddAction()
        .disableRemoveAction()
        .disableUpDownActions()
        .addExtraAction(startWorkspaceAction)
        .addExtraAction(stopWorkspaceAction)
        .addExtraAction(updateWorkspaceTemplateAction)

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
                tfUrl = textField().resizableColumn().horizontalAlign(HorizontalAlign.FILL).gap(RightGap.SMALL).bindText(localWizardModel::coderURL).applyToComponent {
                    addActionListener {
                        poller?.cancel()
                        askTokenAndOpenSession()
                    }
                }.component
                button(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.connect.text")) {
                    poller?.cancel()
                    askTokenAndOpenSession()
                }.applyToComponent {
                    background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
                }
                cell()
            }
            row {
                scrollCell(toolbar.createPanel()).resizableColumn().horizontalAlign(HorizontalAlign.FILL).verticalAlign(VerticalAlign.FILL)
                cell()
            }.topGap(TopGap.NONE).resizableRow()
            row {
                cell(notificationBand).resizableColumn().horizontalAlign(HorizontalAlign.FILL).applyToComponent {
                    font = JBFont.h4().asBold()
                    isVisible = false
                }
            }
        }
    }.apply { background = WelcomeScreenUIManager.getMainAssociatedComponentBackground() }

    override val previousActionText = IdeBundle.message("button.back")
    override val nextActionText = CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.next.text")

    private inner class StartWorkspaceAction : AnActionButton(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.start.text"), CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.start.text"), CoderIcons.RUN) {
        override fun actionPerformed(p0: AnActionEvent) {
            if (tableOfWorkspaces.selectedObject != null) {
                val workspace = tableOfWorkspaces.selectedObject as WorkspaceAgentModel
                cs.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            coderClient.startWorkspace(workspace.workspaceID, workspace.workspaceName)
                            loadWorkspaces()
                        } catch (e: WorkspaceResponseException) {
                            logger.warn("Could not build workspace ${workspace.name}, reason: $e")
                        }
                    }
                }
            }
        }
    }

    private inner class UpdateWorkspaceTemplateAction : AnActionButton(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.update.text"), CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.update.text"), CoderIcons.UPDATE) {
        override fun actionPerformed(p0: AnActionEvent) {
            if (tableOfWorkspaces.selectedObject != null) {
                val workspace = tableOfWorkspaces.selectedObject as WorkspaceAgentModel
                cs.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            coderClient.updateWorkspace(workspace.workspaceID, workspace.workspaceName, workspace.lastBuildTransition, workspace.templateID)
                            loadWorkspaces()
                        } catch (e: WorkspaceResponseException) {
                            logger.warn("Could not update workspace ${workspace.name}, reason: $e")
                        } catch (e: TemplateResponseException) {
                            logger.warn("Could not update workspace ${workspace.name}, reason: $e")
                        }
                    }
                }
            }
        }
    }

    private inner class StopWorkspaceAction : AnActionButton(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.stop.text"), CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.stop.text"), CoderIcons.STOP) {
        override fun actionPerformed(p0: AnActionEvent) {
            if (tableOfWorkspaces.selectedObject != null) {
                val workspace = tableOfWorkspaces.selectedObject as WorkspaceAgentModel
                cs.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            coderClient.stopWorkspace(workspace.workspaceID, workspace.workspaceName)
                            loadWorkspaces()
                        } catch (e: WorkspaceResponseException) {
                            logger.warn("Could not stop workspace ${workspace.name}, reason: $e")
                        }
                    }
                }
            }
        }
    }

    override fun onInit(wizardModel: CoderWorkspacesWizardModel) {
        enableNextButtonCallback(false)
        if (localWizardModel.coderURL.isNotBlank() && localWizardModel.token.isNotBlank()) {
            triggerWorkspacePolling()
        } else {
            val url = appPropertiesService.getValue(CODER_URL_KEY)
            val token = appPropertiesService.getValue(SESSION_TOKEN)
            if (!url.isNullOrBlank() && !token.isNullOrBlank()) {
                localWizardModel.coderURL = url
                localWizardModel.token = token
                tfUrl?.text = url

                poller?.cancel()
                loginAndLoadWorkspace(token)
            }
        }
        updateWorkspaceActions()
    }

    private fun updateWorkspaceActions() {
        when (tableOfWorkspaces.selectedObject?.agentStatus) {
            RUNNING -> {
                startWorkspaceAction.isEnabled = false
                stopWorkspaceAction.isEnabled = true
                when (tableOfWorkspaces.selectedObject?.status) {
                    WorkspaceVersionStatus.OUTDATED -> updateWorkspaceTemplateAction.isEnabled = true
                    else -> updateWorkspaceTemplateAction.isEnabled = false
                }

            }

            STOPPED, FAILED -> {
                startWorkspaceAction.isEnabled = true
                stopWorkspaceAction.isEnabled = false
                when (tableOfWorkspaces.selectedObject?.status) {
                    WorkspaceVersionStatus.OUTDATED -> updateWorkspaceTemplateAction.isEnabled = true
                    else -> updateWorkspaceTemplateAction.isEnabled = false
                }
            }

            else -> {
                startWorkspaceAction.isEnabled = false
                stopWorkspaceAction.isEnabled = false
                updateWorkspaceTemplateAction.isEnabled = false
            }
        }
        ActivityTracker.getInstance().inc()
    }

    private fun askTokenAndOpenSession() {
        // force bindings to be filled
        component.apply()

        val pastedToken = askToken()
        if (pastedToken.isNullOrBlank()) {
            return
        }
        loginAndLoadWorkspace(pastedToken)
    }

    private fun loginAndLoadWorkspace(token: String) {
        try {
            coderClient.initClientSession(localWizardModel.coderURL.toURL(), token)
        } catch (e: AuthenticationResponseException) {
            logger.error("Could not authenticate on ${localWizardModel.coderURL}. Reason $e")
            return
        }
        appPropertiesService.setValue(CODER_URL_KEY, localWizardModel.coderURL)
        appPropertiesService.setValue(SESSION_TOKEN, token)
        val cliManager = CoderCLIManager(localWizardModel.coderURL.toURL(), coderClient.buildVersion)


        localWizardModel.apply {
            this.token = token
            buildVersion = coderClient.buildVersion
            localCliPath = cliManager.localCliPath.toAbsolutePath().toString()
        }

        val authTask = object : Task.Modal(null, CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.cli.downloader.dialog.title"), false) {
            override fun run(pi: ProgressIndicator) {

                pi.apply {
                    isIndeterminate = false
                    text = "Downloading coder cli..."
                    fraction = 0.1
                }

                CoderCLIDownloader().downloadCLI(cliManager.remoteCliPath, cliManager.localCliPath)
                if (getOS() != OS.WINDOWS) {
                    pi.fraction = 0.4
                    val chmodOutput = ProcessExecutor().command("chmod", "+x", localWizardModel.localCliPath).readOutput(true).execute().outputUTF8()
                    logger.info("chmod +x ${cliManager.localCliPath.toAbsolutePath()} $chmodOutput")
                }
                pi.apply {
                    text = "Configuring coder cli..."
                    fraction = 0.5
                }

                val loginOutput = ProcessExecutor().command(localWizardModel.localCliPath, "login", localWizardModel.coderURL, "--token", localWizardModel.token).readOutput(true).execute().outputUTF8()
                logger.info("coder-cli login output: $loginOutput")
                pi.fraction = 0.8
                val sshConfigOutput = ProcessExecutor().command(localWizardModel.localCliPath, "config-ssh", "--yes", "--use-previous-options").readOutput(true).execute().outputUTF8()
                logger.info("Result of `${localWizardModel.localCliPath} config-ssh --yes --use-previous-options`: $sshConfigOutput")
                pi.fraction = 1.0
            }
        }

        cs.launch {
            ProgressManager.getInstance().run(authTask)
        }
        triggerWorkspacePolling()
    }

    private fun askToken(): String? {
        BrowserUtil.browse(localWizardModel.coderURL.toURL().withPath("/login?redirect=%2Fcli-auth"))
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

    private fun triggerWorkspacePolling() {
        poller?.cancel()

        poller = cs.launch {
            while (isActive) {
                loadWorkspaces()
                delay(5000)
            }
        }
    }

    private suspend fun loadWorkspaces() {
        val workspaceList = withContext(Dispatchers.IO) {
            try {
                return@withContext coderClient.workspaces().collectAgents()
            } catch (e: Exception) {
                logger.error("Could not retrieve workspaces for ${coderClient.me.username} on ${coderClient.coderURL}. Reason: $e")
                emptyList()
            }
        }

        withContext(Dispatchers.Main) {
            val selectedWorkspace = tableOfWorkspaces.selectedObject?.name
            listTableModelOfWorkspaces.items = workspaceList
            if (selectedWorkspace != null) {
                tableOfWorkspaces.selectItem(selectedWorkspace)
            }
        }
    }

    private fun List<Workspace>.collectAgents(): List<WorkspaceAgentModel> {
        return this.flatMap { it.agentModels() }.toList()
    }

    private fun Workspace.agentModels(): List<WorkspaceAgentModel> {
        return try {
            val agents = coderClient.workspaceAgentsByTemplate(this)
            when (agents.size) {
                0 -> {
                    listOf(
                        WorkspaceAgentModel(
                            this.id,
                            this.name,
                            this.name,
                            this.templateID,
                            this.templateName,
                            WorkspaceVersionStatus.from(this),
                            WorkspaceAgentStatus.from(this),
                            this.latestBuild.workspaceTransition.name.toLowerCase(),
                            null,
                            null,
                            null
                        )
                    )
                }

                else -> agents.map { agent ->
                    val workspaceWithAgentName = "${this.name}.${agent.name}"
                    WorkspaceAgentModel(
                        this.id,
                        this.name,
                        workspaceWithAgentName,
                        this.templateID,
                        this.templateName,
                        WorkspaceVersionStatus.from(this),
                        WorkspaceAgentStatus.from(this),
                        this.latestBuild.workspaceTransition.name.toLowerCase(),
                        OS.from(agent.operatingSystem),
                        Arch.from(agent.architecture),
                        agent.directory
                    )
                }.toList()
            }
        } catch (e: Exception) {
            logger.warn("Agent(s) for ${this.name} could not be retrieved. Reason: $e")
            listOf(
                WorkspaceAgentModel(
                    this.id,
                    this.name,
                    this.name,
                    this.templateID,
                    this.templateName,
                    WorkspaceVersionStatus.from(this),
                    WorkspaceAgentStatus.from(this),
                    this.latestBuild.workspaceTransition.name.toLowerCase(),
                    null,
                    null,
                    null
                )
            )
        }
    }

    override fun onPrevious() {
        super.onPrevious()
        poller?.cancel()
    }

    override fun onNext(wizardModel: CoderWorkspacesWizardModel): Boolean {
        if (localWizardModel.localCliPath.isNotBlank()) {
            val configSSHTask = object : Task.Modal(null, CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.cli.configssh.dialog.title"), false) {
                override fun run(pi: ProgressIndicator) {
                    pi.apply {
                        text = "Configuring coder cli..."
                        fraction = 0.1
                    }
                    val sshConfigOutput = ProcessExecutor().command(localWizardModel.localCliPath, "config-ssh", "--yes", "--use-previous-options").readOutput(true).execute().outputUTF8()
                    pi.fraction = 0.8
                    logger.info("Result of `${localWizardModel.localCliPath} config-ssh --yes --use-previous-options`: $sshConfigOutput")
                    pi.fraction = 1.0
                }
            }
            ProgressManager.getInstance().run(configSSHTask)
        }

        wizardModel.apply {
            coderURL = localWizardModel.coderURL
            token = localWizardModel.token
            buildVersion = localWizardModel.buildVersion
            localCliPath = localWizardModel.localCliPath
        }

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
                    font = JBFont.h3().asBold()
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

    private class WorkspaceVersionColumnInfo(columnName: String) : ColumnInfo<WorkspaceAgentModel, String>(columnName) {
        override fun valueOf(workspace: WorkspaceAgentModel?): String? {
            return workspace?.status?.label
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

    private fun TableView<WorkspaceAgentModel>.selectItem(workspaceName: String?) {
        if (workspaceName != null) {
            this.items.forEachIndexed { index, workspaceAgentModel ->
                if (workspaceAgentModel.name == workspaceName) {
                    selectionModel.addSelectionInterval(convertRowIndexToView(index), convertRowIndexToView(index))
                    // fix cell selection case
                    columnModel.selectionModel.addSelectionInterval(0, columnCount - 1)
                }
            }
        }
    }

    companion object {
        val logger = Logger.getInstance(CoderWorkspacesStepView::class.java.simpleName)
    }
}