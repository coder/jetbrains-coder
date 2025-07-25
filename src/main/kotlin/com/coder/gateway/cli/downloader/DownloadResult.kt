package com.coder.gateway.cli.downloader

import java.net.URL
import java.nio.file.Path


/**
 * Result of a download operation
 */
sealed class DownloadResult {
    object Skipped : DownloadResult()
    object NotFound : DownloadResult()
    data class Downloaded(val source: URL, val dst: Path) : DownloadResult()
    data class Failed(val error: Exception) : DownloadResult()

    fun isSkipped(): Boolean = this is Skipped

    fun isNotFound(): Boolean = this is NotFound

    fun isFailed(): Boolean = this is Failed

    fun isNotDownloaded(): Boolean = this !is Downloaded
}