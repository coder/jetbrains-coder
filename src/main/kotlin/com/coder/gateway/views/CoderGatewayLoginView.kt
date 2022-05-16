package com.coder.gateway.views

import com.intellij.ide.IdeBundle
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.components.DialogPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.gateway.api.GatewayUI
import java.awt.Component
import javax.swing.JButton
import javax.swing.JPanel

class CoderGatewayLoginView(hgap: Int, vgap: Int) : BorderLayoutPanel(hgap, vgap) {
    init {
        initView()
    }

    private fun initView() {
        addToCenter(createLoginComponent())
        addToBottom(createBackComponent())
    }

    private fun createLoginComponent(): Component {
        return DialogPanel()
    }

    private fun createBackComponent(): Component {
        return JPanel(VerticalLayout(0)).apply {
            add(SeparatorComponent(0, 0, WelcomeScreenUIManager.getSeparatorColor(), null))
            add(BorderLayoutPanel().apply {
                border = JBUI.Borders.empty(6, 24, 6, 0)
                addToLeft(JButton(IdeBundle.message("button.back")).apply {
                    border = JBUI.Borders.empty(3, 3, 3, 3)
                    addActionListener {
                        GatewayUI.Companion.getInstance().reset()
                    }
                })
            })
        }
    }


}