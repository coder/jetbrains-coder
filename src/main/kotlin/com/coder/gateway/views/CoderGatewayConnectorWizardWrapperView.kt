package com.coder.gateway.views

import com.coder.gateway.CoderRemoteConnectionHandle
import com.coder.gateway.views.steps.CoderWorkspaceProjectIDEStepView
import com.coder.gateway.views.steps.CoderWorkspacesStepView
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.jetbrains.gateway.api.GatewayConnectorView
import com.jetbrains.gateway.api.GatewayUI
import javax.swing.JComponent

class CoderGatewayConnectorWizardWrapperView : GatewayConnectorView {
    override val component: JComponent
        get() {
            val step1 = CoderWorkspacesStepView()
            val step2 = CoderWorkspaceProjectIDEStepView()
            val wrapper = Wrapper(step1).apply { border = JBUI.Borders.empty() }
            step1.init()

            step1.onPrevious = {
                GatewayUI.getInstance().reset()
                step1.dispose()
                step2.dispose()
            }
            step1.onNext = {
                step1.stop()
                step2.init(it)
                wrapper.setContent(step2)
            }

            step2.onPrevious = {
                step2.stop()
                step1.init()
                wrapper.setContent(step1)
            }
            step2.onNext = { params ->
                GatewayUI.getInstance().reset()
                step1.dispose()
                step2.dispose()
                CoderRemoteConnectionHandle().connect { params }
            }

            return wrapper
        }
}
