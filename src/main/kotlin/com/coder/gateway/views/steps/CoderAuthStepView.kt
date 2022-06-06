package com.coder.gateway.views.steps

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.models.CoderWorkspacesWizardModel
import com.coder.gateway.sdk.CoderCLIManager
import com.coder.gateway.sdk.CoderRestClientService
import com.coder.gateway.sdk.OS
import com.coder.gateway.sdk.getOS
import com.coder.gateway.sdk.toURL
import com.coder.gateway.sdk.withPath
import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.AppIcon
import com.intellij.ui.IconManager
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.ui.JBFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.zeroturnaround.exec.ProcessExecutor
import java.awt.Dimension
import java.util.logging.Logger

class CoderAuthStepView : CoderWorkspacesWizardStep, Disposable {
    private val cs = CoroutineScope(Dispatchers.Main)
    private var model = CoderWorkspacesWizardModel()
    private val coderClient: CoderRestClientService = ApplicationManager.getApplication().getService(CoderRestClientService::class.java)

    override val component = panel {
        indent {
            row {
                label(CoderGatewayBundle.message("gateway.connector.view.login.header.text")).applyToComponent {
                    font = JBFont.h3().asBold()
                    icon = IconManager.getInstance().getIcon("coder_logo_16.svg", this@CoderAuthStepView::class.java)
                }
            }.topGap(TopGap.SMALL).bottomGap(BottomGap.MEDIUM)
            row {
                cell(ComponentPanelBuilder.createCommentComponent(CoderGatewayBundle.message("gateway.connector.view.login.comment.text"), false, -1, true))
            }
            row {
                browserLink(CoderGatewayBundle.message("gateway.connector.view.login.documentation.action"), "https://coder.com/docs/coder/latest/workspaces")
            }.bottomGap(BottomGap.MEDIUM)
            row(CoderGatewayBundle.message("gateway.connector.view.login.url.label")) {
                textField().resizableColumn().horizontalAlign(HorizontalAlign.FILL).gap(RightGap.SMALL).bindText(model::coderURL)
                cell()
            }
        }
    }.apply {
        background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
    }

    override val nextActionText = CoderGatewayBundle.message("gateway.connector.view.coder.auth.next.text")
    override val previousActionText = IdeBundle.message("button.back")

    override fun onInit(wizardModel: CoderWorkspacesWizardModel) {
        model.apply {
            coderURL = wizardModel.coderURL
            token = wizardModel.token
        }
        component.apply()
    }

    override fun onNext(wizardModel: CoderWorkspacesWizardModel): Boolean {
        BrowserUtil.browse(model.coderURL.toURL().withPath("/login?redirect=%2Fcli-auth"))
        val pastedToken = askToken()

        if (pastedToken?.isNullOrBlank() == true || coderClient.initClientSession(model.coderURL.toURL(), pastedToken) == null) {
            return false
        }
        model.token = pastedToken
        val authTask = object : Task.Modal(null, CoderGatewayBundle.message("gateway.connector.view.login.cli.downloader.dialog.title"), false) {
            override fun run(pi: ProgressIndicator) {
                pi.apply {
                    text = "Downloading coder cli..."
                    fraction = 0.1
                }

                val cliManager = CoderCLIManager(model.coderURL.toURL())
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

                val loginOutput = ProcessExecutor().command(cli.toAbsolutePath().toString(), "login", model.coderURL, "--token", coderClient.sessionToken).readOutput(true).execute().outputUTF8()
                logger.info("coder-cli login output: $loginOutput")
                pi.fraction = 0.8
                val sshConfigOutput = ProcessExecutor().command(cli.toAbsolutePath().toString(), "config-ssh").readOutput(true).execute().outputUTF8()
                logger.info("coder-cli config-ssh output: $sshConfigOutput")
                pi.fraction = 1.0
            }
        }
        wizardModel.apply {
            coderURL = model.coderURL
            token = model.token
        }
        ProgressManager.getInstance().run(authTask)
        return true
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

    override fun dispose() {
        cs.cancel()
    }

    companion object {
        val logger = Logger.getLogger(CoderAuthStepView::class.java.simpleName)
    }
}