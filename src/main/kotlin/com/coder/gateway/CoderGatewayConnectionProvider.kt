package com.coder.gateway

import com.coder.gateway.views.CoderGatewayConnectionComponent
import com.intellij.openapi.rd.util.launchUnderBackgroundProgress
import com.intellij.remote.AuthType
import com.intellij.remote.RemoteCredentialsHolder
import com.intellij.ssh.config.unified.SshConfig
import com.jetbrains.gateway.api.ConnectionRequestor
import com.jetbrains.gateway.api.GatewayConnectionHandle
import com.jetbrains.gateway.api.GatewayConnectionProvider
import com.jetbrains.gateway.ssh.IdeInfo
import com.jetbrains.gateway.ssh.IntelliJPlatformProduct
import com.jetbrains.gateway.ssh.SshCommandsExecutor
import com.jetbrains.gateway.ssh.SshDeployFlowUtil
import com.jetbrains.gateway.ssh.SshDownloadMethod
import com.jetbrains.gateway.ssh.SshMultistagePanelContext
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import kotlinx.coroutines.async
import java.time.Duration
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

            clientLifetime.launchUnderBackgroundProgress("Coder Gateway Deploy", true, true, null) {
                val context = SshMultistagePanelContext().apply {
                    deploy = true
                    sshConfig = SshConfig(true).apply {
                        setHost("coder.${workspaceName}")
                        setUsername(user)
                        authType = AuthType.OPEN_SSH
                    }
                    remoteProjectPath = projectPath
                    remoteCommandsExecutor = SshCommandsExecutor.Companion.create(credentials)
                    downloadMethod = SshDownloadMethod.CustomizedLink
                    customDownloadLink = "https://download.jetbrains.com/idea/ideaIU-2021.3.3.tar.gz"
                    ide = IdeInfo(
                        IntelliJPlatformProduct.IDEA,
                        buildNumber = "213.7172.25"
                    )
                }
                val deployPair = async {
                    SshDeployFlowUtil.fullDeployCycle(
                        clientLifetime,
                        context,
                        Duration.ofMinutes(10)
                    )
                }.await()

                logger.info(">>>$deployPair")
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