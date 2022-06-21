package com.coder.gateway.sdk

import com.intellij.openapi.diagnostic.Logger
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

class CoderCLIDownloader(private val buildVersion: String) {

    fun downloadCLI(url: URL, outputName: String, ext: String): Path {
        val filename = if (ext.isBlank()) "${outputName}-$buildVersion" else "${outputName}-${buildVersion}.${ext}"
        val cliPath = Paths.get(System.getProperty("java.io.tmpdir"), filename)
        if (Files.exists(cliPath)) {
            logger.info("${cliPath.toAbsolutePath()} already exists, skipping download")
            return cliPath
        }
        logger.info("Starting Coder CLI download to ${cliPath.toAbsolutePath()}")
        url.openStream().use {
            Files.copy(it as InputStream, cliPath, StandardCopyOption.REPLACE_EXISTING)
        }
        return cliPath
    }

    companion object {
        val logger = Logger.getInstance(CoderCLIDownloader::class.java.simpleName)
    }
}