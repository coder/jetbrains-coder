package com.coder.gateway.views

import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.jetbrains.gateway.api.GatewayConnectorView
import com.jetbrains.gateway.api.GatewayUI
import javax.swing.JComponent

class CoderGatewayConnectorWizardWrapperView(private val recentWorkspacesReset: () -> Unit = { GatewayUI.Companion.getInstance().reset() }) : GatewayConnectorView {
    override val component: JComponent
        get() = Wrapper(CoderGatewayConnectorWizardView(recentWorkspacesReset)).apply { border = JBUI.Borders.empty() }
}