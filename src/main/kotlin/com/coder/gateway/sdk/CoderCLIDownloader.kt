package com.coder.gateway.sdk

import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.logging.Logger

class CoderCLIDownloader {

    fun downloadCLI(url: URL, outputName: String, ext: String): Path {
        val tempPath: Path = Files.createTempFile(outputName, ext)
        logger.info("Downloading $url to $tempPath")
        url.openStream().use {
            Files.copy(it as InputStream, tempPath, StandardCopyOption.REPLACE_EXISTING)
        }
        return tempPath
    }

    companion object {
        val logger = Logger.getLogger(CoderCLIDownloader::class.java.simpleName)
    }
}