package com.coder.gateway.sdk

import com.intellij.openapi.diagnostic.Logger
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

class CoderCLIManager(url: URL, buildVersion: String) {
    var remoteCli: URL
    var localCli: Path
    private var cliNamePrefix: String
    private var tmpDir: String
    private var cliFileName: String

    init {
        val os = getOS()
        cliNamePrefix = getCoderCLIForOS(os, getArch())
        val cliNameWithExt = if (os == OS.WINDOWS) "$cliNamePrefix.exe" else cliNamePrefix
        cliFileName = if (os == OS.WINDOWS) "${cliNamePrefix}-${buildVersion}.exe" else "${cliNamePrefix}-${buildVersion}"

        remoteCli = URL(url.protocol, url.host, url.port, "/bin/$cliNameWithExt")
        tmpDir = System.getProperty("java.io.tmpdir")
        localCli = Paths.get(tmpDir, cliFileName)
    }

    private fun getCoderCLIForOS(os: OS?, arch: Arch?): String {
        logger.info("Resolving coder cli for $os $arch")
        if (os == null) {
            logger.error("Could not resolve client OS and architecture, defaulting to WINDOWS AMD64")
            return "coder-windows-amd64"
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

    fun downloadCLI(): Boolean {
        if (Files.exists(localCli)) {
            logger.info("${localCli.toAbsolutePath()} already exists, skipping download")
            return false
        }
        logger.info("Starting Coder CLI download to ${localCli.toAbsolutePath()}")
        remoteCli.openStream().use {
            Files.copy(it as InputStream, localCli, StandardCopyOption.REPLACE_EXISTING)
        }
        return true
    }

    fun removeOldCli() {
        Files.walk(Path.of(tmpDir)).sorted().map { it.toFile() }.filter { it.name.contains(cliNamePrefix) && !it.name.contains(cliFileName) }.forEach {
            logger.info("Removing $it because it is an old coder cli")
            it.delete()
        }
    }

    companion object {
        val logger = Logger.getInstance(CoderCLIManager::class.java.simpleName)
    }
}