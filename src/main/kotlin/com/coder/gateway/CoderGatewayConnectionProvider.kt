package com.coder.gateway

import com.coder.gateway.views.CoderGatewayConnectionComponent
import com.jetbrains.gateway.api.ConnectionRequestor
import com.jetbrains.gateway.api.GatewayConnectionHandle
import com.jetbrains.gateway.api.GatewayConnectionProvider
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import java.util.logging.Logger
import javax.swing.JComponent

class CoderGatewayConnectionProvider : GatewayConnectionProvider {
    private val connections = mutableSetOf<CoderConnectionMetadata>()
    override suspend fun connect(parameters: Map<String, String>, requestor: ConnectionRequestor): GatewayConnectionHandle? {
        val coderUrl = parameters["coder_url"]
        val workspaceId = parameters["workspace_id"]

        if (coderUrl != null && workspaceId != null) {
            val connection = CoderConnectionMetadata(coderUrl, workspaceId)
            if (connection in connections) {
                logger.warning("There is already a connection started on ${connection.url} using the workspace ${connection.workspaceId}")
                return null
            }
            val clientLifetime = LifetimeDefinition()
            return object : GatewayConnectionHandle(clientLifetime) {
                override fun createComponent(): JComponent {
                    return CoderGatewayConnectionComponent(clientLifetime, coderUrl, workspaceId)
                }

                override fun getTitle(): String {
                    return "Connection to Coder Workspaces"
                }

                override fun hideToTrayOnStart(): Boolean {
                    return false
                }
            }
        }
        return null
    }

    override fun isApplicable(parameters: Map<String, String>): Boolean {
        return parameters["type"] == "coder"
    }

    companion object {
        val logger = Logger.getLogger(CoderGatewayConnectionProvider::class.java.simpleName)
    }
}

internal data class CoderConnectionMetadata(val url: String, val workspaceId: String)
