package com.coder.gateway.sdk

import com.coder.gateway.sdk.ex.AuthenticationResponseException
import com.coder.gateway.sdk.v2.models.User
import com.coder.gateway.services.CoderSettingsService
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.net.HttpConfigurable
import java.net.URL

@Service(Service.Level.APP)
class CoderRestClientService {
    var isReady: Boolean = false
        private set
    lateinit var me: User
    lateinit var buildVersion: String
    lateinit var client: CoderRestClient

    /**
     * This must be called before anything else. It will authenticate and load
     * information about the current user and the build version.
     *
     * @throws [AuthenticationResponseException] if authentication failed.
     */
    fun initClientSession(url: URL, token: String): User {
        client = DefaultCoderRestClient(url, token)
        me = client.me()
        buildVersion = client.buildInfo().version
        isReady = true
        return me
    }
}

/**
 * A client instance that hooks into global JetBrains services for default
 * settings.  Exists only so we can use the base client in tests.
 */
class DefaultCoderRestClient(url: URL, token: String) : CoderRestClient(url, token,
    service<CoderSettingsService>(),
    ProxyValues(HttpConfigurable.getInstance().proxyLogin,
        HttpConfigurable.getInstance().plainProxyPassword,
        HttpConfigurable.getInstance().PROXY_AUTHENTICATION,
        HttpConfigurable.getInstance().onlyBySettingsSelector),
    PluginManagerCore.getPlugin(PluginId.getId("com.coder.gateway"))!!.version)

