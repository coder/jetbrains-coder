package com.coder.gateway.sdk

import com.intellij.openapi.diagnostic.Logger
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths

class CoderCLIManager(url: URL, buildVersion: String) {
    var remoteCliPath: URL
    var localCliPath: Path

    init {
        val os = getOS()
        val cliName = getCoderCLIForOS(os, getArch())
        val cliNameWithExt = if (os == OS.WINDOWS) "$cliName.exe" else cliName
        val filename = if (os == OS.WINDOWS) "${cliName}-${buildVersion}.exe" else "${cliName}-${buildVersion}"

        remoteCliPath = URL(url.protocol, url.host, url.port, "/bin/$cliNameWithExt")
        localCliPath = Paths.get(System.getProperty("java.io.tmpdir"), filename)
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