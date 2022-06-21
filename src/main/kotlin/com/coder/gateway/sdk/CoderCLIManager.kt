package com.coder.gateway.sdk

import com.intellij.openapi.diagnostic.Logger
import java.net.URL
import java.nio.file.Path

class CoderCLIManager(private val url: URL, buildVersion: String) {
    private val coderCLIDownloader = CoderCLIDownloader(buildVersion)

    fun download(): Path? {
        val os = getOS()
        val cliName = getCoderCLIForOS(os, getArch()) ?: return null
        val cliNameWitExt = if (os == OS.WINDOWS) "$cliName.exe" else cliName
        return coderCLIDownloader.downloadCLI(URL(url.protocol, url.host, url.port, "/bin/$cliNameWitExt"), cliName, if (os == OS.WINDOWS) "exe" else "")
    }

    private fun getCoderCLIForOS(os: OS?, arch: Arch?): String? {
        logger.info("Resolving coder cli for $os $arch")
        if (os == null) {
            return null
        }
        return when (os) {
            OS.WINDOWS -> when (arch) {
                Arch.AMD64 -> "coder-windows-amd64"
                Arch.ARM64 -> "coder-windows-arm64"
                else -> "coder-windows-amd64"
            }
            OS.LINUX -> when (arch) {
                Arch.AMD64 -> "coder-linux-amd64"
                Arch.ARM64 -> "coder-linux-arm64"
                Arch.ARMV7 -> "coder-linux-armv7"
                else -> "coder-linux-amd64"
            }
            OS.MAC -> when (arch) {
                Arch.AMD64 -> "coder-darwin-amd64"
                Arch.ARM64 -> "coder-darwin-arm64"
                else -> "coder-darwin-amd64"
            }
        }
    }

    companion object {
        val logger = Logger.getInstance(CoderCLIManager::class.java.simpleName)
    }
}