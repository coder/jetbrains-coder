package com.coder.gateway.views.steps

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.models.CoderWorkspacesWizardModel
import com.coder.gateway.sdk.CoderCLIManager
import com.coder.gateway.sdk.CoderRestClientService
import com.coder.gateway.sdk.v2.models.Workspace
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.CollectionListModel
import com.intellij.ui.IconManager
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.util.ui.JBFont
import com.jetbrains.gateway.api.GatewayUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.zeroturnaround.exec.ProcessExecutor
import java.net.URL
import java.util.logging.Logger
import javax.swing.DefaultComboBoxModel

class CoderWorkspacesStepView : CoderWorkspacesWizardStep, Disposable {
    private val cs = CoroutineScope(Dispatchers.Main)
    private var workspaces = CollectionListModel<Workspace>()
    private var workspacesView = JBList(workspaces)

    private lateinit var tfProject: JBTextField
    private lateinit var wizardModel: CoderWorkspacesWizardModel

    private val coderClient: CoderRestClientService = ApplicationManager.getApplication().getService(CoderRestClientService::class.java)

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
                tfProject = textField()
                    .resizableColumn()
                    .horizontalAlign(HorizontalAlign.FILL)
                    .applyToComponent {
                        this.text = "/home/coder/workspace/"
                    }.component
                cell()
            }.topGap(TopGap.NONE)

            row {
                scrollCell(workspacesView).resizableColumn().horizontalAlign(HorizontalAlign.FILL).verticalAlign(VerticalAlign.FILL)
                cell()
            }.resizableRow()

        }
    }.apply { background = WelcomeScreenUIManager.getMainAssociatedComponentBackground() }

    override val previousActionText = IdeBundle.message("button.back")
    override val nextActionText = CoderGatewayBundle.message("gateway.connector.view.coder.workspaces.connect.text")

    override fun onInit(wm: CoderWorkspacesWizardModel) {
        wizardModel = wm
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
        val workspace = workspacesView.selectedValue
        if (workspace != null) {
            logger.info("Connecting to ${workspace.name}...")
            cs.launch {
                withContext(Dispatchers.IO) {
                    val url = URL(wizardModel.loginModel.uriScheme.toString().toLowerCase(), wizardModel.loginModel.host, wizardModel.loginModel.port, "")
                    val cliManager = CoderCLIManager(URL(url.protocol, url.host, url.port, ""))
                    val cli = cliManager.download() ?: throw IllegalStateException("Could not download coder binary")
                    val loginOutput = ProcessExecutor().command(cli.toAbsolutePath().toString(), "login", url.toString(), "--token", coderClient.sessionToken).readOutput(true).execute().outputUTF8()

                    logger.info("coder-cli login output: $loginOutput")
                    val sshConfigOutput = ProcessExecutor().command(cli.toAbsolutePath().toString(), "config-ssh").readOutput(true).execute().outputUTF8()
                    logger.info("coder-cli config-ssh output: $sshConfigOutput")
                }

                GatewayUI.getInstance().connect(
                    mapOf(
                        "type" to "coder",
                        "coder_url" to URL(wizardModel.loginModel.uriScheme.toString().toLowerCase(), wizardModel.loginModel.host, wizardModel.loginModel.port, "").toString(),
                        "workspace_name" to workspace.name,
                        "username" to coderClient.me.username,
                        "password" to wizardModel.loginModel.password!!,
                        "session_token" to coderClient.sessionToken,
                        "project_path" to tfProject.text
                    )
                )
            }
        }

    }

    override fun dispose() {

    }

    companion object {
        val logger = Logger.getLogger(CoderWorkspacesStepView::class.java.simpleName)
    }
}