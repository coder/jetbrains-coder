package com.coder.gateway.views

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.models.LoginModel
import com.coder.gateway.sdk.CoderClientService
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.askPassword
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.IconManager
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.gateway.api.GatewayUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Component
import javax.swing.JButton
import javax.swing.JPanel

class CoderGatewayLoginView : BorderLayoutPanel(), Disposable {
    private val logger = Logger.getInstance(CoderClientService::class.java)
    private val cs = CoroutineScope(Dispatchers.Main)
    private val model = LoginModel()
    private val coderClient: CoderClientService = ApplicationManager.getApplication().getService(CoderClientService::class.java)

    init {
        initView()
    }

    private fun initView() {
        background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
        addToCenter(createLoginComponent())
        addToBottom(createBackComponent())
    }

    private fun createLoginComponent(): Component {
        return panel {
            indent {
                row {
                    label(CoderGatewayBundle.message("gateway.connector.view.login.header.text")).applyToComponent {
                        font = JBFont.h3().asBold()
                        icon = IconManager.getInstance().getIcon("coder_logo_16.svg", CoderGatewayLoginView::class.java)
                    }
                }.topGap(TopGap.SMALL).bottomGap(BottomGap.MEDIUM)
                row {
                    cell(ComponentPanelBuilder.createCommentComponent(CoderGatewayBundle.message("gateway.connector.view.login.comment.text"), false, -1, true))
                }
                row {
                    browserLink(CoderGatewayBundle.message("gateway.connector.view.login.documentation.action"), "https://coder.com/docs/coder/latest/workspaces")
                }.bottomGap(BottomGap.MEDIUM)
                row(CoderGatewayBundle.message("gateway.connector.view.login.host.label")) {
                    textField().resizableColumn().horizontalAlign(HorizontalAlign.FILL).gap(RightGap.SMALL).bindText(model::host)
                    intTextField(0..65536).bindIntText(model::port).label(CoderGatewayBundle.message("gateway.connector.view.login.port.label"))
                    button(CoderGatewayBundle.message("gateway.connector.view.login.connect.action")) {
                        model.password = askPassword(
                            null,
                            CoderGatewayBundle.message("gateway.connector.view.login.credentials.dialog.title"),
                            CoderGatewayBundle.message("gateway.connector.view.login.password.label"),
                            CredentialAttributes("Coder"),
                            false
                        )
                        cs.launch {
                            withContext(Dispatchers.IO) {
                                coderClient.initClientSession(model.host, model.port, model.email, model.password!!)
                            }
                            logger.info("Session token:${coderClient.sessionToken}")
                        }

                    }.applyToComponent {
                        background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
                        border = JBUI.Borders.empty(3, 3, 3, 3)
                    }
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
    }

    private fun createBackComponent(): Component {
        return JPanel(VerticalLayout(0)).apply {
            add(SeparatorComponent(0, 0, WelcomeScreenUIManager.getSeparatorColor(), null))
            add(BorderLayoutPanel().apply {
                border = JBUI.Borders.empty(6, 24, 6, 0)
                addToLeft(JButton(IdeBundle.message("button.back")).apply {
                    border = JBUI.Borders.empty(3, 3, 3, 3)
                    background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
                    addActionListener {
                        GatewayUI.Companion.getInstance().reset()
                    }
                })
                background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
            })
            background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
        }
    }

    override fun dispose() {
        cs.cancel()
    }
}

