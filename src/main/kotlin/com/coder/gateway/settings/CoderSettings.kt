package com.coder.gateway.settings

import com.coder.gateway.util.Arch
import com.coder.gateway.util.OS
import com.coder.gateway.util.expand
import com.coder.gateway.util.getArch
import com.coder.gateway.util.getOS
import com.coder.gateway.util.safeHost
import com.coder.gateway.util.toURL
import com.coder.gateway.util.withPath
import com.intellij.openapi.diagnostic.Logger
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

const val CODER_SSH_CONFIG_OPTIONS = "CODER_SSH_CONFIG_OPTIONS";

open class CoderSettingsState(
    // Used to download the Coder CLI which is necessary to proxy SSH
    // connections.  The If-None-Match header will be set to the SHA1 of the CLI
    // and can be used for caching.  Absolute URLs will be used as-is; otherwise
    // this value will be resolved against the deployment domain.  Defaults to
    // the plugin's data directory.
    open var binarySource: String = "",
    // Directories are created here that store the CLI for each domain to which
    // the plugin connects.   Defaults to the data directory.
    open var binaryDirectory: String = "",
    // Where to save plugin data like the Coder binary (if not configured with
    // binaryDirectory) and the deployment URL and session token.
    open var dataDirectory: String = "",
    // Whether to allow the plugin to download the CLI if the current one is out
    // of date or does not exist.
    open var enableDownloads: Boolean = true,
    // Whether to allow the plugin to fall back to the data directory when the
    // CLI directory is not writable.
    open var enableBinaryDirectoryFallback: Boolean = false,
    // An external command that outputs additional HTTP headers added to all
    // requests. The command must output each header as `key=value` on its own
    // line. The following environment variables will be available to the
    // process: CODER_URL.
    open var headerCommand: String = "",
    // Optionally set this to the path of a certificate to use for TLS
    // connections. The certificate should be in X.509 PEM format.
    open var tlsCertPath: String = "",
    // Optionally set this to the path of the private key that corresponds to
    // the above cert path to use for TLS connections. The key should be in
    // X.509 PEM format.
    open var tlsKeyPath: String = "",
    // Optionally set this to the path of a file containing certificates for an
    // alternate certificate authority used to verify TLS certs returned by the
    // Coder service. The file should be in X.509 PEM format.
    open var tlsCAPath: String = "",
    // Optionally set this to an alternate hostname used for verifying TLS
    // connections. This is useful when the hostname used to connect to the
    // Coder service does not match the hostname in the TLS certificate.
    open var tlsAlternateHostname: String = "",
    // Whether to add --disable-autostart to the proxy command.  This works
    // around issues on macOS where it periodically wakes and Gateway
    // reconnects, keeping the workspace constantly up.
    open var disableAutostart: Boolean = getOS() == OS.MAC,
    // Extra SSH config options.
    open var sshConfigOptions: String = "",
)

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

/**
 * In non-test code use CoderSettingsService instead.
 */
