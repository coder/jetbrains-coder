package com.coder.gateway.services

import com.coder.gateway.sdk.CoderRestClient
import com.coder.gateway.sdk.ProxyValues
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.net.HttpConfigurable
import okhttp3.OkHttpClient
import java.net.URL

/**
 * A client instance that hooks into global JetBrains services for default
 * settings.
 */
class CoderRestClientService(url: URL, token: String?, httpClient:OkHttpClient? = null) : CoderRestClient(url, token,
    service<CoderSettingsService>(),
    ProxyValues(HttpConfigurable.getInstance().proxyLogin,
        HttpConfigurable.getInstance().plainProxyPassword,
        HttpConfigurable.getInstance().PROXY_AUTHENTICATION,
        HttpConfigurable.getInstance().onlyBySettingsSelector),
    PluginManagerCore.getPlugin(PluginId.getId("com.coder.gateway"))!!.version,
    httpClient)
