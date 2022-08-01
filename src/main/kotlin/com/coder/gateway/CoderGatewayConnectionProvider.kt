@file:Suppress("DialogTitleCapitalization")

package com.coder.gateway

import com.coder.gateway.models.RecentWorkspaceConnection
import com.coder.gateway.services.CoderRecentWorkspaceConnectionsService
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.rd.util.launchUnderBackgroundProgress
import com.intellij.remote.AuthType
import com.intellij.remote.RemoteCredentialsHolder
import com.intellij.ssh.config.unified.SshConfig
import com.intellij.ssh.config.unified.SshConfigManager
import com.jetbrains.gateway.api.ConnectionRequestor
import com.jetbrains.gateway.api.GatewayConnectionHandle
import com.jetbrains.gateway.api.GatewayConnectionProvider
import com.jetbrains.gateway.ssh.HighLevelHostAccessor
import com.jetbrains.gateway.ssh.HostDeployInputs
import com.jetbrains.gateway.ssh.IdeInfo
import com.jetbrains.gateway.ssh.IntelliJPlatformProduct
import com.jetbrains.gateway.ssh.SshDeployFlowUtil
import com.jetbrains.gateway.ssh.SshMultistagePanelContext
import com.jetbrains.gateway.ssh.deploy.DeployTargetInfo.DeployWithDownload
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import kotlinx.coroutines.launch
import java.net.URI
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class CoderGatewayConnectionProvider : GatewayConnectionProvider {
    private val recentConnectionsService = service<CoderRecentWorkspaceConnectionsService>()
    private val sshConfigService = service<SshConfigManager>()

    private val connections = mutableSetOf<CoderConnectionMetadata>()
    private val localTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm")

    override suspend fun connect(parameters: Map<String, String>, requestor: ConnectionRequestor): GatewayConnectionHandle? {
        val coderWorkspaceHostname = parameters["coder_workspace_hostname"]
        val projectPath = parameters["project_path"]
        val ideProductCode = parameters["ide_product_code"]!!
        val ideBuildNumber = parameters["ide_build_number"]!!
        val ideDownloadLink = parameters["ide_download_link"]!!
        val webTerminalLink = parameters["web_terminal_link"]!!

        if (coderWorkspaceHostname != null && projectPath != null) {
            val connection = CoderConnectionMetadata(coderWorkspaceHostname)
            if (connection in connections) {
                logger.warn("There is already a connection started on ${connection.workspaceHostname}")
                return null
            }
            val sshConfiguration = SshConfig(true).apply {
                setHost(coderWorkspaceHostname)
                setUsername("coder")
                port = 22
                authType = AuthType.OPEN_SSH
            }

            val clientLifetime = LifetimeDefinition()
            clientLifetime.launchUnderBackgroundProgress("Coder Gateway Deploy", canBeCancelled = true, isIndeterminate = true, project = null) {
                val context = SshMultistagePanelContext(
                    HostDeployInputs.FullySpecified(
                        remoteProjectPath = projectPath,
                        deployTarget = DeployWithDownload(
                            URI(ideDownloadLink),
                            null,
                            IdeInfo(
                                product = IntelliJPlatformProduct.fromProductCode(ideProductCode)!!,
                                buildNumber = ideBuildNumber
                            )
                        ),
                        remoteInfo = HostDeployInputs.WithDeployedWorker(
                            HighLevelHostAccessor.create(
                                RemoteCredentialsHolder().apply {
                                    setHost(coderWorkspaceHostname)
                                    userName = "coder"
                                    port = 22
                                    authType = AuthType.OPEN_SSH
                                },
                                true
                            ),
                            HostDeployInputs.WithHostInfo(sshConfiguration)
                        )
                    )
                )
                launch {
                    @Suppress("UnstableApiUsage") SshDeployFlowUtil.fullDeployCycle(
                        clientLifetime, context, Duration.ofMinutes(10)
                    )
                }
            }

            recentConnectionsService.addRecentConnection(
                RecentWorkspaceConnection(
                    coderWorkspaceHostname, projectPath, localTimeFormatter.format(LocalDateTime.now()), ideProductCode, ideBuildNumber, ideDownloadLink, webTerminalLink
                )
            )

            return object : GatewayConnectionHandle(clientLifetime) {
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
        val logger = Logger.getInstance(CoderGatewayConnectionProvider::class.java.simpleName)
    }
}

internal data class CoderConnectionMetadata(val workspaceHostname: String)