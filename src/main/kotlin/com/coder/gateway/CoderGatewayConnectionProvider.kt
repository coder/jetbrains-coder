package com.coder.gateway

import com.coder.gateway.views.CoderGatewayConnectionComponent
import com.intellij.remote.AuthType
import com.intellij.remote.RemoteCredentialsHolder
import com.intellij.ssh.config.unified.SshConfig
import com.jetbrains.gateway.api.ConnectionRequestor
import com.jetbrains.gateway.api.GatewayConnectionHandle
import com.jetbrains.gateway.api.GatewayConnectionProvider
import com.jetbrains.gateway.ssh.IdeInfo
import com.jetbrains.gateway.ssh.IntelliJPlatformProduct
import com.jetbrains.gateway.ssh.SshCommandsExecutor
import com.jetbrains.gateway.ssh.SshDownloadMethod
import com.jetbrains.gateway.ssh.SshMultistagePanelContext
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.logging.Logger
import javax.swing.JComponent

class CoderGatewayConnectionProvider : GatewayConnectionProvider {
    private val connections = mutableSetOf<CoderConnectionMetadata>()
    override suspend fun connect(parameters: Map<String, String>, requestor: ConnectionRequestor): GatewayConnectionHandle? {
        val coderUrl = parameters["coder_url"]
        val workspaceName = parameters["workspace_name"]
        val user = parameters["username"]
        val projectPath = parameters["project_path"]

        if (coderUrl != null && workspaceName != null) {
            val connection = CoderConnectionMetadata(coderUrl, workspaceName)
            if (connection in connections) {
                logger.warning("There is already a connection started on ${connection.url} using the workspace ${connection.workspaceId}")
                return null
            }
            val clientLifetime = LifetimeDefinition()
            val credentials = RemoteCredentialsHolder()
            credentials.apply {
                setHost("coder.${workspaceName}")
                userName = "coder"
                authType = AuthType.OPEN_SSH
            }
            val context = SshMultistagePanelContext().apply {
                deploy = true
                sshConfig = SshConfig(true).apply {
                    setHost("coder.${workspaceName}")
                    setUsername(user)
                    authType = AuthType.OPEN_SSH
                }
                downloadMethod = SshDownloadMethod.SftpUpload
                ide = IdeInfo(
                    IntelliJPlatformProduct.IDEA,
                    buildNumber = "221.5787.30"
                )
            }

//            GlobalScope.launch {
//                val deployPair = withContext(Dispatchers.IO) {
//                    SshDeployFlowUtil.fullDeployCycle(
//                        clientLifetime,
//                        context,
//                        Duration.ofMinutes(10)
//                    )
//                }
//
//
//                println(deployPair)
//            }

            GlobalScope.launch {
                val cmdExecutor = SshCommandsExecutor.Companion.create(credentials)
                cmdExecutor.getInstalledIDEs()
            }

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