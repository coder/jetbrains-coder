package com.coder.gateway

import com.coder.gateway.models.RecentWorkspaceConnection
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

private const val CODER_WORKSPACE_HOSTNAME = "coder_workspace_hostname"
private const val TYPE = "type"
private const val VALUE_FOR_TYPE = "coder"
private const val PROJECT_PATH = "project_path"
private const val IDE_DOWNLOAD_LINK = "ide_download_link"
private const val IDE_PRODUCT_CODE = "ide_product_code"
private const val IDE_BUILD_NUMBER = "ide_build_number"
private const val IDE_PATH_ON_HOST = "ide_path_on_host"
private const val WEB_TERMINAL_LINK = "web_terminal_link"
private const val CONFIG_DIRECTORY = "config_directory"
private const val NAME = "name"

private val localTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm")

fun RecentWorkspaceConnection.toWorkspaceParams(): Map<String, String> {
    val map = mutableMapOf(
        TYPE to VALUE_FOR_TYPE,
        CODER_WORKSPACE_HOSTNAME to "${this.coderWorkspaceHostname}",
        PROJECT_PATH to this.projectPath!!,
        IDE_PRODUCT_CODE to IntelliJPlatformProduct.fromProductCode(this.ideProductCode!!)!!.productCode,
        IDE_BUILD_NUMBER to "${this.ideBuildNumber}",
        WEB_TERMINAL_LINK to "${this.webTerminalLink}",
        CONFIG_DIRECTORY to "${this.configDirectory}",
        NAME to "${this.name}"
    )

    if (!this.downloadSource.isNullOrBlank()) {
        map[IDE_DOWNLOAD_LINK] = this.downloadSource!!
    } else {
        map[IDE_PATH_ON_HOST] = this.idePathOnHost!!
    }
    return map
}

fun IdeWithStatus.toWorkspaceParams(): Map<String, String> {
    val workspaceParams = mutableMapOf(
        TYPE to VALUE_FOR_TYPE,
        IDE_PRODUCT_CODE to this.product.productCode,
        IDE_BUILD_NUMBER to this.buildNumber
    )

    if (this.download != null) {
        workspaceParams[IDE_DOWNLOAD_LINK] = this.download!!.link
    }

    if (!this.pathOnHost.isNullOrBlank()) {
        workspaceParams[IDE_PATH_ON_HOST] = this.pathOnHost!!
    }

    return workspaceParams
}

fun Map<String, String>.withWorkspaceHostname(hostname: String): Map<String, String> {
    val map = this.toMutableMap()
    map[CODER_WORKSPACE_HOSTNAME] = hostname
    return map
}

fun Map<String, String>.withProjectPath(projectPath: String): Map<String, String> {
    val map = this.toMutableMap()
    map[PROJECT_PATH] = projectPath
    return map
}

fun Map<String, String>.withWebTerminalLink(webTerminalLink: String): Map<String, String> {
    val map = this.toMutableMap()
    map[WEB_TERMINAL_LINK] = webTerminalLink
    return map
}

fun Map<String, String>.withConfigDirectory(dir: String): Map<String, String> {
    val map = this.toMutableMap()
    map[CONFIG_DIRECTORY] = dir
    return map
}

fun Map<String, String>.withName(name: String): Map<String, String> {
    val map = this.toMutableMap()
    map[NAME] = name
    return map
}


fun Map<String, String>.areCoderType(): Boolean {
    return this[TYPE] == VALUE_FOR_TYPE && !this[CODER_WORKSPACE_HOSTNAME].isNullOrBlank() && !this[PROJECT_PATH].isNullOrBlank()
}

fun Map<String, String>.toSshConfig(): SshConfig {
    return SshConfig(true).apply {
        setHost(this@toSshConfig.workspaceHostname())
        setUsername("coder")
        port = 22
        authType = AuthType.OPEN_SSH
    }
}

suspend fun Map<String, String>.toHostDeployInputs(): HostDeployInputs {
    return HostDeployInputs.FullySpecified(
        remoteProjectPath = this[PROJECT_PATH]!!,
        deployTarget = this.toDeployTargetInfo(),
        remoteInfo = HostDeployInputs.WithDeployedWorker(
            HighLevelHostAccessor.create(
                RemoteCredentialsHolder().apply {
                    setHost(this@toHostDeployInputs.workspaceHostname())
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

private fun Map<String, String>.toIdeInfo(): IdeInfo {
    return IdeInfo(
        product = IntelliJPlatformProduct.fromProductCode(this[IDE_PRODUCT_CODE]!!)!!,
        buildNumber = this[IDE_BUILD_NUMBER]!!
    )
}

private fun Map<String, String>.toDeployTargetInfo(): DeployTargetInfo {
    return if (!this[IDE_DOWNLOAD_LINK].isNullOrBlank()) DeployTargetInfo.DeployWithDownload(
        URI(this[IDE_DOWNLOAD_LINK]),
        null,
        this.toIdeInfo()
    )
    else DeployTargetInfo.NoDeploy(this[IDE_PATH_ON_HOST]!!, this.toIdeInfo())
}

private fun Map<String, String>.workspaceHostname() = this[CODER_WORKSPACE_HOSTNAME]!!
private fun Map<String, String>.projectPath() = this[PROJECT_PATH]!!

fun Map<String, String>.toRecentWorkspaceConnection(): RecentWorkspaceConnection {
    return if (!this[IDE_DOWNLOAD_LINK].isNullOrBlank()) RecentWorkspaceConnection(
        this.workspaceHostname(),
        this.projectPath(),
        localTimeFormatter.format(LocalDateTime.now()),
        this[IDE_PRODUCT_CODE]!!,
        this[IDE_BUILD_NUMBER]!!,
        this[IDE_DOWNLOAD_LINK]!!,
        null,
        this[WEB_TERMINAL_LINK]!!,
        this[CONFIG_DIRECTORY]!!,
        this[NAME]!!,
    ) else RecentWorkspaceConnection(
        this.workspaceHostname(),
        this.projectPath(),
        localTimeFormatter.format(LocalDateTime.now()),
        this[IDE_PRODUCT_CODE]!!,
        this[IDE_BUILD_NUMBER]!!,
        null,
        this[IDE_PATH_ON_HOST],
        this[WEB_TERMINAL_LINK]!!,
        this[CONFIG_DIRECTORY]!!,
        this[NAME]!!,
    )
}
