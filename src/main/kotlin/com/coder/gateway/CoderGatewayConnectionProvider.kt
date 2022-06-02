package com.coder.gateway

import com.coder.gateway.views.CoderGatewayConnectionComponent
import com.intellij.remote.RemoteCredentialsHolder
import com.jetbrains.gateway.api.ConnectionRequestor
import com.jetbrains.gateway.api.GatewayConnectionHandle
import com.jetbrains.gateway.api.GatewayConnectionProvider
import com.jetbrains.gateway.ssh.ClientOverSshTunnelConnector
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import java.net.URI
import java.net.URL
import java.util.logging.Logger
import javax.swing.JComponent

class CoderGatewayConnectionProvider : GatewayConnectionProvider {
    private val connections = mutableSetOf<CoderConnectionMetadata>()
    override suspend fun connect(parameters: Map<String, String>, requestor: ConnectionRequestor): GatewayConnectionHandle? {
        val coderUrl = parameters["coder_url"]
        val workspaceName = parameters["workspace_name"]
        val user = parameters["username"]
        val privateSSHKey = parameters["private_ssh_key"]
        val projectPath = parameters["project_path"]

        if (coderUrl != null && workspaceName != null) {
            val connection = CoderConnectionMetadata(coderUrl, workspaceName)
            if (connection in connections) {
                logger.warning("There is already a connection started on ${connection.url} using the workspace ${connection.workspaceId}")
                return null
            }
            val url = URL(coderUrl)
            val clientLifetime = LifetimeDefinition()
            val credentials = RemoteCredentialsHolder()
            credentials.apply {
                setHost("coder.${workspaceName}")
                userName = user
                setPrivateKeyFile(privateSSHKey)
            }
            var tcpJoinLink = "jetbrains-gateway://connect#projectPath=${projectPath}&host=${url.host}&port=22&user=${user}&type=ssh&deploy=true&buildNumber=221.5591.52&productCode=IU"
            ClientOverSshTunnelConnector(clientLifetime, credentials, URI(tcpJoinLink)).connect()
            return object : GatewayConnectionHandle(clientLifetime) {
                override fun createComponent(): JComponent {
                    return CoderGatewayConnectionComponent(clientLifetime, coderUrl, workspaceName)
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
