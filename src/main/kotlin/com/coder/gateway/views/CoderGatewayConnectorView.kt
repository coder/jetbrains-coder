package com.coder.gateway.views

import com.intellij.ui.components.panels.Wrapper
import com.jetbrains.gateway.api.GatewayConnectorView
import javax.swing.JComponent

class CoderGatewayConnectorView : GatewayConnectorView {
    override val component: JComponent
        get() = Wrapper(CoderGatewayLoginView(0, 0))
}