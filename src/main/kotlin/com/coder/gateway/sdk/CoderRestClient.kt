package com.coder.gateway.sdk

import com.coder.gateway.services.CoderSettingsService
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.net.HttpConfigurable
import java.net.URL

/**
 * A client instance that hooks into global JetBrains services for default
 * settings.  Exists only so we can use the base client in tests.
 */
class CoderRestClient(url: URL, token: String) : BaseCoderRestClient(url, token,
    service<CoderSettingsService>(),
    ProxyValues(HttpConfigurable.getInstance().proxyLogin,
        HttpConfigurable.getInstance().plainProxyPassword,
        HttpConfigurable.getInstance().PROXY_AUTHENTICATION,
        HttpConfigurable.getInstance().onlyBySettingsSelector),
    PluginManagerCore.getPlugin(PluginId.getId("com.coder.gateway"))!!.version)
