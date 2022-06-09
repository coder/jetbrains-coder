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
        val coderWorkspaceHostname = parameters["coder_workspace_hostname"]
        val projectPath = parameters["project_path"]
        val ideProductCode = parameters["ide_product_code"]!!
        val ideBuildNumber = parameters["ide_build_number"]!!
        val ideDownloadLink = parameters["ide_download_link"]

        if (coderWorkspaceHostname != null) {
            val connection = CoderConnectionMetadata(coderWorkspaceHostname)
            if (connection in connections) {
                logger.warning("There is already a connection started on ${connection.workspaceHostname}")
                return null
            }
            val clientLifetime = LifetimeDefinition()
            val credentials = RemoteCredentialsHolder().apply {
                setHost(coderWorkspaceHostname)
                userName = "coder"
                authType = AuthType.OPEN_SSH
            }

            clientLifetime.launchUnderBackgroundProgress("Coder Gateway Deploy", true, true, null) {
                val context = SshMultistagePanelContext().apply {
                    deploy = true
                    sshConfig = SshConfig(true).apply {
                        setHost(coderWorkspaceHostname)
                        setUsername("coder")
                        authType = AuthType.OPEN_SSH
                    }
                    remoteProjectPath = projectPath
                    remoteCommandsExecutor = SshCommandsExecutor.Companion.create(credentials)
                    downloadMethod = SshDownloadMethod.CustomizedLink
                    customDownloadLink = ideDownloadLink
                    ide = IdeInfo(
                        product = IntelliJPlatformProduct.fromProductCode(ideProductCode)!!,
                        buildNumber = ideBuildNumber
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
                    return CoderGatewayConnectionComponent(clientLifetime, coderWorkspaceHostname)
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

internal data class CoderConnectionMetadata(val workspaceHostname: String)