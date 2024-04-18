package com.coder.gateway.models

import com.intellij.remote.AuthType
import com.intellij.remote.RemoteCredentialsHolder
import com.intellij.ssh.config.unified.SshConfig
import com.jetbrains.gateway.ssh.HighLevelHostAccessor
import com.jetbrains.gateway.ssh.HostDeployInputs
import com.jetbrains.gateway.ssh.IdeInfo
import com.jetbrains.gateway.ssh.IdeWithStatus
import com.jetbrains.gateway.ssh.IntelliJPlatformProduct
import com.jetbrains.gateway.ssh.deploy.DeployTargetInfo
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val localTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm")

/**
 * Validated parameters for downloading (if necessary) and opening a project
 * using an IDE on a workspace.
 */
@Suppress("UnstableApiUsage")
class WorkspaceProjectIDE(
    val name: String?,
    val hostname: String,
    val projectPath: String,
    val ideProductCode: IntelliJPlatformProduct,
    val ideBuildNumber: String,

    // Either a path or URL.
    val ideSource: String,
    val isDownloadSource: Boolean,

    // These are used in the recent connections window.
    val webTerminalLink: String?,
    val configDirectory: String?,
    var lastOpened: String?,
) {
    /**
     * Return accessor for deploying the IDE.
     */
    suspend fun toHostDeployInputs(): HostDeployInputs {
        this.lastOpened = localTimeFormatter.format(LocalDateTime.now())
        return HostDeployInputs.FullySpecified(
            remoteProjectPath = projectPath,
            deployTarget = toDeployTargetInfo(),
            remoteInfo = HostDeployInputs.WithDeployedWorker(
                HighLevelHostAccessor.create(
                    RemoteCredentialsHolder().apply {
                        setHost(hostname)
                        userName = "coder"
                        port = 22
                        authType = AuthType.OPEN_SSH
                    },
                    true
                ),
                HostDeployInputs.WithHostInfo(this.toSshConfig())
            )
        )
    }

    private fun toSshConfig(): SshConfig {
        return SshConfig(true).apply {
            setHost(hostname)
            setUsername("coder")
            port = 22
            authType = AuthType.OPEN_SSH
        }
    }

    private fun toDeployTargetInfo(): DeployTargetInfo {
        return if (this.isDownloadSource) DeployTargetInfo.DeployWithDownload(
            URI(this.ideSource),
            null,
            this.toIdeInfo()
        )
        else DeployTargetInfo.NoDeploy(this.ideSource, this.toIdeInfo())
    }

    private fun toIdeInfo(): IdeInfo {
        return IdeInfo(
            product = this.ideProductCode,
            buildNumber = this.ideBuildNumber,
        )
    }

    /**
     * Convert parameters into a recent workspace connection (for storage).
     */
    fun toRecentWorkspaceConnection(): RecentWorkspaceConnection {
        return RecentWorkspaceConnection(
            name = this.name,
            coderWorkspaceHostname = this.hostname,
            projectPath = this.projectPath,
            ideProductCode = this.ideProductCode.productCode,
            ideBuildNumber = this.ideBuildNumber,
            downloadSource = if (this.isDownloadSource) this.ideSource else "",
            idePathOnHost = if (this.isDownloadSource) "" else this.ideSource,
            lastOpened = this.lastOpened,
            webTerminalLink = this.webTerminalLink,
            configDirectory = this.configDirectory,
        )
    }

    companion object {
        /**
         * Create from unvalidated user inputs.
         */
        @JvmStatic
        fun fromInputs(
            name: String?,
            hostname: String?,
            projectPath: String?,
            lastOpened: String?,
            ideProductCode: String?,
            ideBuildNumber: String?,
            downloadSource: String?,
            idePathOnHost: String?,
            webTerminalLink: String?,
            configDirectory: String?,
        ): WorkspaceProjectIDE {
            val ideSource = if (idePathOnHost.isNullOrBlank()) downloadSource else idePathOnHost
            if (hostname.isNullOrBlank()) {
                throw Error("host name is missing")
            } else if (projectPath.isNullOrBlank()) {
                throw Error("project path is missing")
            } else if (ideProductCode.isNullOrBlank()) {
                throw Error("ide product code is missing")
            } else if (ideBuildNumber.isNullOrBlank()) {
                throw Error("ide build number is missing")
            } else if (ideSource.isNullOrBlank()) {
                throw Error("one of path or download is required")
            }

            return WorkspaceProjectIDE(
                name = name,
                hostname = hostname,
                projectPath = projectPath,
                ideProductCode = IntelliJPlatformProduct.fromProductCode(ideProductCode) ?: throw Error("invalid product code"),
                ideBuildNumber = ideBuildNumber,
                webTerminalLink = webTerminalLink,
                configDirectory = configDirectory,

                ideSource = ideSource,
                isDownloadSource = idePathOnHost.isNullOrBlank(),
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
    return WorkspaceProjectIDE.fromInputs(
        name = name,
        hostname = coderWorkspaceHostname,
        projectPath = projectPath,
        ideProductCode = ideProductCode,
        ideBuildNumber = ideBuildNumber,
        webTerminalLink = webTerminalLink,
        configDirectory = configDirectory,
        idePathOnHost = idePathOnHost,
        downloadSource = downloadSource,
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
    webTerminalLink: String,
    configDirectory: String,
): WorkspaceProjectIDE {
    val download = this.download
    val pathOnHost = this.pathOnHost
    val ideSource = if (pathOnHost.isNullOrBlank()) download?.link else pathOnHost
    if (ideSource.isNullOrBlank()) {
        throw Error("one of path or download is required")
    }
    return WorkspaceProjectIDE(
        name = name,
        hostname = hostname,
        projectPath = projectPath,
        ideProductCode = this.product,
        ideBuildNumber = this.buildNumber,
        webTerminalLink = webTerminalLink,
        configDirectory = configDirectory,

        ideSource = ideSource,
        isDownloadSource = pathOnHost.isNullOrBlank(),
        lastOpened = null,
    )
}
