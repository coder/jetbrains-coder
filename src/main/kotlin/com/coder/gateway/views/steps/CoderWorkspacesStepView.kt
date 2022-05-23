package com.coder.gateway.views.steps

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.models.CoderWorkspacesWizardModel
import com.coder.gateway.models.Workspace
import com.coder.gateway.sdk.CoderClientService
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.CollectionListModel
import com.intellij.ui.IconManager
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.util.ui.JBFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.DefaultComboBoxModel

class CoderWorkspacesStepView : CoderWorkspacesWizardStep, Disposable {
    private val cs = CoroutineScope(Dispatchers.Main)
    private var workspaces = CollectionListModel<Workspace>()
    private var workspacesView = JBList(workspaces)
    private val coderClient: CoderClientService = ApplicationManager.getApplication().getService(CoderClientService::class.java)

    override val component = panel {
        val model = DefaultComboBoxModel(arrayOf("IntelliJ IDEA", "PyCharm", "Goland"))
        indent {
            row {
                label(CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.choose.text")).applyToComponent {
                    font = JBFont.h3().asBold()
                    icon = IconManager.getInstance().getIcon("coder_logo_16.svg", this@CoderWorkspacesStepView::class.java)
                }
            }.bottomGap(BottomGap.MEDIUM)
            row {
                label("IDE version:")
                comboBox(model)
                    .gap(RightGap.SMALL)
                label("Project directory:")
                textField()
                    .resizableColumn()
                    .horizontalAlign(HorizontalAlign.FILL)
                    .applyToComponent {
                        this.text = "/home/ifaur/workspace/"
                    }
                cell()
            }.topGap(TopGap.NONE)

            row {
                scrollCell(workspacesView).resizableColumn().horizontalAlign(HorizontalAlign.FILL).verticalAlign(VerticalAlign.FILL)
                cell()
            }.resizableRow()

        }
    }.apply { background = WelcomeScreenUIManager.getMainAssociatedComponentBackground() }

    override val previousActionText = IdeBundle.message("button.back")
    override val nextActionText = CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.next.text")

    override fun onInit(wizardModel: CoderWorkspacesWizardModel) {
        workspaces.removeAll()
        workspacesView.cellRenderer = WorkspaceCellRenderer()

        cs.launch {
            val workspaceList = withContext(Dispatchers.IO) {
                coderClient.workspaces()
            }
            workspaceList.forEach {
                workspaces.add(it)
            }
        }
    }

    override suspend fun onNext(wizardModel: CoderWorkspacesWizardModel) {

    }

    override fun dispose() {

    }
}