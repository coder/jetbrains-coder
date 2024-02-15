package com.coder.gateway.settings

import com.coder.gateway.services.CoderSettingsState

/**
 * Consolidated TLS settings.
 */
data class CoderTLSSettings (private val state: CoderSettingsState) {
    val certPath: String
        get() = state.tlsCertPath
    val keyPath: String
        get() = state.tlsKeyPath
    val caPath: String
        get() = state.tlsCAPath
    val altHostname: String
        get() = state.tlsAlternateHostname
}