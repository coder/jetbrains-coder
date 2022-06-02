package com.coder.gateway.sdk

import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class CoderCLIDownloader {

    fun downloadCLI(url: URL, outputName: String, ext: String): Path {
        val tempPath: Path = Files.createTempFile(outputName, ext)
        url.openStream().use {
            Files.copy(it as InputStream, tempPath, StandardCopyOption.REPLACE_EXISTING)
        }
        return tempPath
    }
}