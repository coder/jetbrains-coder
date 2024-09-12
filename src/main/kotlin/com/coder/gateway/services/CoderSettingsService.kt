package com.coder.gateway.services

import com.coder.gateway.settings.CoderSettingsState
import com.jetbrains.toolbox.gateway.PluginSettingsStore

/**
 * Provides Coder settings backed by the settings state service.
 *
 * This also provides some helpers such as resolving the provided settings with
 * environment variables and the defaults.
 *
 * For that reason, and to avoid presenting mutable values to most of the code
 * while letting the settings page still read and mutate the underlying state,
 * prefer using CoderSettingsService over CoderSettingsStateService.
 */
class CoderSettingsService(private val store: PluginSettingsStore) : CoderSettingsState() {
    private fun get(key: String): String? = store[key]

    private fun set(key: String, value: String) {
        if (value.isBlank()) {
            store.remove(key)
        } else {
            store[key] = value
        }
    }

    override var binarySource: String
        get() = get("binarySource") ?: super.binarySource
        set(value) = set("binarySource", value)
    override var binaryDirectory: String
        get() = get("binaryDirectory") ?: super.binaryDirectory
        set(value) = set("binaryDirectory", value)
    override var dataDirectory: String
        get() = get("dataDirectory") ?: super.dataDirectory
        set(value) = set("dataDirectory", value)
    override var enableDownloads: Boolean
        get() = get("enableDownloads")?.toBooleanStrictOrNull() ?: super.enableDownloads
        set(value) = set("enableDownloads", value.toString())
    override var enableBinaryDirectoryFallback: Boolean
        get() = get("enableBinaryDirectoryFallback")?.toBooleanStrictOrNull() ?: super.enableBinaryDirectoryFallback
        set(value) = set("enableBinaryDirectoryFallback", value.toString())
    override var headerCommand: String
        get() = store["headerCommand"] ?: super.headerCommand
        set(value) = set("headerCommand", value)
    override var tlsCertPath: String
        get() = store["tlsCertPath"] ?: super.tlsCertPath
        set(value) = set("tlsCertPath", value)
    override var tlsKeyPath: String
        get() = store["tlsKeyPath"] ?: super.tlsKeyPath
        set(value) = set("tlsKeyPath", value)
    override var tlsCAPath: String
        get() = store["tlsCAPath"] ?: super.tlsCAPath
        set(value) = set("tlsCAPath", value)
    override var tlsAlternateHostname: String
        get() = store["tlsAlternateHostname"] ?: super.tlsAlternateHostname
        set(value) = set("tlsAlternateHostname", value)
    override var disableAutostart: Boolean
        get() = store["disableAutostart"]?.toBooleanStrictOrNull() ?: super.disableAutostart
        set(value) = set("disableAutostart", value.toString())
}
