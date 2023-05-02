@file:Suppress("DialogTitleCapitalization")

package com.coder.gateway

import com.coder.gateway.sdk.humanizeDuration
import com.coder.gateway.sdk.isCancellation
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
            try {
                indicator.text = CoderGatewayBundle.message("gateway.connector.coder.connecting")
                val context = suspendingRetryWithExponentialBackOff(
                    action = { attempt ->
                        logger.info("Connecting... (attempt $attempt")
                        if (attempt > 1) {
                            // indicator.text is the text above the progress bar.
                            indicator.text = CoderGatewayBundle.message("gateway.connector.coder.connecting.retry", attempt)
                        }
                        SshMultistagePanelContext(parameters.toHostDeployInputs())
                    },
                    retryIf = {
                        it is ConnectionException || it is TimeoutException
                                || it is SSHException || it is DeployException
                    },
                    onException = { attempt, nextMs, e ->
                        logger.error("Failed to connect (attempt $attempt; will retry in $nextMs ms)")
                        // indicator.text2 is the text below the progress bar.
                        indicator.text2 =
                            if (isWorkerTimeout(e)) "Failed to upload worker binary...it may have timed out"
                            else e.message ?: CoderGatewayBundle.message("gateway.connector.no-details")
                    },
                    onCountdown = { remainingMs ->
                        indicator.text = CoderGatewayBundle.message("gateway.connector.coder.connecting.failed.retry", humanizeDuration(remainingMs))
                    },
                )
                launch {
                    logger.info("Deploying and starting IDE with $context")
                    // At this point JetBrains takes over with their own UI.
                    @Suppress("UnstableApiUsage") SshDeployFlowUtil.fullDeployCycle(
                        clientLifetime, context, Duration.ofMinutes(10)
                    )
                }
            } catch (e: Exception) {
                if (isCancellation(e)) {
                    logger.info("Connection canceled due to ${e.javaClass}")
                } else {
                    logger.info("Failed to connect (will not retry)", e)
                    // The dialog will close once we return so write the error
                    // out into a new dialog.
                    ApplicationManager.getApplication().invokeAndWait {
                        Messages.showMessageDialog(
                            e.message ?: CoderGatewayBundle.message("gateway.connector.no-details"),
                            CoderGatewayBundle.message("gateway.connector.coder.connection.failed"),
                            Messages.getErrorIcon())
                    }
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
