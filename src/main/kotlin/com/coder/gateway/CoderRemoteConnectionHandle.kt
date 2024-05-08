@file:Suppress("DialogTitleCapitalization")

package com.coder.gateway

import com.coder.gateway.models.WorkspaceProjectIDE
import com.coder.gateway.models.toRawString
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
import com.intellij.remote.AuthType
import com.intellij.remote.RemoteCredentialsHolder
import com.intellij.remoteDev.hostStatus.UnattendedHostStatus
import com.intellij.ui.AppIcon
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.applyIf
import com.intellij.util.ui.UIUtil
import com.jetbrains.gateway.ssh.ClientOverSshTunnelConnector
import com.jetbrains.gateway.ssh.HighLevelHostAccessor
import com.jetbrains.gateway.ssh.SshHostTunnelConnector
import com.jetbrains.gateway.ssh.deploy.DeployException
import com.jetbrains.gateway.ssh.deploy.ShellArgument
import com.jetbrains.gateway.ssh.deploy.TransferProgressTracker
import com.jetbrains.gateway.ssh.util.validateIDEInstallPath
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.LifetimeStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import net.schmizz.sshj.common.SSHException
import net.schmizz.sshj.connection.ConnectionException
import org.zeroturnaround.exec.ProcessExecutor
import java.awt.Dimension
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// CoderRemoteConnection uses the provided workspace SSH parameters to launch an
// IDE against the workspace.  If successful the connection is added to recent
// connections.
@Suppress("UnstableApiUsage")
class CoderRemoteConnectionHandle {
    private val recentConnectionsService = service<CoderRecentWorkspaceConnectionsService>()
    private val settings = service<CoderSettingsService>()

    private val localTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm")

