package com.coder.gateway.sdk

fun getOS(): OS? {
    val os = System.getProperty("os.name").toLowerCase()
    return when {
        os.contains("win", true) -> {
            OS.WINDOWS
        }
        os.contains("nix", true) || os.contains("nux", true) || os.contains("aix", true) -> {
            OS.LINUX
        }
        os.contains("mac", true) -> {
            OS.MAC
        }
        else -> null
    }
}

fun getArch(): Arch? {
    val arch = System.getProperty("os.arch").toLowerCase()
    return when {
        arch.contains("amd64", true) || arch.contains("x86_64", true) -> Arch.AMD64
        arch.contains("arm64", true) || arch.contains("aarch64", true) -> Arch.ARM64
        arch.contains("armv7", true) -> Arch.ARMV7
        else -> null
    }
}

enum class OS {
    WINDOWS, LINUX, MAC
}

enum class Arch {
    AMD64, ARM64, ARMV7
}