package com.coder.gateway.views

import com.coder.gateway.CoderRemoteConnectionHandle
import com.coder.gateway.views.steps.CoderWizardView
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.jetbrains.gateway.api.GatewayConnectorView
import javax.swing.JComponent

class CoderGatewayConnectorWizardWrapperView : GatewayConnectorView {
    override val component: JComponent
        get() {
            return Wrapper(CoderWizardView { params ->
                CoderRemoteConnectionHandle().connect { params }
            }).apply { border = JBUI.Borders.empty() }
        }
}