    fun connect(getParameters: (indicator: ProgressIndicator) -> WorkspaceProjectIDE) {
        val clientLifetime = LifetimeDefinition()
        clientLifetime.launchUnderBackgroundProgress(CoderGatewayBundle.message("gateway.connector.coder.connection.provider.title")) {
            try {
                val parameters = getParameters(indicator)
                logger.debug("Creating connection handle", parameters)
                indicator.text = CoderGatewayBundle.message("gateway.connector.coder.connecting")
                suspendingRetryWithExponentialBackOff(
                    action = { attempt ->
                        logger.info("Connecting... (attempt $attempt)")
                        if (attempt > 1) {
                            // indicator.text is the text above the progress bar.
                            indicator.text = CoderGatewayBundle.message("gateway.connector.coder.connecting.retry", attempt)
                        }
                        doConnect(
                            parameters,
                            indicator,
                            clientLifetime,
                            settings.setupCommand,
                            settings.ignoreSetupFailure,
                        )
                    },
                    retryIf = {
                        it is ConnectionException || it is TimeoutException ||
                            it is SSHException || it is DeployException
                    },
                    onException = { attempt, nextMs, e ->
                        logger.error("Failed to connect (attempt $attempt; will retry in $nextMs ms)")
                        // indicator.text2 is the text below the progress bar.
                        indicator.text2 =
                            if (isWorkerTimeout(e)) {
                                "Failed to upload worker binary...it may have timed out"
                            } else {
                                e.message ?: e.javaClass.simpleName
                            }
                    },
                    onCountdown = { remainingMs ->
                        indicator.text =
                            CoderGatewayBundle.message(
                                "gateway.connector.coder.connecting.failed.retry",
                                humanizeDuration(remainingMs),
                            )
                    },
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
                            Messages.getErrorIcon(),
                        )
                    }
                }
            }
        }
    }

    /**
     * Deploy (if needed), connect to the IDE, and update the last opened date.
     */
    private suspend fun doConnect(
        workspace: WorkspaceProjectIDE,
        indicator: ProgressIndicator,
        lifetime: LifetimeDefinition,
        setupCommand: String,
        ignoreSetupFailure: Boolean,
        timeout: Duration = Duration.ofMinutes(10),
    ) {
        workspace.lastOpened = localTimeFormatter.format(LocalDateTime.now())

        // This establishes an SSH connection to a remote worker binary.
        // TODO: Can/should accessors to the same host be shared?
        indicator.text = "Connecting to remote worker..."
        logger.info("Connecting to remote worker on ${workspace.hostname}")
        val credentials = RemoteCredentialsHolder().apply {
            setHost(workspace.hostname)
            userName = "coder"
            port = 22
            authType = AuthType.OPEN_SSH
        }
        val accessor = HighLevelHostAccessor.create(credentials, true)

        // Deploy if we need to.
        val ideDir = this.deploy(workspace, accessor, indicator, timeout)
        workspace.idePathOnHost = ideDir.toRawString()

        // Run the setup command.
        this.setup(workspace, indicator, setupCommand, ignoreSetupFailure)

        // Wait for the IDE to come up.
        indicator.text = "Waiting for ${workspace.ideName} backend..."
        var status: UnattendedHostStatus? = null
        val remoteProjectPath = accessor.makeRemotePath(ShellArgument.PlainText(workspace.projectPath))
        val logsDir = accessor.getLogsDir(workspace.ideProductCode.productCode, remoteProjectPath)
        while (lifetime.status == LifetimeStatus.Alive) {
            status = ensureIDEBackend(workspace, accessor, ideDir, remoteProjectPath, logsDir, lifetime, null)
            if (!status?.joinLink.isNullOrBlank()) {
                break
            }
            delay(5000)
        }

        // We wait for non-null, so this only happens on cancellation.
        val joinLink = status?.joinLink
        if (joinLink.isNullOrBlank()) {
            logger.info("Connection to ${workspace.ideName} on ${workspace.hostname} was canceled")
            return
        }

        // Make the initial connection.
        indicator.text = "Connecting ${workspace.ideName} client..."
        logger.info("Connecting ${workspace.ideName} client to coder@${workspace.hostname}:22")
        val client = ClientOverSshTunnelConnector(lifetime, SshHostTunnelConnector(credentials))
        val handle = client.connect(URI(joinLink)) // Downloads the client too, if needed.

        // Reconnect if the join link changes.
        logger.info("Launched ${workspace.ideName} client; beginning backend monitoring")
        lifetime.coroutineScope.launch {
            while (isActive) {
                delay(5000)
                val newStatus = ensureIDEBackend(workspace, accessor, ideDir, remoteProjectPath, logsDir, lifetime, status)
                val newLink = newStatus?.joinLink
                if (newLink != null && newLink != status?.joinLink) {
                    logger.info("${workspace.ideName} backend join link changed; updating")
                    // Unfortunately, updating the link is not a smooth
                    // reconnection.  The client closes and is relaunched.
                    // Trying to reconnect without updating the link results in
                    // a fingerprint mismatch error.
                    handle.updateJoinLink(URI(newLink), true)
                    status = newStatus
                }
            }
        }

        // Tie the lifetime and client together, and wait for the initial open.
        suspendCancellableCoroutine { continuation ->
            // Close the client if the user cancels.
            lifetime.onTermination {
                logger.info("Connection to ${workspace.ideName} on ${workspace.hostname} canceled")
                if (continuation.isActive) {
                    continuation.cancel()
                }
                handle.close()
            }
            // Kill the lifetime if the client is closed by the user.
            handle.clientClosed.advise(lifetime) {
                logger.info("${workspace.ideName} client ${workspace.hostname} closed")
                if (lifetime.status == LifetimeStatus.Alive) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(Exception("${workspace.ideName} client was closed"))
                    }
                    lifetime.terminate()
                }
            }
            // Continue once the client is present.
            handle.onClientPresenceChanged.advise(lifetime) {
                if (handle.clientPresent && continuation.isActive) {
                    continuation.resume(true)
                }
            }
        }

        // The presence handler runs a good deal earlier than the client
        // actually appears, which results in some dead space where it can look
        // like opening the client silently failed.  This delay janks around
        // that, so we can keep the progress indicator open a bit longer.
        delay(5000)
    }

    /**
     * Deploy the IDE if necessary and return the path to its location on disk.
     */
    private suspend fun deploy(
        workspace: WorkspaceProjectIDE,
        accessor: HighLevelHostAccessor,
        indicator: ProgressIndicator,
        timeout: Duration,
    ): ShellArgument.RemotePath {
        // The backend might already exist at the provided path.
        if (!workspace.idePathOnHost.isNullOrBlank()) {
            indicator.text = "Verifying ${workspace.ideName} installation..."
            logger.info("Verifying ${workspace.ideName} exists at ${workspace.hostname}:${workspace.idePathOnHost}")
            val validatedPath = validateIDEInstallPath(workspace.idePathOnHost, accessor).pathOrNull
            if (validatedPath != null) {
                logger.info("${workspace.ideName} exists at ${workspace.hostname}:${validatedPath.toRawString()}")
                return validatedPath
            }
        }

        // The backend might already be installed somewhere on the system.
        indicator.text = "Searching for ${workspace.ideName} installation..."
        logger.info("Searching for ${workspace.ideName} on ${workspace.hostname}")
        val installed =
            accessor.getInstalledIDEs().find {
                it.product == workspace.ideProductCode && it.buildNumber == workspace.ideBuildNumber
            }
        if (installed != null) {
            logger.info("${workspace.ideName} found at ${workspace.hostname}:${installed.pathToIde}")
            return accessor.makeRemotePath(ShellArgument.PlainText(installed.pathToIde))
        }

        // Otherwise we have to download it.
        if (workspace.downloadSource.isNullOrBlank()) {
            throw Exception("${workspace.ideName} could not be found on the remote and no download source was provided")
        }

        // TODO: Should we download to idePathOnHost if set?  That would require
        //       symlinking instead of creating the sentinel file if the path is
        //       outside the default dist directory.
        indicator.text = "Downloading ${workspace.ideName}..."
        indicator.text2 = workspace.downloadSource
        val distDir = accessor.getDefaultDistDir()

        // HighLevelHostAccessor.downloadFile does NOT create the directory.
        logger.info("Creating ${workspace.hostname}:${distDir.toRawString()}")
        accessor.createPathOnRemote(distDir)

        // Download the IDE.
        val fileName = workspace.downloadSource.split("/").last()
        val downloadPath = distDir.join(listOf(ShellArgument.PlainText(fileName)))
        logger.info("Downloading ${workspace.ideName} to ${workspace.hostname}:${downloadPath.toRawString()} from ${workspace.downloadSource}")
        accessor.downloadFile(
            indicator,
            URI(workspace.downloadSource),
            downloadPath,
            object : TransferProgressTracker {
                override var isCancelled: Boolean = false

                override fun updateProgress(
                    transferred: Long,
                    speed: Long?,
                ) {
                    // Since there is no total size, this is useless.
                }
            },
        )

        // Extract the IDE to its final resting place.
        val ideDir = distDir.join(listOf(ShellArgument.PlainText(workspace.ideName)))
        indicator.text = "Extracting ${workspace.ideName}..."
        indicator.text2 = ""
        logger.info("Extracting ${workspace.ideName} to ${workspace.hostname}:${ideDir.toRawString()}")
        accessor.removePathOnRemote(ideDir)
        accessor.expandArchive(downloadPath, ideDir, timeout.toMillis())
        accessor.removePathOnRemote(downloadPath)

        // Without this file it does not show up in the installed IDE list.
        val sentinelFile = ideDir.join(listOf(ShellArgument.PlainText(".expandSucceeded"))).toRawString()
        logger.info("Creating ${workspace.hostname}:$sentinelFile")
        accessor.fileAccessor.uploadFileFromLocalStream(
            sentinelFile,
            "".byteInputStream(),
            null,
        )

        logger.info("Successfully installed ${workspace.ideName} on ${workspace.hostname}")
        return ideDir
    }

    /**
     * Run the setup command in the IDE's bin directory.
     */
    private fun setup(
        workspace: WorkspaceProjectIDE,
        indicator: ProgressIndicator,
        setupCommand: String,
        ignoreSetupFailure: Boolean,
    ) {
        if (setupCommand.isNotBlank()) {
            indicator.text = "Running setup command..."
            try {
                exec(workspace, setupCommand)
            } catch (ex: Exception) {
                if (!ignoreSetupFailure) {
                    throw ex
                }
            }
        } else {
            logger.info("No setup command to run on ${workspace.hostname}")
        }
    }

    /**
     * Execute a command in the IDE's bin directory.
     * This exists since the accessor does not provide a generic exec.
     */
    private fun exec(workspace: WorkspaceProjectIDE, command: String): String {
        logger.info("Running command `$command` in ${workspace.hostname}:${workspace.idePathOnHost}/bin...")
        return ProcessExecutor()
            .command("ssh", "-t", workspace.hostname, "cd '${workspace.idePathOnHost}' ; cd bin ; $command")
            .exitValues(0)
            .readOutput(true)
            .execute()
            .outputUTF8()
    }

    /**
     * Ensure the backend is started.  Status and/or links may be null if the
     * backend has not started.
     */
    private suspend fun ensureIDEBackend(
        workspace: WorkspaceProjectIDE,
        accessor: HighLevelHostAccessor,
        ideDir: ShellArgument.RemotePath,
        remoteProjectPath: ShellArgument.RemotePath,
        logsDir: ShellArgument.RemotePath,
        lifetime: LifetimeDefinition,
        currentStatus: UnattendedHostStatus?,
    ): UnattendedHostStatus? {
        val details = "${workspace.hostname}:${ideDir.toRawString()}, project=${remoteProjectPath.toRawString()}"
        return try {
            if (currentStatus?.appPid != null &&
                !currentStatus.joinLink.isNullOrBlank() &&
                accessor.isPidAlive(currentStatus.appPid.toInt())
            ) {
                // If the PID is alive, assume the join link we have is still
                // valid.  The join link seems to change even if it is the same
                // backend running, so if we always fetched the link the client
                // would relaunch over and over.
                return currentStatus
            }

            // See if there is already a backend running.  Weirdly, there is
            // always a PID, even if there is no backend running, and
            // backendUnresponsive is always false, but the links are null so
            // hopefully that is an accurate indicator that the IDE is up.
            val status = accessor.getHostIdeStatus(ideDir, remoteProjectPath)
            if (!status.joinLink.isNullOrBlank()) {
                logger.info("Found existing ${workspace.ideName} backend on $details")
                return status
            }

            // Otherwise, spawn a new backend.  This does not seem to spawn a
            // second backend if one is already running, yet it does somehow
            // cause a second client to launch.  So only run this if we are
            // really sure we have to launch a new backend.
            logger.info("Starting ${workspace.ideName} backend on $details")
            accessor.startHostIdeInBackgroundAndDetach(lifetime, ideDir, remoteProjectPath, logsDir)
            // Get the newly spawned PID and join link.
            return accessor.getHostIdeStatus(ideDir, remoteProjectPath)
        } catch (ex: Exception) {
            logger.info("Failed to get ${workspace.ideName} status from $details", ex)
            currentStatus
        }
    }

    companion object {
        val logger = Logger.getInstance(CoderRemoteConnectionHandle::class.java.simpleName)

        /**
         * Generic function to ask for consent.
         */
        fun confirm(
            title: String,
            comment: String,
            details: String,
        ): Boolean {
            var inputFromUser = false
            ApplicationManager.getApplication().invokeAndWait({
                val panel =
                    panel {
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
        fun ask(
            comment: String,
            isError: Boolean = false,
            link: Pair<String, String>? = null,
            default: String? = null,
        ): String? {
            var inputFromUser: String? = null
            ApplicationManager.getApplication().invokeAndWait({
                lateinit var inputTextField: JBTextField
                val panel =
                    panel {
                        row {
                            if (link != null) browserLink(link.first, link.second)
                            inputTextField =
                                textField()
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
                                    },
                            )
                        }.layout(RowLayout.PARENT_GRID)
                    }
                AppIcon.getInstance().requestAttention(null, true)
                if (!dialog(
                        CoderGatewayBundle.message("gateway.connector.view.login.token.dialog"),
                        panel = panel,
                        focusedComponent = inputTextField,
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
                    val tryToken = settings.token(url)
                    if (tryToken != null && tryToken.first != existingToken) {
                        logger.info("Injecting token for $url from ${tryToken.second}")
                        return tryToken
                    }
                }
            }

            // On subsequent tries or if not using an existing token, ask the user
            // for the token.
            val tokenFromUser =
                ask(
                    CoderGatewayBundle.message(
                        if (isRetry) {
                            "gateway.connector.view.workspaces.token.rejected"
                        } else if (tokenSource == Source.CONFIG) {
                            "gateway.connector.view.workspaces.token.injected-global"
                        } else if (tokenSource == Source.DEPLOYMENT_CONFIG) {
                            "gateway.connector.view.workspaces.token.injected"
                        } else if (tokenSource == Source.LAST_USED) {
                            "gateway.connector.view.workspaces.token.last-used"
                        } else if (tokenSource == Source.QUERY) {
                            "gateway.connector.view.workspaces.token.query"
                        } else if (existingToken.isNotBlank()) {
                            "gateway.connector.view.workspaces.token.comment"
                        } else {
                            "gateway.connector.view.workspaces.token.none"
                        },
                        url.host,
                    ),
                    isRetry,
                    Pair(
                        CoderGatewayBundle.message("gateway.connector.view.login.token.label"),
                        getTokenUrl.toString(),
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
            val finalUrl =
                try {
                    resolveRedirects(url)
                } catch (e: Exception) {
                    when (e) {
                        is SSLHandshakeException ->
                            throw Exception(
                                CoderGatewayBundle.message(
                                    "gateway.connector.view.workspaces.connect.ssl-error",
                                    url.host,
                                    e.message ?: CoderGatewayBundle.message("gateway.connector.view.workspaces.connect.no-reason"),
                                ),
                            )
                        else -> throw e
                    }
                }

            var linkWithRedirect = url.toString()
            if (finalUrl.host != url.host) {
                linkWithRedirect = "$linkWithRedirect (redirects to to $finalUrl)"
            }

            val allowlisted =
                domainAllowlist.any { url.host == it || url.host.endsWith(".$it") } &&
                    domainAllowlist.any { finalUrl.host == it || finalUrl.host.endsWith(".$it") }
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
