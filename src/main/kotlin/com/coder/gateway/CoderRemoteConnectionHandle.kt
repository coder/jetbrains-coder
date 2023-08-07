@file:Suppress("DialogTitleCapitalization")

package com.coder.gateway

import com.coder.gateway.models.TokenSource
import com.coder.gateway.sdk.CoderCLIManager
import com.coder.gateway.sdk.humanizeDuration
import com.coder.gateway.sdk.isCancellation
import com.coder.gateway.sdk.isWorkerTimeout
import com.coder.gateway.sdk.suspendingRetryWithExponentialBackOff
import com.coder.gateway.sdk.toURL
import com.coder.gateway.sdk.withPath
import com.coder.gateway.services.CoderRecentWorkspaceConnectionsService
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.rd.util.launchUnderBackgroundProgress
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.AppIcon
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.applyIf
import com.intellij.util.ui.UIUtil
import com.jetbrains.gateway.ssh.SshDeployFlowUtil
import com.jetbrains.gateway.ssh.SshMultistagePanelContext
import com.jetbrains.gateway.ssh.deploy.DeployException
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import kotlinx.coroutines.launch
import net.schmizz.sshj.common.SSHException
import net.schmizz.sshj.connection.ConnectionException
import java.awt.Dimension
import java.net.URL
import java.time.Duration
import java.util.concurrent.TimeoutException

// CoderRemoteConnection uses the provided workspace SSH parameters to launch an
// IDE against the workspace.  If successful the connection is added to recent
// connections.
class CoderRemoteConnectionHandle {
    private val recentConnectionsService = service<CoderRecentWorkspaceConnectionsService>()

    suspend fun connect(parameters: Map<String, String>) {
        logger.debug("Creating connection handle", parameters)
        val clientLifetime = LifetimeDefinition()
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
                recentConnectionsService.addRecentConnection(parameters.toRecentWorkspaceConnection())
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
    }

    companion object {
        val logger = Logger.getInstance(CoderRemoteConnectionHandle::class.java.simpleName)

        /**
         * Generic function to ask for input.
         */
        @JvmStatic
        fun ask(comment: String, isError: Boolean, link: Pair<String, String>?, default: String?): String? {
            var inputFromUser: String? = null
            ApplicationManager.getApplication().invokeAndWait({
                lateinit var inputTextField: JBTextField
                val panel = panel {
                    row {
                        if (link != null) browserLink(link.first, link.second)
                        inputTextField = textField()
                            .applyToComponent {
                                text = default ?: ""
                                minimumSize = Dimension(520, -1)
                            }.component
                    }.layout(RowLayout.PARENT_GRID)
                    row {
                        cell() // To align with the text box.
                        cell(
                            ComponentPanelBuilder.createCommentComponent(comment, false, -1, true)
                                .applyIf(isError) {
                                    apply {
                                        foreground = UIUtil.getErrorForeground()
                                    }
                                }
                        )
                    }.layout(RowLayout.PARENT_GRID)
                }
                AppIcon.getInstance().requestAttention(null, true)
                if (!dialog(
                        CoderGatewayBundle.message("gateway.connector.view.login.token.dialog"),
                        panel = panel,
                        focusedComponent = inputTextField
                    ).showAndGet()
                ) {
                    return@invokeAndWait
                }
                inputFromUser = inputTextField.text
            }, ModalityState.any())
            return inputFromUser
        }

        /**
         * Open a dialog for providing the token.  Show any existing token so the
         * user can validate it if a previous connection failed.  If we are not
         * retrying and the user has not checked the existing token box then open a
         * browser to the auth page.  If the user has checked the existing token box
         * then populate the dialog with the token on disk (this will overwrite any
         * other existing token) unless this is a retry to avoid clobbering the
         * token that just failed.  Return the token submitted by the user.
         */
        @JvmStatic
        fun askToken(
            url: URL,
            token: Pair<String, TokenSource>?,
            isRetry: Boolean,
            useExisting: Boolean,
        ): Pair<String, TokenSource>? {
            var (existingToken, tokenSource) = token ?: Pair("", TokenSource.USER)
            val getTokenUrl = url.withPath("/login?redirect=%2Fcli-auth")
            if (!isRetry && !useExisting) {
                BrowserUtil.browse(getTokenUrl)
            } else if (!isRetry && useExisting) {
                val (u, t) = CoderCLIManager.readConfig()
                if (url == u?.toURL() && !t.isNullOrBlank() && t != existingToken) {
                    logger.info("Injecting token from CLI config")
                    tokenSource = TokenSource.CONFIG
                    existingToken = t
                }
            }
            val tokenFromUser = ask(
                CoderGatewayBundle.message(
                    if (isRetry) "gateway.connector.view.workspaces.token.rejected"
                    else if (tokenSource == TokenSource.CONFIG) "gateway.connector.view.workspaces.token.injected"
                    else if (existingToken.isNotBlank()) "gateway.connector.view.workspaces.token.comment"
                    else "gateway.connector.view.workspaces.token.none"
                ),
                isRetry,
                Pair(
                    CoderGatewayBundle.message("gateway.connector.view.login.token.label"),
                    getTokenUrl.toString()
                ),
                existingToken,
            )
            if (tokenFromUser.isNullOrBlank()) {
                return null
            }
            if (tokenFromUser != existingToken) {
                tokenSource = TokenSource.USER
            }
            return Pair(tokenFromUser, tokenSource)
        }
    }
}
