package com.coder.gateway.settings

import com.coder.gateway.services.CoderSettingsState
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

/**
 * In non-test code use CoderSettingsService instead.
 */
open class CoderSettings(
    private val state: CoderSettingsState,
    // The location of the SSH config.  Defaults to ~/.ssh/config.
    val sshConfigPath: Path = Path.of(System.getProperty("user.home")).resolve(".ssh/config"),
    // Env allows overriding the default environment.
    private val env: Environment = Environment(),
) {
    val tls = CoderTLSSettings(state)
    val enableDownloads: Boolean
        get() = state.enableDownloads

    val enableBinaryDirectoryFallback: Boolean
        get() = state.enableBinaryDirectoryFallback

    val headerCommand: String
        get() = state.headerCommand

    /**
     * Where the specified deployment should put its data.
     */
    fun dataDir(url: URL): Path {
        val dir = if (state.dataDirectory.isBlank()) dataDir
        else Path.of(expand(state.dataDirectory))
        return withHost(dir, url).toAbsolutePath()
    }

    /**
     * From where the specified deployment should download the binary.
     */
    fun binSource(url: URL): URL {
        val binaryName = getCoderCLIForOS(getOS(), getArch())
        return if (state.binarySource.isBlank()) {
            url.withPath("/bin/$binaryName")
        } else {
            logger.info("Using binary source override ${state.binarySource}")
            try {
                state.binarySource.toURL()
            } catch (e: Exception) {
                url.withPath(state.binarySource) // Assume a relative path.
            }
        }
    }

    /**
     * To where the specified deployment should download the binary.
     */
    fun binPath(url: URL, forceDownloadToData: Boolean = false): Path {
        val binaryName = getCoderCLIForOS(getOS(), getArch())
        val dir = if (forceDownloadToData || state.binaryDirectory.isBlank()) dataDir(url)
        else withHost(Path.of(expand(state.binaryDirectory)), url)
        return dir.resolve(binaryName).toAbsolutePath()
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
