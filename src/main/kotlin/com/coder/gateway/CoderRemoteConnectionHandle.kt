@file:Suppress("DialogTitleCapitalization")

package com.coder.gateway

import com.coder.gateway.models.WorkspaceProjectIDE
import com.coder.gateway.services.CoderRecentWorkspaceConnectionsService
import com.coder.gateway.services.CoderSettingsService
import com.coder.gateway.settings.CoderSettings
import com.coder.gateway.settings.Source
import com.coder.gateway.util.humanizeDuration
import com.coder.gateway.util.isCancellation
import com.coder.gateway.util.isWorkerTimeout
import com.coder.gateway.util.suspendingRetryWithExponentialBackOff
import com.coder.gateway.util.withPath
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
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
import net.schmizz.sshj.common.SSHException
import net.schmizz.sshj.connection.ConnectionException
import java.awt.Dimension
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLHandshakeException

// CoderRemoteConnection uses the provided workspace SSH parameters to launch an
// IDE against the workspace.  If successful the connection is added to recent
// connections.
class CoderRemoteConnectionHandle {
    private val recentConnectionsService = service<CoderRecentWorkspaceConnectionsService>()
    private val settings = service<CoderSettingsService>()

    fun connect(getParameters: (indicator: ProgressIndicator) -> WorkspaceProjectIDE) {
        val clientLifetime = LifetimeDefinition()
        clientLifetime.launchUnderBackgroundProgress(CoderGatewayBundle.message("gateway.connector.coder.connection.provider.title")) {
            try {
                val parameters = getParameters(indicator)
                logger.debug("Creating connection handle", parameters)
                indicator.text = CoderGatewayBundle.message("gateway.connector.coder.connecting")
                val context = suspendingRetryWithExponentialBackOff(
                    action = { attempt ->
                        logger.info("Connecting... (attempt $attempt)")
                        if (attempt > 1) {
                            // indicator.text is the text above the progress bar.
                            indicator.text = CoderGatewayBundle.message("gateway.connector.coder.connecting.retry", attempt)
                        }
                        val deployInputs = parameters.deploy(
                            indicator,
                            Duration.ofMinutes(10),
                            settings.setupCommand,
                            settings.ignoreSetupFailure)
                        SshMultistagePanelContext(deployInputs)
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
                            else e.message ?: e.javaClass.simpleName
                    },
                    onCountdown = { remainingMs ->
                        indicator.text = CoderGatewayBundle.message("gateway.connector.coder.connecting.failed.retry", humanizeDuration(remainingMs))
                    },
                )
                logger.info("Starting and connecting to ${parameters.ideName} with $context")
                // At this point JetBrains takes over with their own UI.
                @Suppress("UnstableApiUsage") SshDeployFlowUtil.fullDeployCycle(
                    clientLifetime, context, Duration.ofMinutes(10)
                )
                logger.info("Adding ${parameters.ideName} for ${parameters.hostname}:${parameters.projectPath} to recent connections")
                recentConnectionsService.addRecentConnection(parameters.toRecentWorkspaceConnection())
            } catch (e: Exception) {
                if (isCancellation(e)) {
                    logger.info("Connection canceled due to ${e.javaClass.simpleName}")
                } else {
                    logger.error("Failed to connect (will not retry)", e)
                    // The dialog will close once we return so write the error
                    // out into a new dialog.
                    ApplicationManager.getApplication().invokeAndWait {
                        Messages.showMessageDialog(
                            e.message ?: e.javaClass.simpleName ?: "Aborted",
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
         * Generic function to ask for consent.
         */
        fun confirm(title: String, comment: String, details: String): Boolean {
            var inputFromUser = false
            ApplicationManager.getApplication().invokeAndWait({
                val panel = panel {
                    row {
                        label(comment)
                    }
                    row {
                        label(details)
                    }
                }
                AppIcon.getInstance().requestAttention(null, true)
                if (!dialog(
                        title = title,
                        panel = panel,
                    ).showAndGet()
                ) {
                    return@invokeAndWait
                }
                inputFromUser = true
            }, ModalityState.defaultModalityState())
            return inputFromUser
        }

        /**
         * Generic function to ask for input.
         */
        @JvmStatic
        fun ask(comment: String, isError: Boolean = false, link: Pair<String, String>? = null, default: String? = null): String? {
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
         * Open a dialog for providing the token.  Show any existing token so
         * the user can validate it if a previous connection failed.
         *
         * If we are not retrying and the user has not checked the existing
         * token box then also open a browser to the auth page.
         *
         * If the user has checked the existing token box then return the token
         * on disk immediately and skip the dialog (this will overwrite any
         * other existing token) unless this is a retry to avoid clobbering the
         * token that just failed.
         */
        @JvmStatic
        fun askToken(
            url: URL,
            token: Pair<String, Source>?,
            isRetry: Boolean,
            useExisting: Boolean,
            settings: CoderSettings,
        ): Pair<String, Source>? {
            var (existingToken, tokenSource) = token ?: Pair("", Source.USER)
            val getTokenUrl = url.withPath("/login?redirect=%2Fcli-auth")

            // On the first run either open a browser to generate a new token
            // or, if using an existing token, use the token on disk if it
            // exists otherwise assume the user already copied an existing
            // token and they will paste in.
            if (!isRetry) {
                if (!useExisting) {
                    BrowserUtil.browse(getTokenUrl)
                } else {
                    // Look on disk in case we already have a token, either in
                    // the deployment's config or the global config.
                    val token = settings.token(url.toString())
                    if (token != null && token.first != existingToken) {
                        logger.info("Injecting token for $url from ${token.second}")
                        return token
                    }
                }
            }

            // On subsequent tries or if not using an existing token, ask the user
            // for the token.
            val tokenFromUser = ask(
                CoderGatewayBundle.message(
                    if (isRetry) "gateway.connector.view.workspaces.token.rejected"
                    else if (tokenSource == Source.CONFIG) "gateway.connector.view.workspaces.token.injected-global"
                    else if (tokenSource == Source.DEPLOYMENT_CONFIG) "gateway.connector.view.workspaces.token.injected"
                    else if (tokenSource == Source.LAST_USED) "gateway.connector.view.workspaces.token.last-used"
                    else if (tokenSource == Source.QUERY) "gateway.connector.view.workspaces.token.query"
                    else if (existingToken.isNotBlank()) "gateway.connector.view.workspaces.token.comment"
                    else "gateway.connector.view.workspaces.token.none",
                    url.host,
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
                tokenSource = Source.USER
            }
            return Pair(tokenFromUser, tokenSource)
        }

        /**
         * Return if the URL is allowlisted, https, and the URL and its final
         * destination, if it is a different host.
         */
        @JvmStatic
        fun isAllowlisted(url: URL): Triple<Boolean, Boolean, String> {
            // TODO: Setting for the allowlist, and remember previously allowed
            //  domains.
            val domainAllowlist = listOf("intellij.net", "jetbrains.com")

            // Resolve any redirects.
            val finalUrl = try {
                resolveRedirects(url)
            } catch (e: Exception) {
                when (e) {
                    is SSLHandshakeException ->
                    throw Exception(CoderGatewayBundle.message(
                        "gateway.connector.view.workspaces.connect.ssl-error",
                        url.host,
                        e.message ?: CoderGatewayBundle.message("gateway.connector.view.workspaces.connect.no-reason")
                    ))
                    else -> throw e
                }
            }

            var linkWithRedirect = url.toString()
            if (finalUrl.host != url.host) {
                linkWithRedirect = "$linkWithRedirect (redirects to to $finalUrl)"
            }

            val allowlisted = domainAllowlist.any { url.host == it || url.host.endsWith(".$it") }
                    && domainAllowlist.any { finalUrl.host == it || finalUrl.host.endsWith(".$it") }
            val https = url.protocol == "https" && finalUrl.protocol == "https"
            return Triple(allowlisted, https, linkWithRedirect)
        }

        /**
         * Follow a URL's redirects to its final destination.
         */
        @JvmStatic
        fun resolveRedirects(url: URL): URL {
            var location = url
            val maxRedirects = 10
            for (i in 1..maxRedirects) {
                val conn = location.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connect()
                val code = conn.responseCode
                val nextLocation = conn.getHeaderField("Location")
                conn.disconnect()
                // Redirects are triggered by any code starting with 3 plus a
                // location header.
                if (code < 300 || code >= 400 || nextLocation.isNullOrBlank()) {
                    return location
                }
                // Location headers might be relative.
                location = URL(location, nextLocation)
            }
            throw Exception("Too many redirects")
        }
    }
}
