package com.coder.gateway.views.steps

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.icons.CoderIcons
import com.coder.gateway.models.CoderWorkspacesWizardModel
import com.coder.gateway.sdk.CoderRestClientService
import com.coder.gateway.views.LazyBrowserLink
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.remote.AuthType
import com.intellij.remote.RemoteCredentialsHolder
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.gateway.api.GatewayUI
import com.jetbrains.gateway.ssh.CachingProductsJsonWrapper
import com.jetbrains.gateway.ssh.IdeStatus
import com.jetbrains.gateway.ssh.IdeWithStatus
import com.jetbrains.gateway.ssh.IntelliJPlatformProduct
import com.jetbrains.gateway.ssh.guessOs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants

class CoderLocateRemoteProjectStepView : CoderWorkspacesWizardStep, Disposable {
    private val cs = CoroutineScope(Dispatchers.Main)
    private val coderClient: CoderRestClientService = ApplicationManager.getApplication().getService(CoderRestClientService::class.java)

    private val spinner = JLabel("", AnimatedIcon.Default(), SwingConstants.LEFT)
    private var ideComboBoxModel = DefaultComboBoxModel<IdeWithStatus>()

    private lateinit var titleLabel: JLabel
    private lateinit var wizard: CoderWorkspacesWizardModel
    private lateinit var cbIDE: IDEComboBox
    private lateinit var tfProject: JBTextField
    private lateinit var terminalLink: LazyBrowserLink

    override val component = panel {
        indent {
            row {
                titleLabel = label("").applyToComponent {
                    font = JBFont.h3().asBold()
                    icon = CoderIcons.LOGO_16
                }.component
            }.bottomGap(BottomGap.MEDIUM)

            row {
                label("IDE:")
                cbIDE = cell(IDEComboBox(ideComboBoxModel).apply {
                    renderer = IDECellRenderer()
                }).resizableColumn().horizontalAlign(HorizontalAlign.FILL).comment("The IDE will be downloaded from jetbrains.com").component
                cell()
            }.topGap(TopGap.NONE).layout(RowLayout.PARENT_GRID)

            row {
                label("Project directory:")
                tfProject = textField()
                    .resizableColumn()
                    .horizontalAlign(HorizontalAlign.FILL)
                    .applyToComponent {
                        this.text = "/home/coder/workspace/"
                    }.component
                cell()
            }.topGap(TopGap.NONE).bottomGap(BottomGap.NONE).layout(RowLayout.PARENT_GRID)
            row {
                cell()
                terminalLink = cell(
                    LazyBrowserLink(
                        CoderIcons.OPEN_TERMINAL,
                        "Open Terminal"
                    )
                ).component
            }.topGap(TopGap.NONE).layout(RowLayout.PARENT_GRID)
        }
    }.apply { background = WelcomeScreenUIManager.getMainAssociatedComponentBackground() }

    override val previousActionText = IdeBundle.message("button.back")
    override val nextActionText = CoderGatewayBundle.message("gateway.connector.view.coder.remoteproject.next.text")

    override fun onInit(wizardModel: CoderWorkspacesWizardModel) {
        wizard = wizardModel
        val selectedWorkspace = wizardModel.selectedWorkspace
        if (selectedWorkspace == null) {
            logger.warn("No workspace was selected. Please go back to the previous step and select a Coder Workspace")
            return
        }

        titleLabel.text = CoderGatewayBundle.message("gateway.connector.view.coder.remoteproject.choose.text", selectedWorkspace.name)
        terminalLink.url = "${coderClient.coderURL}/@${coderClient.me.username}/${selectedWorkspace.name}.coder/terminal"

        cs.launch {
            logger.info("Retrieving available IDE's for ${selectedWorkspace.name} workspace...")
            val workspaceOS = withContext(Dispatchers.IO) {
                RemoteCredentialsHolder().apply {
                    setHost("coder.${selectedWorkspace.name}")
                    userName = "coder"
                    authType = AuthType.OPEN_SSH
                }.guessOs
            }
            logger.info("Resolved OS and Arch for ${selectedWorkspace.name} is: $workspaceOS")
            val idesWithStatus = IntelliJPlatformProduct.values()
                .filter { it.showInGateway }
                .flatMap { CachingProductsJsonWrapper.getAvailableIdes(it, workspaceOS) }
                .map { ide -> IdeWithStatus(ide.product, ide.buildNumber, IdeStatus.DOWNLOAD, ide.downloadLink, ide.presentableVersion) }

            if (idesWithStatus.isNullOrEmpty()) {
                logger.warn("Could not resolve any IDE for workspace ${selectedWorkspace.name}, probably $workspaceOS is not supported by Gateway")
            } else {
                cbIDE.remove(spinner)
                ideComboBoxModel.addAll(idesWithStatus)
                cbIDE.selectedIndex = 0
            }
        }
    }

    override fun onNext(wizardModel: CoderWorkspacesWizardModel): Boolean {
        val selectedIDE = cbIDE.selectedItem ?: return false

        cs.launch {

            GatewayUI.getInstance().connect(
                mapOf(
                    "type" to "coder",
                    "coder_workspace_hostname" to "coder.${wizardModel.selectedWorkspace?.name}",
                    "project_path" to tfProject.text,
                    "ide_product_code" to "${selectedIDE.product.productCode}",
                    "ide_build_number" to "${selectedIDE.buildNumber}",
                    "ide_download_link" to "${selectedIDE.source}"
                )
            )
        }
        return true
    }

    override fun dispose() {
    }

    companion object {
        val logger = Logger.getInstance(CoderLocateRemoteProjectStepView::class.java.simpleName)
    }

    private class IDEComboBox(model: ComboBoxModel<IdeWithStatus>) : ComboBox<IdeWithStatus>(model) {
        override fun getSelectedItem(): IdeWithStatus? {
            return super.getSelectedItem() as IdeWithStatus?
        }
    }

    private class IDECellRenderer : ListCellRenderer<IdeWithStatus> {
        override fun getListCellRendererComponent(list: JList<out IdeWithStatus>?, ideWithStatus: IdeWithStatus?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
            return if (ideWithStatus == null && index == -1) {
                JPanel().apply {
                    layout = FlowLayout(FlowLayout.LEFT)
                    add(JLabel("Retrieving products...", AnimatedIcon.Default(), SwingConstants.LEFT))
                }
            } else if (ideWithStatus != null) {
                JPanel().apply {
                    layout = FlowLayout(FlowLayout.LEFT)
                    add(JLabel(ideWithStatus.product.ideName, ideWithStatus.product.icon, SwingConstants.LEFT))
                    add(JLabel("${ideWithStatus.product.productCode} ${ideWithStatus.presentableVersion} ${ideWithStatus.buildNumber} | ${ideWithStatus.status.name.toLowerCase()}").apply {
                        foreground = UIUtil.getLabelDisabledForeground()
                    })
                    background = JBUI.CurrentTheme.List.background(isSelected, cellHasFocus)
                }
            } else {
                JPanel()
            }
        }
    }
}