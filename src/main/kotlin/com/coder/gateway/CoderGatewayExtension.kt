package com.coder.gateway

import com.jetbrains.toolbox.api.core.PluginSecretStore
import com.jetbrains.toolbox.api.core.PluginSettingsStore
import com.jetbrains.toolbox.api.core.ServiceLocator
import com.jetbrains.toolbox.api.remoteDev.RemoteDevExtension
import com.jetbrains.toolbox.api.remoteDev.RemoteEnvironmentConsumer
import com.jetbrains.toolbox.api.remoteDev.RemoteProvider
import com.jetbrains.toolbox.api.ui.ToolboxUi
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient

/**
 * Entry point into the extension.
 */
class CoderGatewayExtension : RemoteDevExtension {
    // All services must be passed in here and threaded as necessary.
    override fun createRemoteProviderPluginInstance(serviceLocator: ServiceLocator): RemoteProvider {
        return CoderRemoteProvider(
            serviceLocator.getService(OkHttpClient::class.java),
            serviceLocator.getService(RemoteEnvironmentConsumer::class.java),
            serviceLocator.getService(CoroutineScope::class.java),
            serviceLocator.getService(ToolboxUi::class.java),
            serviceLocator.getService(PluginSettingsStore::class.java),
            serviceLocator.getService(PluginSecretStore::class.java),
        )
    }
}
