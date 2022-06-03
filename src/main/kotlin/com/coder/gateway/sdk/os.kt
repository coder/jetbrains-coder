package com.coder.gateway.sdk

fun getOS(): OS? {
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

fun getArch(): Arch? {
    val arch = System.getProperty("os.arch").toLowerCase()
    return when {
        arch.contains("amd64") -> Arch.amd64
        arch.contains("arm64") -> Arch.arm64
        arch.contains("armv7") -> Arch.armv7
        else -> null
    }
}

enum class OS {
    WINDOWS, LINUX, MAC
}

enum class Arch {
    amd64, arm64, armv7
}