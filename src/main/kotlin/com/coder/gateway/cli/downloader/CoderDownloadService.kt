package com.coder.gateway.cli.downloader

import com.coder.gateway.cli.ex.ResponseException
import com.coder.gateway.settings.CoderSettings
import com.coder.gateway.util.OS
import com.coder.gateway.util.SemVer
import com.coder.gateway.util.getHeaders
import com.coder.gateway.util.getOS
import com.coder.gateway.util.sha1
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.FileInputStream
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_NOT_MODIFIED
import java.net.HttpURLConnection.HTTP_OK
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.zip.GZIPInputStream
import kotlin.io.path.name
import kotlin.io.path.notExists

/**
 * Handles the download steps of Coder CLI
 */
class CoderDownloadService(
    private val settings: CoderSettings,
    private val downloadApi: CoderDownloadApi,
    private val deploymentUrl: URL,
    forceDownloadToData: Boolean,
) {
    private val remoteBinaryURL: URL = settings.binSource(deploymentUrl)
    private val cliFinalDst: Path = settings.binPath(deploymentUrl, forceDownloadToData)
    private val cliTempDst: Path = cliFinalDst.resolveSibling("${cliFinalDst.name}.tmp")

    suspend fun downloadCli(buildVersion: String, showTextProgress: ((t: String) -> Unit)? = null): DownloadResult {
        val eTag = calculateLocalETag()
        if (eTag != null) {
            logger.info("Found existing binary at $cliFinalDst; calculated hash as $eTag")
        }
        val response = downloadApi.downloadCli(
            url = remoteBinaryURL.toString(),
            eTag = eTag?.let { "\"$it\"" },
            headers = getRequestHeaders()
        )

        return when (response.code()) {
            HTTP_OK -> {
                logger.info("Downloading binary to temporary $cliTempDst")
                response.saveToDisk(cliTempDst, showTextProgress, buildVersion)?.makeExecutable()
                DownloadResult.Downloaded(remoteBinaryURL, cliTempDst)
            }

            HTTP_NOT_MODIFIED -> {
                logger.info("Using cached binary at $cliFinalDst")
                showTextProgress?.invoke("Using cached binary")
                DownloadResult.Skipped
            }

            else -> {
                throw ResponseException(
                    "Unexpected response from $remoteBinaryURL",
                    response.code()
                )
            }
        }
    }

    /**
     * Renames the temporary binary file to its original destination name.
     * The implementation will override sibling file that has the original
     * destination name.
     */
    suspend fun commit(): Path {
        return withContext(Dispatchers.IO) {
            logger.info("Renaming binary from $cliTempDst to $cliFinalDst")
            Files.move(cliTempDst, cliFinalDst, StandardCopyOption.REPLACE_EXISTING)
            cliFinalDst.makeExecutable()
            cliFinalDst
        }
    }

    /**
     * Cleans up the temporary binary file if it exists.
     */
    suspend fun cleanup() {
        withContext(Dispatchers.IO) {
            runCatching { Files.deleteIfExists(cliTempDst) }
                .onFailure { ex ->
                    logger.warn("Failed to delete temporary CLI file: $cliTempDst", ex)
                }
        }
    }

    private fun calculateLocalETag(): String? {
        return try {
            if (cliFinalDst.notExists()) {
                return null
            }
            sha1(FileInputStream(cliFinalDst.toFile()))
        } catch (e: Exception) {
            logger.warn("Unable to calculate hash for $cliFinalDst", e)
            null
        }
    }

    private fun getRequestHeaders(): Map<String, String> {
        return if (settings.headerCommand.isBlank()) {
            emptyMap()
        } else {
            getHeaders(deploymentUrl, settings.headerCommand)
        }
    }

    private fun Response<ResponseBody>.saveToDisk(
        localPath: Path,
        showTextProgress: ((t: String) -> Unit)? = null,
        buildVersion: String? = null
    ): Path? {
        val responseBody = this.body() ?: return null
        Files.deleteIfExists(localPath)
        Files.createDirectories(localPath.parent)

        val outputStream = Files.newOutputStream(
            localPath,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
        val contentEncoding = this.headers()["Content-Encoding"]
        val sourceStream = if (contentEncoding?.contains("gzip", ignoreCase = true) == true) {
            GZIPInputStream(responseBody.byteStream())
        } else {
            responseBody.byteStream()
        }

        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytesRead: Int
        var totalRead = 0L
        // local path is a temporary filename, reporting the progress with the real name
        val binaryName = localPath.name.removeSuffix(".tmp")
        sourceStream.use { source ->
            outputStream.use { sink ->
                while (source.read(buffer).also { bytesRead = it } != -1) {
                    sink.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    val prettyBuildVersion = buildVersion ?: ""
                    showTextProgress?.invoke(
                        "$binaryName $prettyBuildVersion - ${totalRead.toHumanReadableSize()} downloaded"
                    )
                }
            }
        }
        return cliFinalDst
    }


    private fun Path.makeExecutable() {
        if (getOS() != OS.WINDOWS) {
            logger.info("Making $this executable...")
            this.toFile().setExecutable(true)
        }
    }

    private fun Long.toHumanReadableSize(): String {
        if (this < 1024) return "$this B"

        val kb = this / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)

        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)

        val gb = mb / 1024.0
        return String.format("%.1f GB", gb)
    }

    suspend fun downloadSignature(showTextProgress: ((t: String) -> Unit)? = null): DownloadResult {
        return downloadSignature(remoteBinaryURL, showTextProgress, getRequestHeaders())
    }

    private suspend fun downloadSignature(
        url: URL,
        showTextProgress: ((t: String) -> Unit)? = null,
        headers: Map<String, String> = emptyMap()
    ): DownloadResult {
        val signatureURL = url.toURI().resolve(settings.defaultSignatureNameByOsAndArch).toURL()
        val localSignaturePath = cliFinalDst.parent.resolve(settings.defaultSignatureNameByOsAndArch)
        logger.info("Downloading signature from $signatureURL")

        val response = downloadApi.downloadSignature(
            url = signatureURL.toString(),
            headers = headers
        )

        return when (response.code()) {
            HTTP_OK -> {
                response.saveToDisk(localSignaturePath, showTextProgress)
                DownloadResult.Downloaded(signatureURL, localSignaturePath)
            }

            HTTP_NOT_FOUND -> {
                logger.warn("Signature file not found at $signatureURL")
                DownloadResult.NotFound
            }

            else -> {
                DownloadResult.Failed(
                    ResponseException(
                        "Failed to download signature from $signatureURL",
                        response.code()
                    )
                )
            }
        }

    }

    suspend fun downloadReleasesSignature(
        buildVersion: String,
        showTextProgress: ((t: String) -> Unit)? = null
    ): DownloadResult {
        val semVer = SemVer.parse(buildVersion)
        return downloadSignature(
            URI.create("https://releases.coder.com/coder-cli/${semVer.major}.${semVer.minor}.${semVer.patch}/").toURL(),
            showTextProgress
        )
    }

    companion object {
        val logger = Logger.getInstance(CoderDownloadService::class.java.simpleName)
    }
}