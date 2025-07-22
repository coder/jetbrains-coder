package com.coder.gateway.cli.downloader

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.HeaderMap
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Retrofit API for downloading CLI
 */
interface CoderDownloadApi {
    @GET
    @Streaming
    suspend fun downloadCli(
        @Url url: String,
        @Header("If-None-Match") eTag: String? = null,
        @HeaderMap headers: Map<String, String> = emptyMap(),
        @Header("Accept-Encoding") acceptEncoding: String = "gzip",
    ): Response<ResponseBody>

    @GET
    suspend fun downloadSignature(
        @Url url: String,
        @HeaderMap headers: Map<String, String> = emptyMap()
    ): Response<ResponseBody>
}