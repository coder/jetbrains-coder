package com.coder.gateway.views.steps

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.icons.CoderIcons
import com.coder.gateway.models.CoderWorkspacesWizardModel
import com.coder.gateway.models.WorkspaceAgentModel
import com.coder.gateway.sdk.Arch
import com.coder.gateway.sdk.CoderRestClientService
import com.coder.gateway.sdk.OS
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.util.ui.JBFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CoderWorkspacesStepView : CoderWorkspacesWizardStep, Disposable {
    private val cs = CoroutineScope(Dispatchers.Main)

    private val coderClient: CoderRestClientService = ApplicationManager.getApplication().getService(CoderRestClientService::class.java)
    private var workspaces = CollectionListModel<WorkspaceAgentModel>()
    private var workspacesView = JBList(workspaces)

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
                scrollCell(workspacesView).resizableColumn().horizontalAlign(HorizontalAlign.FILL).verticalAlign(VerticalAlign.FILL)
                cell()
            }.topGap(TopGap.NONE).resizableRow()

        }
    }.apply { background = WelcomeScreenUIManager.getMainAssociatedComponentBackground() }

    override val previousActionText = IdeBundle.message("button.back")
    override val nextActionText = CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.next.text")

    override fun onInit(wizardModel: CoderWorkspacesWizardModel) {
        wizard = wizardModel
        workspaces.removeAll()
        workspacesView.cellRenderer = WorkspaceCellRenderer()

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
                                workspace.latestBuild.job.status,
                                workspace.latestBuild.workspaceTransition,
                                OS.from(agent.operatingSystem),
                                Arch.from(agent.architecture)

                            )
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Could not retrieve workspaces for ${coderClient.me.username} on ${coderClient.coderURL}. Reason: $e")
                    emptyList()
                }
            }
            workspaceList.forEach {
                workspaces.add(it)
            }
        }
    }

    override fun onNext(wizardModel: CoderWorkspacesWizardModel): Boolean {
        val workspace = workspacesView.selectedValue
        if (workspace != null) {
            wizardModel.selectedWorkspace = workspace
            return true
        }
        return false
    }

    override fun dispose() {
        cs.cancel()
    }

    companion object {
        val logger = Logger.getInstance(CoderWorkspacesStepView::class.java.simpleName)
    }
}