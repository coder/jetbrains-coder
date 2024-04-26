package com.coder.gateway.models

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.remote.AuthType
import com.intellij.remote.RemoteCredentialsHolder
import com.intellij.ssh.config.unified.SshConfig
import com.jetbrains.gateway.ssh.HighLevelHostAccessor
import com.jetbrains.gateway.ssh.HostDeployInputs
import com.jetbrains.gateway.ssh.IdeInfo
import com.jetbrains.gateway.ssh.IdeWithStatus
import com.jetbrains.gateway.ssh.IntelliJPlatformProduct
import com.jetbrains.gateway.ssh.deploy.DeployTargetInfo
import com.jetbrains.gateway.ssh.deploy.ShellArgument
import com.jetbrains.gateway.ssh.deploy.TransferProgressTracker
import com.jetbrains.gateway.ssh.util.validateIDEInstallPath
import org.zeroturnaround.exec.ProcessExecutor
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.name

private val localTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm")

/**
 * Validated parameters for downloading (if necessary) and opening a project
 * using an IDE on a workspace.
 */
@Suppress("UnstableApiUsage")
class WorkspaceProjectIDE(
    val name: String,
    val hostname: String,
    val projectPath: String,
    val ideProductCode: IntelliJPlatformProduct,
    val ideBuildNumber: String,
    // One of these must exist; enforced by the constructor.
    var idePathOnHost: String?,
    val downloadSource: String?,
    // These are used in the recent connections window.
    val deploymentURL: URL,
    var lastOpened: String?, // Null if never opened.
) {
    val ideName = "${ideProductCode.productCode}-$ideBuildNumber"

    private val maxDisplayLength = 35

    /**
     * A shortened path for displaying where space is tight.
     */
    val projectPathDisplay =
        if (projectPath.length <= maxDisplayLength) {
            projectPath
        } else {
            "…" + projectPath.substring(projectPath.length - maxDisplayLength, projectPath.length)
        }

    init {
        if (idePathOnHost.isNullOrBlank() && downloadSource.isNullOrBlank()) {
            throw Exception("A path to the IDE on the host or a download source is required")
        }
    }

    /**
     * Return an accessor for connecting to the IDE, deploying it first if
     * necessary.  If a deployment was necessary, the IDE path on the host will
     * be updated to reflect the location on disk.
     */
    suspend fun deploy(
        indicator: ProgressIndicator,
        timeout: Duration,
        setupCommand: String,
        ignoreSetupFailure: Boolean,
    ): HostDeployInputs {
        this.lastOpened = localTimeFormatter.format(LocalDateTime.now())
        indicator.text = "Connecting to remote worker..."
        logger.info("Connecting to remote worker on $hostname")
        val accessor =
            HighLevelHostAccessor.create(
                RemoteCredentialsHolder().apply {
                    setHost(hostname)
                    userName = "coder"
                    port = 22
                    authType = AuthType.OPEN_SSH
                },
                true,
            )

        val path = this.doDeploy(accessor, indicator, timeout)
        idePathOnHost = path

        if (setupCommand.isNotBlank()) {
            // The accessor does not appear to provide a generic exec.
            indicator.text = "Running setup command..."
            logger.info("Running setup command `$setupCommand` in $path on $hostname...")
            try {
                exec(setupCommand)
            } catch (ex: Exception) {
                if (!ignoreSetupFailure) {
                    throw ex
                }
            }
        } else {
            logger.info("No setup command to run on $hostname")
        }

        val sshConfig =
            SshConfig(true).apply {
                setHost(hostname)
                setUsername("coder")
                port = 22
                authType = AuthType.OPEN_SSH
            }

        // This is the configuration that tells JetBrains to connect to the IDE
        // stored at this path.  It will spawn the IDE and handle reconnections,
        // but it will not respawn the IDE if it goes away.
        // TODO: We will need to handle the respawn ourselves.
        return HostDeployInputs.FullySpecified(
            remoteProjectPath = projectPath,
            deployTarget =
            DeployTargetInfo.NoDeploy(
                path,
                IdeInfo(
                    product = this.ideProductCode,
                    buildNumber = this.ideBuildNumber,
                ),
            ),
            remoteInfo =
            HostDeployInputs.WithDeployedWorker(
                accessor,
                HostDeployInputs.WithHostInfo(sshConfig),
            ),
        )
    }

    /**
     * Deploy the IDE if necessary and return the path to its location on disk.
     */
    private suspend fun doDeploy(
        accessor: HighLevelHostAccessor,
        indicator: ProgressIndicator,
        timeout: Duration,
    ): String {
        // The backend might already exist at the provided path.
        if (!idePathOnHost.isNullOrBlank()) {
            indicator.text = "Verifying $ideName installation..."
            logger.info("Verifying $ideName exists at $idePathOnHost on $hostname")
            val validatedPath = validateIDEInstallPath(idePathOnHost, accessor).pathOrNull
            if (validatedPath != null) {
                logger.info("$ideName exists at ${validatedPath.toRawString()} on $hostname")
                return validatedPath.toRawString()
            }
        }

        // The backend might already be installed somewhere on the system.
        indicator.text = "Searching for $ideName installation..."
        logger.info("Searching for $ideName on $hostname")
        val installed =
            accessor.getInstalledIDEs().find {
                it.product == ideProductCode && it.buildNumber == ideBuildNumber
            }
        if (installed != null) {
            logger.info("$ideName found at ${installed.pathToIde} on $hostname")
            return installed.pathToIde
        }

        // Otherwise we have to download it.
        if (downloadSource.isNullOrBlank()) {
            throw Exception("The IDE could not be found on the remote and no download source was provided")
        }

        // TODO: Should we download to idePathOnHost if set?  That would require
        //       symlinking instead of creating the sentinel file if the path is
        //       outside the default dist directory.
        val distDir = accessor.getDefaultDistDir()

        // HighLevelHostAccessor.downloadFile does NOT create the directory.
        indicator.text = "Creating $distDir..."
        accessor.createPathOnRemote(distDir)

        // Download the IDE.
        val fileName = downloadSource.split("/").last()
        val downloadPath = distDir.join(listOf(ShellArgument.PlainText(fileName)))
        indicator.text = "Downloading $ideName..."
        indicator.text2 = downloadSource
        logger.info("Downloading $ideName to ${downloadPath.toRawString()} from $downloadSource on $hostname")
        accessor.downloadFile(
            indicator,
            URI(downloadSource),
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
        val ideDir = distDir.join(listOf(ShellArgument.PlainText(ideName)))
        indicator.text = "Extracting $ideName..."
        logger.info("Extracting $ideName to ${ideDir.toRawString()} on $hostname")
        accessor.removePathOnRemote(ideDir)
        accessor.expandArchive(downloadPath, ideDir, timeout.toMillis())
        accessor.removePathOnRemote(downloadPath)

        // Without this file it does not show up in the installed IDE list.
        val sentinelFile = ideDir.join(listOf(ShellArgument.PlainText(".expandSucceeded"))).toRawString()
        logger.info("Creating $sentinelFile on $hostname")
        accessor.fileAccessor.uploadFileFromLocalStream(
            sentinelFile,
            "".byteInputStream(),
            null,
        )

        logger.info("Successfully installed ${ideProductCode.productCode}-$ideBuildNumber on $hostname")
        indicator.text = "Connecting..."
        indicator.text2 = ""

        return ideDir.toRawString()
    }

    /**
     * Execute a command in the IDE's bin directory.
     */
    private fun exec(command: String): String {
        return ProcessExecutor()
            .command("ssh", "-t", hostname, "cd '$idePathOnHost' ; cd bin ; $command")
            .exitValues(0)
            .readOutput(true)
            .execute()
            .outputUTF8()
    }

    /**
     * Convert parameters into a recent workspace connection (for storage).
     */
    fun toRecentWorkspaceConnection(): RecentWorkspaceConnection {
        return RecentWorkspaceConnection(
            name = name,
            coderWorkspaceHostname = hostname,
            projectPath = projectPath,
            ideProductCode = ideProductCode.productCode,
            ideBuildNumber = ideBuildNumber,
            downloadSource = downloadSource,
            idePathOnHost = idePathOnHost,
            deploymentURL = deploymentURL.toString(),
            lastOpened = lastOpened,
        )
    }

    companion object {
        val logger = Logger.getInstance(WorkspaceProjectIDE::class.java.simpleName)

        /**
         * Create from unvalidated user inputs.
         */
        @JvmStatic
        fun fromInputs(
            name: String?,
            hostname: String?,
            projectPath: String?,
            deploymentURL: String?,
            lastOpened: String?,
            ideProductCode: String?,
            ideBuildNumber: String?,
            downloadSource: String?,
            idePathOnHost: String?,
        ): WorkspaceProjectIDE {
            if (name.isNullOrBlank()) {
                throw Exception("Workspace name is missing")
            } else if (deploymentURL.isNullOrBlank()) {
                throw Exception("Deployment URL is missing")
            } else if (hostname.isNullOrBlank()) {
                throw Exception("Host name is missing")
            } else if (projectPath.isNullOrBlank()) {
                throw Exception("Project path is missing")
            } else if (ideProductCode.isNullOrBlank()) {
                throw Exception("IDE product code is missing")
            } else if (ideBuildNumber.isNullOrBlank()) {
                throw Exception("IDE build number is missing")
            }

            return WorkspaceProjectIDE(
                name = name,
                hostname = hostname,
                projectPath = projectPath,
                ideProductCode = IntelliJPlatformProduct.fromProductCode(ideProductCode) ?: throw Exception("invalid product code"),
                ideBuildNumber = ideBuildNumber,
                idePathOnHost = idePathOnHost,
                downloadSource = downloadSource,
                deploymentURL = URL(deploymentURL),
                lastOpened = lastOpened,
            )
        }
    }
}

/**
 * Convert into parameters for making a connection to a project using an IDE
 * on a workspace.  Throw if invalid.
 */
fun RecentWorkspaceConnection.toWorkspaceProjectIDE(): WorkspaceProjectIDE {
    val hostname = coderWorkspaceHostname

    @Suppress("DEPRECATION")
    val dir = configDirectory
    return WorkspaceProjectIDE.fromInputs(
        // The name was added to query the workspace status on the recent
        // connections page, so it could be missing.  Try to get it from the
        // host name.
        name =
        if (name.isNullOrBlank() && !hostname.isNullOrBlank()) {
            hostname
                .removePrefix("coder-jetbrains--")
                .removeSuffix("--${hostname.split("--").last()}")
        } else {
            name
        },
        hostname = hostname,
        projectPath = projectPath,
        ideProductCode = ideProductCode,
        ideBuildNumber = ideBuildNumber,
        idePathOnHost = idePathOnHost,
        downloadSource = downloadSource,
        // The deployment URL was added to replace storing the web terminal link
        // and config directory, as we can construct both from the URL and the
        // config directory might not always exist (for example, authentication
        // might happen with mTLS, and we can skip login which normally creates
        // the config directory).  For backwards compatibility with existing
        // entries, extract the URL from the config directory or host name.
        deploymentURL =
        if (deploymentURL.isNullOrBlank()) {
            if (!dir.isNullOrBlank()) {
                "https://${Path.of(dir).parent.name}"
            } else if (!hostname.isNullOrBlank()) {
                "https://${hostname.split("--").last()}"
            } else {
                deploymentURL
            }
        } else {
            deploymentURL
        },
        lastOpened = lastOpened,
    )
}

/**
 * Convert an IDE into parameters for making a connection to a project using
 * that IDE on a workspace.  Throw if invalid.
 */
fun IdeWithStatus.withWorkspaceProject(
    name: String,
    hostname: String,
    projectPath: String,
    deploymentURL: URL,
): WorkspaceProjectIDE {
    return WorkspaceProjectIDE(
        name = name,
        hostname = hostname,
        projectPath = projectPath,
        ideProductCode = this.product,
        ideBuildNumber = this.buildNumber,
        downloadSource = this.download?.link,
        idePathOnHost = this.pathOnHost,
        deploymentURL = deploymentURL,
        lastOpened = null,
    )
}

val remotePathRe = Regex("^[^(]+\\((.+)\\)$")

fun ShellArgument.RemotePath.toRawString(): String {
    // TODO: Surely there is an actual way to do this.
    val remotePath = flatten().toString()
    return remotePathRe.find(remotePath)?.groupValues?.get(1)
        ?: throw Exception("Got invalid path $remotePath")
}
