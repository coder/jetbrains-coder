@file:Suppress("DialogTitleCapitalization")

package com.coder.gateway

import com.coder.gateway.cli.CoderCLIManager
import com.coder.gateway.models.WorkspaceProjectIDE
import com.coder.gateway.models.toIdeWithStatus
import com.coder.gateway.models.toRawString
import com.coder.gateway.models.withWorkspaceProject
import com.coder.gateway.services.CoderRecentWorkspaceConnectionsService
import com.coder.gateway.services.CoderSettingsService
import com.coder.gateway.util.DialogUi
import com.coder.gateway.util.SemVer
import com.coder.gateway.util.humanizeDuration
import com.coder.gateway.util.isCancellation
import com.coder.gateway.util.isWorkerTimeout
import com.coder.gateway.util.suspendingRetryWithExponentialBackOff
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.rd.util.launchUnderBackgroundProgress
import com.intellij.openapi.ui.Messages
import com.intellij.remote.AuthType
import com.intellij.remote.RemoteCredentialsHolder
import com.intellij.remoteDev.hostStatus.UnattendedHostStatus
import com.jetbrains.gateway.ssh.CachingProductsJsonWrapper
import com.jetbrains.gateway.ssh.ClientOverSshTunnelConnector
import com.jetbrains.gateway.ssh.HighLevelHostAccessor
import com.jetbrains.gateway.ssh.IdeWithStatus
import com.jetbrains.gateway.ssh.IntelliJPlatformProduct
import com.jetbrains.gateway.ssh.ReleaseType
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
import java.net.URI
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
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
    private val dialogUi = DialogUi(settings)

    fun connect(getParameters: (indicator: ProgressIndicator) -> WorkspaceProjectIDE) {
        val clientLifetime = LifetimeDefinition()
        clientLifetime.launchUnderBackgroundProgress(CoderGatewayBundle.message("gateway.connector.coder.connection.provider.title")) {
            try {
                var parameters = getParameters(indicator)
                var oldParameters: WorkspaceProjectIDE? = null
                logger.debug("Creating connection handle", parameters)
                indicator.text = CoderGatewayBundle.message("gateway.connector.coder.connecting")
                suspendingRetryWithExponentialBackOff(
                    action = { attempt ->
                        logger.info("Connecting to remote worker on ${parameters.hostname}... (attempt $attempt)")
                        if (attempt > 1) {
                            // indicator.text is the text above the progress bar.
                            indicator.text = CoderGatewayBundle.message("gateway.connector.coder.connecting.retry", attempt)
                        } else {
                            indicator.text = "Connecting to remote worker..."
                        }
                        // This establishes an SSH connection to a remote worker binary.
                        // TODO: Can/should accessors to the same host be shared?
                        val accessor = HighLevelHostAccessor.create(
                            RemoteCredentialsHolder().apply {
                                setHost(CoderCLIManager.getBackgroundHostName(parameters.hostname))
                                userName = "coder"
                                port = 22
                                authType = AuthType.OPEN_SSH
                            },
                            true,
                        )
                        if (settings.checkIDEUpdate && attempt == 1) {
                            // See if there is a newer (non-EAP) version of the IDE available.
                            checkUpdate(accessor, parameters, indicator)?.let { update ->
                                // Store the old IDE to delete later.
                                oldParameters = parameters
                                // Continue with the new IDE.
                                parameters = update.withWorkspaceProject(
                                    name = parameters.name,
                                    hostname = parameters.hostname,
                                    projectPath = parameters.projectPath,
                                    deploymentURL = parameters.deploymentURL,
                                )
                            }
                        }
                        doConnect(
                            accessor,
                            parameters,
                            indicator,
                            clientLifetime,
                            settings.setupCommand,
                            settings.ignoreSetupFailure,
                        )
                        // If successful, delete the old IDE and connection.
                        oldParameters?.let {
                            indicator.text = "Deleting ${it.ideName} backend..."
                            try {
                                it.idePathOnHost?.let { path ->
                                    accessor.removePathOnRemote(accessor.makeRemotePath(ShellArgument.PlainText(path)))
                                }
                                recentConnectionsService.removeConnection(it.toRecentWorkspaceConnection())
                            } catch (ex: Exception) {
                                logger.error("Failed to delete old IDE or connection", ex)
                            }
                        }
                        indicator.text = "Connecting ${parameters.ideName} client..."
                        // The presence handler runs a good deal earlier than the client
                        // actually appears, which results in some dead space where it can look
                        // like opening the client silently failed.  This delay janks around
                        // that, so we can keep the progress indicator open a bit longer.
                        delay(5000)
                    },
                    retryIf = {
                        it is ConnectionException ||
                            it is TimeoutException ||
                            it is SSHException ||
                            it is DeployException
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
     * Return a new (non-EAP) IDE if we should update.
     */
    private suspend fun checkUpdate(
        accessor: HighLevelHostAccessor,
        workspace: WorkspaceProjectIDE,
        indicator: ProgressIndicator,
    ): IdeWithStatus? {
        indicator.text = "Checking for updates..."
        val workspaceOS = accessor.guessOs()
        logger.info("Got $workspaceOS for ${workspace.hostname}")
        val latest = CachingProductsJsonWrapper.getInstance().getAvailableIdes(
            IntelliJPlatformProduct.fromProductCode(workspace.ideProduct.productCode)
                ?: throw Exception("invalid product code ${workspace.ideProduct.productCode}"),
            workspaceOS,
        )
            .filter { it.releaseType == ReleaseType.RELEASE }
            .minOfOrNull { it.toIdeWithStatus() }
        if (latest != null && SemVer.parse(latest.buildNumber) > SemVer.parse(workspace.ideBuildNumber)) {
            logger.info("Got newer version: ${latest.buildNumber} versus current ${workspace.ideBuildNumber}")
            if (dialogUi.confirm("Update IDE", "There is a new version of this IDE: ${latest.buildNumber}. Would you like to update?")) {
                return latest
            }
        }
        return null
    }

    /**
     * Check for updates, deploy (if needed), connect to the IDE, and update the
     * last opened date.
     */
    private suspend fun doConnect(
        accessor: HighLevelHostAccessor,
        workspace: WorkspaceProjectIDE,
        indicator: ProgressIndicator,
        lifetime: LifetimeDefinition,
        setupCommand: String,
        ignoreSetupFailure: Boolean,
        timeout: Duration = Duration.ofMinutes(10),
    ) {
        workspace.lastOpened = localTimeFormatter.format(LocalDateTime.now())

        // Deploy if we need to.
        val ideDir = deploy(accessor, workspace, indicator, timeout)
        workspace.idePathOnHost = ideDir.toRawString()

        // Run the setup command.
        setup(workspace, indicator, setupCommand, ignoreSetupFailure)

        // Wait for the IDE to come up.
        indicator.text = "Waiting for ${workspace.ideName} backend..."
        val remoteProjectPath = accessor.makeRemotePath(ShellArgument.PlainText(workspace.projectPath))
        val logsDir = accessor.getLogsDir(workspace.ideProduct.productCode, remoteProjectPath)
        var status = ensureIDEBackend(accessor, workspace, ideDir, remoteProjectPath, logsDir, lifetime, null)

        // We wait for non-null, so this only happens on cancellation.
        val joinLink = status?.joinLink
        if (joinLink.isNullOrBlank()) {
            logger.info("Connection to ${workspace.ideName} on ${workspace.hostname} was canceled")
            return
        }

        // Makes sure the ssh log directory exists.
        if (settings.sshLogDirectory.isNotBlank()) {
            Path.of(settings.sshLogDirectory).toFile().mkdirs()
        }

        // Make the initial connection.
        indicator.text = "Connecting ${workspace.ideName} client..."
        logger.info("Connecting ${workspace.ideName} client to coder@${workspace.hostname}:22")
        val client = ClientOverSshTunnelConnector(
            lifetime,
            SshHostTunnelConnector(
                RemoteCredentialsHolder().apply {
                    setHost(workspace.hostname)
                    userName = "coder"
                    port = 22
                    authType = AuthType.OPEN_SSH
                },
            ),
        )
        val handle = client.connect(URI(joinLink)) // Downloads the client too, if needed.

        // Reconnect if the join link changes.
        logger.info("Launched ${workspace.ideName} client; beginning backend monitoring")
        lifetime.coroutineScope.launch {
            while (isActive) {
                delay(5000)
                val newStatus = ensureIDEBackend(accessor, workspace, ideDir, remoteProjectPath, logsDir, lifetime, status)
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
                logger.info("${workspace.ideName} client to ${workspace.hostname} closed")
                if (lifetime.status == LifetimeStatus.Alive) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(Exception("${workspace.ideName} client was closed"))
                    }
                    lifetime.terminate()
                }
            }
            // Continue once the client is present.
            handle.onClientPresenceChanged.advise(lifetime) {
                logger.info("${workspace.ideName} client to ${workspace.hostname} presence: ${handle.clientPresent}")
                if (handle.clientPresent && continuation.isActive) {
                    continuation.resume(true)
                }
            }
        }
    }

    /**
     * Deploy the IDE if necessary and return the path to its location on disk.
     */
    private suspend fun deploy(
        accessor: HighLevelHostAccessor,
        workspace: WorkspaceProjectIDE,
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
                it.product == workspace.ideProduct && it.buildNumber == workspace.ideBuildNumber
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
            .command("ssh", "-t", CoderCLIManager.getBackgroundHostName(workspace.hostname), "cd '${workspace.idePathOnHost}' ; cd bin ; $command")
            .exitValues(0)
            .readOutput(true)
            .execute()
            .outputUTF8()
    }

    /**
     * Ensure the backend is started.  It will not return until a join link is
     * received or the lifetime expires.
     */
    private suspend fun ensureIDEBackend(
        accessor: HighLevelHostAccessor,
        workspace: WorkspaceProjectIDE,
        ideDir: ShellArgument.RemotePath,
        remoteProjectPath: ShellArgument.RemotePath,
        logsDir: ShellArgument.RemotePath,
        lifetime: LifetimeDefinition,
        currentStatus: UnattendedHostStatus?,
    ): UnattendedHostStatus? {
        val details = "${workspace.hostname}:${ideDir.toRawString()}, project=${remoteProjectPath.toRawString()}"
        val wait = TimeUnit.SECONDS.toMillis(5)

        // Check if the current IDE is alive.
        if (currentStatus != null) {
            while (lifetime.status == LifetimeStatus.Alive) {
                try {
                    val isAlive = accessor.isPidAlive(currentStatus.appPid.toInt())
                    logger.info("${workspace.ideName} status: pid=${currentStatus.appPid}, alive=$isAlive")
                    if (isAlive) {
                        // Use the current status and join link.
                        return currentStatus
                    } else {
                        logger.info("Relaunching ${workspace.ideName} since it is not alive...")
                        break
                    }
                } catch (ex: Exception) {
                    logger.info("Failed to check if ${workspace.ideName} is alive on $details; waiting $wait ms to try again: pid=${currentStatus.appPid}", ex)
                }
                delay(wait)
            }
        } else {
            logger.info("Launching ${workspace.ideName} for the first time on ${workspace.hostname}...")
        }

        // This means we broke out because the user canceled or closed the IDE.
        if (lifetime.status != LifetimeStatus.Alive) {
            return null
        }

        // If the PID is not alive, spawn a new backend.  This may not be
        // idempotent, so only call if we are really sure we need to.
        accessor.startHostIdeInBackgroundAndDetach(lifetime, ideDir, remoteProjectPath, logsDir)

        // Get the newly spawned PID and join link.
        var attempts = 0
        val maxAttempts = 6
        while (lifetime.status == LifetimeStatus.Alive) {
            try {
                attempts++
                val status = accessor.getHostIdeStatus(ideDir, remoteProjectPath)
                if (!status.joinLink.isNullOrBlank()) {
                    logger.info("Found join link for ${workspace.ideName}; proceeding to connect: pid=${status.appPid}")
                    return status
                }
                // If we did not get a join link, see if the IDE is alive in
                // case it died and we need to respawn.
                val isAlive = status.appPid > 0 && accessor.isPidAlive(status.appPid.toInt())
                logger.info("${workspace.ideName} status: pid=${status.appPid}, alive=$isAlive, unresponsive=${status.backendUnresponsive}, attempt=$attempts")
                // It is not clear whether the PID can be trusted because we get
                // one even when there is no backend at all.  For now give it
                // some time and if it is still dead, only then try to respawn.
                if (!isAlive && attempts >= maxAttempts) {
                    logger.info("${workspace.ideName} is still not alive after $attempts checks, respawning backend and waiting $wait ms to try again")
                    accessor.startHostIdeInBackgroundAndDetach(lifetime, ideDir, remoteProjectPath, logsDir)
                    attempts = 0
                } else {
                    logger.info("No join link found in status; waiting $wait ms to try again")
                }
            } catch (ex: Exception) {
                logger.info("Failed to get ${workspace.ideName} status from $details; waiting $wait ms to try again", ex)
            }
            delay(wait)
        }

        // This means the lifetime is no longer alive.
        logger.info("Connection to ${workspace.ideName} on $details aborted by user")
        return null
    }

    companion object {
        val logger = Logger.getInstance(CoderRemoteConnectionHandle::class.java.simpleName)
    }
}
