@file:Suppress("DialogTitleCapitalization")

package com.coder.gateway

import com.coder.gateway.sdk.humanizeDuration
import com.coder.gateway.sdk.isWorkerTimeout
import com.coder.gateway.sdk.suspendingRetryWithExponentialBackOff
import com.coder.gateway.services.CoderRecentWorkspaceConnectionsService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.rd.util.launchUnderBackgroundProgress
import com.intellij.openapi.ui.Messages
import com.jetbrains.gateway.api.ConnectionRequestor
import com.jetbrains.gateway.api.GatewayConnectionHandle
import com.jetbrains.gateway.api.GatewayConnectionProvider
import com.jetbrains.gateway.api.GatewayUI
import com.jetbrains.gateway.ssh.SshDeployFlowUtil
import com.jetbrains.gateway.ssh.SshMultistagePanelContext
import com.jetbrains.gateway.ssh.deploy.DeployException
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import kotlinx.coroutines.launch
import net.schmizz.sshj.common.SSHException
import net.schmizz.sshj.connection.ConnectionException
import java.time.Duration
import java.util.concurrent.TimeoutException

class CoderGatewayConnectionProvider : GatewayConnectionProvider {
    private val recentConnectionsService = service<CoderRecentWorkspaceConnectionsService>()

    override suspend fun connect(parameters: Map<String, String>, requestor: ConnectionRequestor): GatewayConnectionHandle? {
        val clientLifetime = LifetimeDefinition()
        // TODO: If this fails determine if it is an auth error and if so prompt
        // for a new token, configure the CLI, then try again.
        clientLifetime.launchUnderBackgroundProgress(CoderGatewayBundle.message("gateway.connector.coder.connection.provider.title"), canBeCancelled = true, isIndeterminate = true, project = null) {
            val context = suspendingRetryWithExponentialBackOff(
                label = "connect",
                logger = logger,
                action = { attempt ->
                    logger.info("Deploying (attempt $attempt)...")
                    indicator.text =
                        if (attempt > 1) CoderGatewayBundle.message("gateway.connector.coder.connection.retry.text", attempt)
                        else CoderGatewayBundle.message("gateway.connector.coder.connection.loading.text")
                    SshMultistagePanelContext(parameters.toHostDeployInputs())
                },
                predicate = { e ->
                    e is ConnectionException || e is TimeoutException
                            || e is SSHException || e is DeployException
                },
                update = { _, e, remainingMs ->
                    if (remainingMs != null) {
                        indicator.text2 =
                            if (isWorkerTimeout(e)) "Failed to upload worker binary...it may have timed out"
                            else e.message ?: CoderGatewayBundle.message("gateway.connector.no-details")
                        indicator.text = CoderGatewayBundle.message("gateway.connector.coder.connection.retry-error.text", humanizeDuration(remainingMs))
                    } else {
                        ApplicationManager.getApplication().invokeAndWait {
                            Messages.showMessageDialog(
                                e.message ?: CoderGatewayBundle.message("gateway.connector.no-details"),
                                CoderGatewayBundle.message("gateway.connector.coder.connection.error.text"),
                                Messages.getErrorIcon())
                        }
                    }
                },
            )
            if (context != null) {
                launch {
                    logger.info("Deploying and starting IDE with $context")
                    // At this point JetBrains takes over with their own UI.
                    @Suppress("UnstableApiUsage") SshDeployFlowUtil.fullDeployCycle(
                        clientLifetime, context, Duration.ofMinutes(10)
                    )
                }
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
