package com.coder.gateway.services

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Controls serializing and deserializing raw settings to and from disk.  Use
 * only when you need to directly mutate the settings (such as from the settings
 * page) and in tests, otherwise use CoderSettingsService.
 */
@Service(Service.Level.APP)
@State(
    name = "CoderSettingsState",
    storages = [Storage("coder-settings.xml", roamingType = RoamingType.DISABLED, exportable = true)]
)
class CoderSettingsState(
    // Used to download the Coder CLI which is necessary to proxy SSH
    // connections.  The If-None-Match header will be set to the SHA1 of the CLI
    // and can be used for caching.  Absolute URLs will be used as-is; otherwise
    // this value will be resolved against the deployment domain.  Defaults to
    // the plugin's data directory.
    var binarySource: String = "",
    // Directories are created here that store the CLI for each domain to which
    // the plugin connects.   Defaults to the data directory.
    var binaryDirectory: String = "",
    // Where to save plugin data like the Coder binary (if not configured with
    // binaryDirectory) and the deployment URL and session token.
    var dataDirectory: String = "",
    // Whether to allow the plugin to download the CLI if the current one is out
    // of date or does not exist.
    var enableDownloads: Boolean = true,
    // Whether to allow the plugin to fall back to the data directory when the
    // CLI directory is not writable.
    var enableBinaryDirectoryFallback: Boolean = false,
    // An external command that outputs additional HTTP headers added to all
    // requests. The command must output each header as `key=value` on its own
    // line. The following environment variables will be available to the
    // process: CODER_URL.
    var headerCommand: String = "",
    // Optionally set this to the path of a certificate to use for TLS
    // connections. The certificate should be in X.509 PEM format.
    var tlsCertPath: String = "",
    // Optionally set this to the path of the private key that corresponds to
    // the above cert path to use for TLS connections. The key should be in
    // X.509 PEM format.
    var tlsKeyPath: String = "",
    // Optionally set this to the path of a file containing certificates for an
    // alternate certificate authority used to verify TLS certs returned by the
    // Coder service. The file should be in X.509 PEM format.
    var tlsCAPath: String = "",
    // Optionally set this to an alternate hostname used for verifying TLS
    // connections. This is useful when the hostname used to connect to the
    // Coder service does not match the hostname in the TLS certificate.
    var tlsAlternateHostname: String = "",
) : PersistentStateComponent<CoderSettingsState> {
    override fun getState(): CoderSettingsState {
        return this
    }

    override fun loadState(state: CoderSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }
}
