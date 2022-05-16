package com.coder.gateway

import com.intellij.ui.IconManager
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.BrowserLink
import com.jetbrains.gateway.api.GatewayConnector
import com.jetbrains.gateway.api.GatewayConnectorView
import com.jetbrains.rd.util.lifetime.Lifetime
import javax.swing.Icon
import javax.swing.JComponent

class CoderGatewayConnector : GatewayConnector {
    override val icon: Icon
        get() = IconManager.getInstance().getIcon("coder_logo.svg", this::class.java)

    override fun createView(lifetime: Lifetime): GatewayConnectorView {
        TODO("Not yet implemented")
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