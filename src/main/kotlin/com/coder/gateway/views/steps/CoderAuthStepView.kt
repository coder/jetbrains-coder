package com.coder.gateway.views.steps

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.models.CoderWorkspacesWizardModel
import com.coder.gateway.models.LoginModel
import com.coder.gateway.sdk.CoderCLIManager
import com.coder.gateway.sdk.CoderRestClientService
import com.coder.gateway.sdk.OS
import com.coder.gateway.sdk.getOS
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.askPassword
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.IconManager
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
import java.net.URL
import java.util.logging.Logger

class CoderAuthStepView : CoderWorkspacesWizardStep, Disposable {
    private val cs = CoroutineScope(Dispatchers.Main)
    private var model = LoginModel()
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
            row {
                label(CoderGatewayBundle.message("gateway.connector.view.login.url.label"))
                textField().resizableColumn().horizontalAlign(HorizontalAlign.FILL).gap(RightGap.SMALL).bindText(model::url)
                cell()
            }

            row(CoderGatewayBundle.message("gateway.connector.view.login.email.label")) {
                textField().resizableColumn().bindText(model::email)
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
            url = wizardModel.loginModel.url
            email = wizardModel.loginModel.email
            password = wizardModel.loginModel.password
        }
        component.apply()
    }

    override suspend fun onNext(wizardModel: CoderWorkspacesWizardModel) {
        val password = askPassword(
            null,
            CoderGatewayBundle.message("gateway.connector.view.login.credentials.dialog.title"),
            CoderGatewayBundle.message("gateway.connector.view.login.password.label"),
            CredentialAttributes("Coder"),
            true
        )
        model.password = password
        val authTask = object : Task.Modal(null, "Authenticate and setup coder", false) {
            override fun run(pi: ProgressIndicator) {

                pi.apply {
                    text = "Authenticating ${model.email} on ${model.url}..."
                    fraction = 0.3
                }

                val url = URL(model.url)
                coderClient.initClientSession(url, model.email, model.password!!)
                wizardModel.apply {
                    loginModel = model.copy()
                }

                pi.apply {
                    text = "Downloading coder cli..."
                    fraction = 0.4
                }

                val cliManager = CoderCLIManager(URL(url.protocol, url.host, url.port, ""))
                val cli = cliManager.download() ?: throw IllegalStateException("Could not download coder binary")
                if (getOS() != OS.WINDOWS) {
                    pi.fraction = 0.5
                    val chmodOutput = ProcessExecutor().command("chmod", "+x", cli.toAbsolutePath().toString()).readOutput(true).execute().outputUTF8()
                    logger.info("chmod +x ${cli.toAbsolutePath()} $chmodOutput")
                }
                pi.apply {
                    text = "Configuring coder cli..."
                    fraction = 0.5
                }

                val loginOutput = ProcessExecutor().command(cli.toAbsolutePath().toString(), "login", url.toString(), "--token", coderClient.sessionToken).readOutput(true).execute().outputUTF8()
                logger.info("coder-cli login output: $loginOutput")
                pi.fraction = 0.6
                val sshConfigOutput = ProcessExecutor().command(cli.toAbsolutePath().toString(), "config-ssh").readOutput(true).execute().outputUTF8()
                logger.info("coder-cli config-ssh output: $sshConfigOutput")
                pi.fraction = 1.0
            }
        }
        ProgressManager.getInstance().run(authTask)
    }

    override fun dispose() {
        cs.cancel()
    }

    companion object {
        val logger = Logger.getLogger(CoderAuthStepView::class.java.simpleName)
    }
}