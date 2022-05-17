package com.coder.gateway.views

import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.jetbrains.gateway.api.GatewayConnectorView
import javax.swing.JComponent

class CoderGatewayConnectorView : GatewayConnectorView {
    override val component: JComponent
        get() = Wrapper(CoderGatewayLoginView()).apply { border = JBUI.Borders.empty() }
}