@file:Suppress("DialogTitleCapitalization")

package com.coder.gateway

import com.coder.gateway.services.CoderRecentWorkspaceConnectionsService
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.rd.util.launchUnderBackgroundProgress
import com.jetbrains.gateway.api.ConnectionRequestor
import com.jetbrains.gateway.api.GatewayConnectionHandle
import com.jetbrains.gateway.api.GatewayConnectionProvider
import com.jetbrains.gateway.api.GatewayUI
import com.jetbrains.gateway.ssh.SshDeployFlowUtil
import com.jetbrains.gateway.ssh.SshMultistagePanelContext
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import kotlinx.coroutines.launch
import java.time.Duration

class CoderGatewayConnectionProvider : GatewayConnectionProvider {
    private val recentConnectionsService = service<CoderRecentWorkspaceConnectionsService>()

    override suspend fun connect(parameters: Map<String, String>, requestor: ConnectionRequestor): GatewayConnectionHandle? {
        val clientLifetime = LifetimeDefinition()
        clientLifetime.launchUnderBackgroundProgress(CoderGatewayBundle.message("gateway.connector.coder.connection.provider.title"), canBeCancelled = true, isIndeterminate = true, project = null) {
            val context = SshMultistagePanelContext(parameters.toHostDeployInputs())
            logger.info("Deploying and starting IDE with $context")
            launch {
                @Suppress("UnstableApiUsage") SshDeployFlowUtil.fullDeployCycle(
                    clientLifetime, context, Duration.ofMinutes(10)
                )
            }
        }

        recentConnectionsService.addRecentConnection(parameters.toRecentWorkspaceConnection())
        GatewayUI.getInstance().reset()
        return null
    }

    override fun isApplicable(parameters: Map<String, String>): Boolean {
        return parameters.areCoderType()
    }

    companion object {
        val logger = Logger.getInstance(CoderGatewayConnectionProvider::class.java.simpleName)
    }
}