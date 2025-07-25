package com.coder.gateway.util

import java.util.Locale

fun getOS(): OS? = OS.from(System.getProperty("os.name"))

fun getArch(): Arch? = Arch.from(System.getProperty("os.arch")?.lowercase(Locale.getDefault()))

enum class OS {
    WINDOWS,
    LINUX,
    MAC;

    companion object {
        fun from(os: String?): OS? = when {
            os.isNullOrBlank() -> null
            os.contains("win", true) -> {
                WINDOWS
            }

            os.contains("nix", true) || os.contains("nux", true) || os.contains("aix", true) -> {
                LINUX
            }

            os.contains("mac", true) || os.contains("darwin", true) -> {
                MAC
            }

            else -> null
        }
    }
}

enum class Arch {
    AMD64,
    ARM64,
    ARMV7,
    ;

    companion object {
        fun from(arch: String?): Arch? = when {
            arch.isNullOrBlank() -> null
            arch.contains("amd64", true) || arch.contains("x86_64", true) -> AMD64
            arch.contains("arm64", true) || arch.contains("aarch64", true) -> ARM64
            arch.contains("armv7", true) -> ARMV7
            else -> null
        }
    }
}
