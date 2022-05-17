package com.coder.gateway.views

import com.coder.gateway.CoderGatewayBundle
import com.intellij.ide.IdeBundle
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.IconManager
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.gateway.api.GatewayUI
import java.awt.Component
import javax.swing.JButton
import javax.swing.JPanel

class CoderGatewayLoginView : BorderLayoutPanel() {
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
                row {
                    label(CoderGatewayBundle.message("gateway.connector.view.login.url.label"))
                    textField().resizableColumn().horizontalAlign(HorizontalAlign.FILL).applyToComponent {
                        text = "https://dev.coder.com"
                    }
                    button(CoderGatewayBundle.message("gateway.connector.view.login.connect.action"), {}).applyToComponent {
                        background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
                        border = JBUI.Borders.empty(3, 3, 3, 3)
                    }
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


}