package com.coder.gateway.services

import com.jetbrains.toolbox.gateway.PluginSecretStore

/**
 * Provides Coder secrets backed by the secrets store service.
 */
class CoderSecretsService(private val store: PluginSecretStore) {
    private fun get(key: String): String {
        return store[key] ?: ""
    }

    private fun set(key: String, value: String) {
        if (value.isBlank()) {
            store.clear(key)
        } else {
            store[key] = value
        }
    }

    var url : String
        get() = get("url")
        set(value) = set("url", value)
    var token : String
        get() = get("token")
        set(value) = set("token", value)
}
