package com.coder.gateway.models

import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.gateway.ssh.AvailableIde
import com.jetbrains.gateway.ssh.IdeStatus
import com.jetbrains.gateway.ssh.IdeWithStatus
import com.jetbrains.gateway.ssh.InstalledIdeUIEx
import com.jetbrains.gateway.ssh.IntelliJPlatformProduct
import com.jetbrains.gateway.ssh.ReleaseType
import com.jetbrains.gateway.ssh.deploy.ShellArgument
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.name

private val NON_STABLE_RELEASE_TYPES = setOf("EAP", "RC", "NIGHTLY", "PREVIEW")

/**
 * Validated parameters for downloading and opening a project using an IDE on a
 * workspace.
 */
data class WorkspaceProjectIDE(
    // Either `workspace.agent` for old connections or `user/workspace.agent`
    // for new connections.
    val name: String,
    val hostname: String,
    val projectPath: String,
    val ideProduct: IntelliJPlatformProduct,
    val ideBuildNumber: String,
    // One of these must exist; enforced by the constructor.
    var idePathOnHost: String?,
    val downloadSource: String?,
    // These are used in the recent connections window.
    val deploymentURL: URL,
    var lastOpened: String?, // Null if never opened.
) {
    val ideName = "${ideProduct.productCode}-$ideBuildNumber"

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
     * Convert parameters into a recent workspace connection (for storage).
     */
    fun toRecentWorkspaceConnection(): RecentWorkspaceConnection = RecentWorkspaceConnection(
        name = name,
        coderWorkspaceHostname = hostname,
        projectPath = projectPath,
        ideProductCode = ideProduct.productCode,
        ideBuildNumber = ideBuildNumber,
        downloadSource = downloadSource,
        idePathOnHost = idePathOnHost,
        deploymentURL = deploymentURL.toString(),
        lastOpened = lastOpened,
    )

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
                ideProduct = IntelliJPlatformProduct.fromProductCode(ideProductCode)
                    ?: throw Exception("invalid product code"),
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
): WorkspaceProjectIDE = WorkspaceProjectIDE(
    name = name,
    hostname = hostname,
    projectPath = projectPath,
    ideProduct = this.product,
    ideBuildNumber = this.buildNumber,
    downloadSource = this.download?.link,
    idePathOnHost = this.pathOnHost,
    deploymentURL = deploymentURL,
    lastOpened = null,
)

/**
 * Convert an available IDE to an IDE with status.
 */
fun AvailableIde.toIdeWithStatus(): IdeWithStatus = IdeWithStatus(
    product = product,
    buildNumber = buildNumber,
    status = IdeStatus.DOWNLOAD,
    download = download,
    pathOnHost = null,
    presentableVersion = presentableVersion,
    remoteDevType = remoteDevType,
)

/**
 * Returns a list of installed IDEs that don't have a RELEASED version available for download.
 * Typically, installed EAP, RC, nightly or preview builds should be superseded by released versions.
 */
fun List<InstalledIdeUIEx>.filterOutAvailableReleasedIdes(availableIde: List<AvailableIde>): List<InstalledIdeUIEx> {
    val availableReleasedByProductCode = availableIde
        .filter { it.releaseType == ReleaseType.RELEASE }
        .groupBy { it.product.productCode }
    val result = mutableListOf<InstalledIdeUIEx>()

    this.forEach { installedIde ->
        // installed IDEs have the release type embedded in the presentable version
        // which is a string in the form: 2024.2.4 NIGHTLY
        if (NON_STABLE_RELEASE_TYPES.any { it in installedIde.presentableVersion }) {
            // we can show the installed IDe if there isn't a higher released version available for download
            if (installedIde.isSNotSupersededBy(availableReleasedByProductCode[installedIde.product.productCode])) {
                result.add(installedIde)
            }
        } else {
            result.add(installedIde)
        }
    }

    return result
}

private fun InstalledIdeUIEx.isSNotSupersededBy(availableIdes: List<AvailableIde>?): Boolean {
    if (availableIdes.isNullOrEmpty()) {
        return true
    }
    return !availableIdes.any { it.buildNumber >= this.buildNumber }
}

/**
 * Convert an installed IDE to an IDE with status.
 */
fun InstalledIdeUIEx.toIdeWithStatus(): IdeWithStatus = IdeWithStatus(
    product = product,
    buildNumber = buildNumber,
    status = IdeStatus.ALREADY_INSTALLED,
    download = null,
    pathOnHost = pathToIde,
    presentableVersion = presentableVersion,
    remoteDevType = remoteDevType,
)

val remotePathRe = Regex("^[^(]+\\((.+)\\)$")

fun ShellArgument.RemotePath.toRawString(): String {
    // TODO: Surely there is an actual way to do this.
    val remotePath = flatten().toString()
    return remotePathRe.find(remotePath)?.groupValues?.get(1)
        ?: throw Exception("Got invalid path $remotePath")
}
