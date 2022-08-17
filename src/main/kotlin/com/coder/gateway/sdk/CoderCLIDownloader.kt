package com.coder.gateway.sdk

import com.intellij.openapi.diagnostic.Logger
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class CoderCLIDownloader {

    fun downloadCLI(url: URL, cliPath: Path): Boolean {
        if (Files.exists(cliPath)) {
            logger.info("${cliPath.toAbsolutePath()} already exists, skipping download")
            return false
        }
        logger.info("Starting Coder CLI download to ${cliPath.toAbsolutePath()}")
        url.openStream().use {
            Files.copy(it as InputStream, cliPath, StandardCopyOption.REPLACE_EXISTING)
        }
        return true
    }

    companion object {
        val logger = Logger.getInstance(CoderCLIDownloader::class.java.simpleName)
    }
}