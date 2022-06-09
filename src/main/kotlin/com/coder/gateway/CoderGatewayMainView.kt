package com.coder.gateway

import com.coder.gateway.icons.CoderIcons
import com.coder.gateway.views.CoderGatewayConnectorWizardWrapperView
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.BrowserLink
import com.jetbrains.gateway.api.GatewayConnector
import com.jetbrains.gateway.api.GatewayConnectorView
import com.jetbrains.rd.util.lifetime.Lifetime
import javax.swing.Icon
import javax.swing.JComponent

class CoderGatewayMainView : GatewayConnector {
    override val icon: Icon
        get() = CoderIcons.LOGO

    override fun createView(lifetime: Lifetime): GatewayConnectorView {
        return CoderGatewayConnectorWizardWrapperView()
    }

    override fun getActionText(): String {
        return CoderGatewayBundle.message("gateway.connector.action.text")
    }

    override fun getDescription(): String? {
        return CoderGatewayBundle.message("gateway.connector.description")
    }

    override fun getDocumentationLink(): ActionLink? {
        return BrowserLink(null, "Learn more about Coder Workspaces", null, "https://coder.com/docs/coder/latest/workspaces")
    }

    override fun getTitle(): String {
        return CoderGatewayBundle.message("gateway.connector.title")
    }

    override fun getTitleAdornment(): JComponent? {
        return null
    }

    override fun isAvailable(): Boolean {
        return true
    }
}