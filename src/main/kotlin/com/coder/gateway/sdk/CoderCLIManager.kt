package com.coder.gateway.sdk

import java.net.URL
import java.nio.file.Path

class CoderCLIManager(private val url: URL) {
    private val coderCLIDownloader = CoderCLIDownloader()

    fun download(): Path? {
        val os = getOS()
        val cliName = getCoderCLIForOS(os, getArch()) ?: return null
        val cliNameWitExt = if (os == OS.WINDOWS) "$cliName.exe" else cliName
        return coderCLIDownloader.downloadCLI(URL(url.protocol, url.host, url.port, "/bin/$cliNameWitExt"), cliName, if (os == OS.WINDOWS) ".exe" else "")
    }

    private fun getCoderCLIForOS(os: OS?, arch: Arch?): String? {
        if (os == null || arch == null) {
            return null
        }
        return when (os) {
            OS.WINDOWS -> when (arch) {
                Arch.amd64 -> "coder-windows-amd64"
                Arch.arm64 -> "coder-windows-arm64"
                else -> null
            }
            OS.LINUX -> when (arch) {
                Arch.amd64 -> "coder-linux-amd64"
                Arch.arm64 -> "coder-linux-arm64"
                Arch.armv7 -> "coder-linux-armv7"
            }
            OS.MAC -> when (arch) {
                Arch.amd64 -> "coder-darwin-amd64"
                Arch.arm64 -> "coder-darwin-arm64"
                else -> null
            }
        }
    }

    private fun getOS(): OS? {
        val os = System.getProperty("os.name").toLowerCase()
        return when {
            os.contains("win") -> {
                OS.WINDOWS
            }
            os.contains("nix") || os.contains("nux") || os.contains("aix") -> {
                OS.LINUX
            }
            os.contains("mac") -> {
                OS.MAC
            }
            else -> null
        }
    }

    private fun getArch(): Arch? {
        val arch = System.getProperty("os.arch").toLowerCase()
        return when {
            arch.contains("amd64") -> Arch.amd64
            arch.contains("arm64") -> Arch.arm64
            arch.contains("armv7") -> Arch.armv7
            else -> null
        }
    }
}

enum class OS {
    WINDOWS, LINUX, MAC
}

enum class Arch {
    amd64, arm64, armv7
}