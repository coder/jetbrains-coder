package com.coder.gateway

import com.coder.gateway.help.ABOUT_HELP_TOPIC
import com.coder.gateway.icons.CoderIcons
import com.coder.gateway.views.CoderGatewayConnectorWizardWrapperView
import com.coder.gateway.views.CoderGatewayRecentWorkspaceConnectionsView
import com.intellij.openapi.help.HelpManager
import com.jetbrains.gateway.api.GatewayConnector
import com.jetbrains.gateway.api.GatewayConnectorDocumentation
import com.jetbrains.gateway.api.GatewayConnectorView
import com.jetbrains.gateway.api.GatewayRecentConnections
import com.jetbrains.rd.util.lifetime.Lifetime
import java.awt.Component
import javax.swing.Icon

class CoderGatewayMainView : GatewayConnector {
    override fun getConnectorId() = CoderGatewayConstants.GATEWAY_CONNECTOR_ID

    override val icon: Icon
        get() = CoderIcons.LOGO

    override fun createView(lifetime: Lifetime): GatewayConnectorView {
        return CoderGatewayConnectorWizardWrapperView()
    }

    override fun getActionText(): String {
        return CoderGatewayBundle.message("gateway.connector.action.text")
    }

    override fun getDescription(): String {
        return CoderGatewayBundle.message("gateway.connector.description")
    }

    override fun getDocumentationAction(): GatewayConnectorDocumentation {
        return GatewayConnectorDocumentation(true) {
            HelpManager.getInstance().invokeHelp(ABOUT_HELP_TOPIC)
        }
    }

    override fun getRecentConnections(setContentCallback: (Component) -> Unit): GatewayRecentConnections {
        return CoderGatewayRecentWorkspaceConnectionsView(setContentCallback)
    }

    override fun getTitle(): String {
        return CoderGatewayBundle.message("gateway.connector.title")
    }

    override fun isAvailable(): Boolean {
        return true
    }
}