open class CoderSettings(
    // Raw mutable setting state.
    private val state: CoderSettingsState,
    // The location of the SSH config.  Defaults to ~/.ssh/config.
    val sshConfigPath: Path = Path.of(System.getProperty("user.home")).resolve(".ssh/config"),
    // Overrides the default environment (for tests).
    private val env: Environment = Environment(),
    // Overrides the default binary name (for tests).
    private val binaryName: String? = null,
) {
    val tls = CoderTLSSettings(state)

    /**
     * Whether downloading the CLI is allowed.
     */
    val enableDownloads: Boolean
        get() = state.enableDownloads

    /**
     * Whether falling back to the data directory is allowed if the binary
     * directory is not writable.
     */
    val enableBinaryDirectoryFallback: Boolean
        get() = state.enableBinaryDirectoryFallback

    /**
     * A command to run to set headers for API calls.
     */
    val headerCommand: String
        get() = state.headerCommand

    /**
     * Whether to disable automatically starting a workspace when connecting.
     */
    val disableAutostart: Boolean
        get() = state.disableAutostart

    /**
     * Extra SSH config to append to each host block.
     */
    val sshConfigOptions: String
        get() = state.sshConfigOptions.ifBlank { env.get(CODER_SSH_CONFIG_OPTIONS) }

    /**
     * Where the specified deployment should put its data.
     */
    fun dataDir(url: URL): Path {
        state.dataDirectory.let {
            val dir = if (it.isBlank()) dataDir
            else Path.of(expand(it))
            return withHost(dir, url).toAbsolutePath()
        }
    }

    /**
     * From where the specified deployment should download the binary.
     */
    fun binSource(url: URL): URL {
        state.binarySource.let {
            val binaryName = getCoderCLIForOS(getOS(), getArch())
            return if (it.isBlank()) {
                url.withPath("/bin/$binaryName")
            } else {
                logger.info("Using binary source override $it")
                try {
                    it.toURL()
                } catch (e: Exception) {
                    url.withPath(it) // Assume a relative path.
                }
            }
        }
    }

    /**
     * To where the specified deployment should download the binary.
     */
    fun binPath(url: URL, forceDownloadToData: Boolean = false): Path {
        state.binaryDirectory.let {
            val name = binaryName ?: getCoderCLIForOS(getOS(), getArch())
            val dir = if (forceDownloadToData || it.isBlank()) dataDir(url)
            else withHost(Path.of(expand(it)), url)
            return dir.resolve(name).toAbsolutePath()
        }
    }

    /**
     * Return the URL and token from the config, if it exists.
     */
    fun readConfig(dir: Path): Pair<String?, String?> {
        logger.info("Reading config from $dir")
        return try {
            Files.readString(dir.resolve("url")) to Files.readString(dir.resolve("session"))
        } catch (e: Exception) {
            // SSH has not been configured yet.
            null to null
        }
    }

    /**
     * Append the host to the path.  For example, foo/bar could become
     * foo/bar/dev.coder.com-8080.
     */
    private fun withHost(path: Path, url: URL): Path {
        val host = if (url.port > 0) "${url.safeHost()}-${url.port}" else url.safeHost()
        return path.resolve(host)
    }

    /**
     * Return the global config directory used by the Coder CLI.
     */
    val coderConfigDir: Path
        get() {
            var dir = env.get("CODER_CONFIG_DIR")
            if (dir.isNotBlank()) {
                return Path.of(dir)
            }
            // The Coder CLI uses https://github.com/kirsle/configdir so this should
            // match how it behaves.
            return when (getOS()) {
                OS.WINDOWS -> Paths.get(env.get("APPDATA"), "coderv2")
                OS.MAC -> Paths.get(env.get("HOME"), "Library/Application Support/coderv2")
                else -> {
                    dir = env.get("XDG_CONFIG_HOME")
                    if (dir.isNotBlank()) {
                        return Paths.get(dir, "coderv2")
                    }
                    return Paths.get(env.get("HOME"), ".config/coderv2")
                }
            }
        }

    /**
     * Return the Coder plugin's global data directory.
     */
    val dataDir: Path
        get() {
            return when (getOS()) {
                OS.WINDOWS -> Paths.get(env.get("LOCALAPPDATA"), "coder-gateway")
                OS.MAC -> Paths.get(env.get("HOME"), "Library/Application Support/coder-gateway")
                else -> {
                    val dir = env.get("XDG_DATA_HOME")
                    if (dir.isNotBlank()) {
                        return Paths.get(dir, "coder-gateway")
                    }
                    return Paths.get(env.get("HOME"), ".local/share/coder-gateway")
                }
            }
        }

    /**
     * Return the name of the binary (with extension) for the provided OS and
     * architecture.
     */
    private fun getCoderCLIForOS(os: OS?, arch: Arch?): String {
        logger.info("Resolving binary for $os $arch")
        if (os == null) {
            logger.error("Could not resolve client OS and architecture, defaulting to WINDOWS AMD64")
            return "coder-windows-amd64.exe"
        }
        return when (os) {
            OS.WINDOWS -> when (arch) {
                Arch.AMD64 -> "coder-windows-amd64.exe"
                Arch.ARM64 -> "coder-windows-arm64.exe"
                else -> "coder-windows-amd64.exe"
            }

            OS.LINUX -> when (arch) {
                Arch.AMD64 -> "coder-linux-amd64"
                Arch.ARM64 -> "coder-linux-arm64"
                Arch.ARMV7 -> "coder-linux-armv7"
                else -> "coder-linux-amd64"
            }

            OS.MAC -> when (arch) {
                Arch.AMD64 -> "coder-darwin-amd64"
                Arch.ARM64 -> "coder-darwin-arm64"
                else -> "coder-darwin-amd64"
            }
        }
    }

    companion object {
        val logger = Logger.getInstance(CoderSettings::class.java.simpleName)
    }
}
