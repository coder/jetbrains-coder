package com.coder.gateway.services

import com.jetbrains.toolbox.api.core.PluginSecretStore


/**
 * Provides Coder secrets backed by the secrets store service.
 */
class CoderSecretsService(private val store: PluginSecretStore) {
    private fun get(key: String): String = store[key] ?: ""

    private fun set(key: String, value: String) {
        if (value.isBlank()) {
            store.clear(key)
        } else {
            store[key] = value
        }
    }

    var lastDeploymentURL: String
        get() = get("last-deployment-url")
        set(value) = set("last-deployment-url", value)
    var lastToken: String
        get() = get("last-token")
        set(value) = set("last-token", value)
    var rememberMe: String
        get() = get("remember-me")
        set(value) = set("remember-me", value)
}
