package com.coder.gateway.sdk

import com.coder.gateway.icons.CoderIcons
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.IconUtil
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import javax.swing.Icon
import javax.swing.ImageIcon

@Service(Service.Level.APP)
class TemplateIconDownloader {
    private val coderClient: CoderRestClientService = service()
    private val httpClient = OkHttpClient.Builder().build()
    fun load(url: String, templateName: String): Icon {
        if (url.isNullOrBlank()) {
            return CoderIcons.UNKNOWN
        }
        var byteArray: ByteArray? = null

        if (url.startsWith("http")) {
            byteArray = if (url.contains(coderClient.coderURL.host)) {
                coderClient.getImageIcon(url.toURL())
            } else {
                getExternalImageIcon(url.toURL())
            }
        } else if (url.contains(coderClient.coderURL.host)) {
            byteArray = coderClient.getImageIcon(coderClient.coderURL.withPath(url))
        }

        if (byteArray != null) {
            return IconUtil.resizeSquared(ImageIcon(byteArray), 32)
        }

        return CoderIcons.UNKNOWN
    }

    private fun getExternalImageIcon(url: URL): ByteArray? {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return null
            }

            response.body!!.byteStream().use {
                return it.readAllBytes()
            }
        }
    }
}