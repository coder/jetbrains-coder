package com.coder.gateway.views.steps

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.models.CoderWorkspacesWizardModel
import com.coder.gateway.models.LoginModel
import com.coder.gateway.models.UriScheme
import com.coder.gateway.sdk.CoderRestClientService
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.askPassword
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.IconManager
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toNullableProperty
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.ui.JBFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext

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
                label(CoderGatewayBundle.message("gateway.connector.view.login.scheme.label"))
                comboBox(UriScheme.values().toList()).bindItem(model::uriScheme.toNullableProperty())
                label(CoderGatewayBundle.message("gateway.connector.view.login.host.label"))
                textField().resizableColumn().horizontalAlign(HorizontalAlign.FILL).gap(RightGap.SMALL).bindText(model::host)
                label(CoderGatewayBundle.message("gateway.connector.view.login.port.label"))
                intTextField(0..65536).bindIntText(model::port)
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
            uriScheme = wizardModel.loginModel.uriScheme
            host = wizardModel.loginModel.host
            port = wizardModel.loginModel.port
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
            false
        )

        model.password = password
        withContext(Dispatchers.IO) {
            coderClient.initClientSession(model.uriScheme, model.host, model.port, model.email, model.password!!)
        }
        wizardModel.apply {
            loginModel = model.copy()
        }
    }


    override fun dispose() {
        cs.cancel()
    }
}