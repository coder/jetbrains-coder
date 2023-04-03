package com.coder.gateway.sdk

import com.intellij.openapi.diagnostic.Logger
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

/**
 * Manage the CLI for a single deployment.
 */
class CoderCLIManager(deployment: URL, buildVersion: String) {
    private var remoteBinaryUrl: URL
    var localBinaryPath: Path
    private var binaryNamePrefix: String
    private var destinationDir: Path
    private var localBinaryName: String

    init {
        // TODO: Should use a persistent path to avoid needing to download on
        //       each restart.
        destinationDir = Path.of(System.getProperty("java.io.tmpdir"))
            .resolve("coder-gateway").resolve(deployment.host)
        val os = getOS()
        binaryNamePrefix = getCoderCLIForOS(os, getArch())
        val binaryName = if (os == OS.WINDOWS) "$binaryNamePrefix.exe" else binaryNamePrefix
        localBinaryName =
            if (os == OS.WINDOWS) "${binaryNamePrefix}-${buildVersion}.exe" else "${binaryNamePrefix}-${buildVersion}"
        remoteBinaryUrl = URL(
            deployment.protocol,
            deployment.host,
            deployment.port,
            "/bin/$binaryName"
        )
        localBinaryPath = destinationDir.resolve(localBinaryName)
    }

    /**
     * Return the name of the binary (sans extension) for the provided OS and
     * architecture.
     */
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

    /**
     * Download the CLI from the deployment if necessary.
     */
    fun downloadCLI(): Boolean {
        Files.createDirectories(destinationDir)
        try {
            logger.info("Downloading Coder CLI to ${localBinaryPath.toAbsolutePath()}")
            remoteBinaryUrl.openStream().use {
                Files.copy(it as InputStream, localBinaryPath)
            }
        } catch (e: java.nio.file.FileAlreadyExistsException) {
            // This relies on the provided build version being the latest.  It
            // must be freshly fetched immediately before downloading.
            // TODO: Use etags instead?
            logger.info("${localBinaryPath.toAbsolutePath()} already exists, skipping download")
            return false
        }
        if (getOS() != OS.WINDOWS) {
            Files.setPosixFilePermissions(
                localBinaryPath,
                PosixFilePermissions.fromString("rwxr-x---")
            )
        }
        return true
    }

    /**
     * Remove all versions of the CLI for this deployment that do not match the
     * current build version.
     */
    fun removeOldCli() {
        if (Files.isReadable(destinationDir)) {
            Files.walk(destinationDir, 1).use {
                it.sorted().map { pt -> pt.toFile() }
                    .filter { fl -> fl.name.contains(binaryNamePrefix) && fl.name != localBinaryName }
                    .forEach { fl ->
                        logger.info("Removing $fl because it is an old version")
                        fl.delete()
                    }
            }
        }
    }

    companion object {
        val logger = Logger.getInstance(CoderCLIManager::class.java.simpleName)
    }
}
