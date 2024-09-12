package com.coder.gateway

import com.jetbrains.toolbox.gateway.GatewayExtension
import com.jetbrains.toolbox.gateway.PluginSecretStore
import com.jetbrains.toolbox.gateway.PluginSettingsStore
import com.jetbrains.toolbox.gateway.RemoteEnvironmentConsumer
import com.jetbrains.toolbox.gateway.RemoteProvider
import com.jetbrains.toolbox.gateway.ToolboxServiceLocator
import com.jetbrains.toolbox.gateway.ui.ObservablePropertiesFactory
import com.jetbrains.toolbox.gateway.ui.ToolboxUi
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient

/**
 * Entry point into the extension.
 */
class CoderGatewayExtension : GatewayExtension {
    // All services must be passed in here and threaded as necessary.
    override fun createRemoteProviderPluginInstance(serviceLocator: ToolboxServiceLocator): RemoteProvider = CoderRemoteProvider(
        serviceLocator.getService(OkHttpClient::class.java),
        serviceLocator.getService(RemoteEnvironmentConsumer::class.java),
        serviceLocator.getService(CoroutineScope::class.java),
        serviceLocator.getService(ToolboxUi::class.java),
        serviceLocator.getService(PluginSettingsStore::class.java),
        serviceLocator.getService(PluginSecretStore::class.java),
        serviceLocator.getService(ObservablePropertiesFactory::class.java),
    )
}
