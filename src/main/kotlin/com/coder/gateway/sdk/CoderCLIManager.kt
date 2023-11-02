package com.coder.gateway.sdk

import com.coder.gateway.models.WorkspaceAgentModel
import com.coder.gateway.services.CoderSettingsState
import com.coder.gateway.views.steps.CoderWorkspacesStepView
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import org.zeroturnaround.exec.ProcessExecutor
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.IDN
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import javax.net.ssl.HttpsURLConnection
import javax.xml.bind.annotation.adapters.HexBinaryAdapter


/**
 * Manage the CLI for a single deployment.
 */
class CoderCLIManager @JvmOverloads constructor(
    private val settings: CoderSettingsState,
    private val deploymentURL: URL,
    dataDir: Path,
    cliDir: Path? = null,
    remoteBinaryURLOverride: String? = null,
    private val sshConfigPath: Path = Path.of(System.getProperty("user.home")).resolve(".ssh/config"),
) {
    var remoteBinaryURL: URL
    var localBinaryPath: Path
    var coderConfigPath: Path

    init {
        val binaryName = getCoderCLIForOS(getOS(), getArch())
        remoteBinaryURL = URL(
            deploymentURL.protocol,
            deploymentURL.host,
            deploymentURL.port,
            "/bin/$binaryName"
        )
        if (!remoteBinaryURLOverride.isNullOrBlank()) {
            logger.info("Using remote binary override $remoteBinaryURLOverride")
            remoteBinaryURL = try {
                remoteBinaryURLOverride.toURL()
            } catch (e: Exception) {
                remoteBinaryURL.withPath(remoteBinaryURLOverride)
            }
        }
        val host = getSafeHost(deploymentURL)
        val subdir = if (deploymentURL.port > 0) "${host}-${deploymentURL.port}" else host
        localBinaryPath = (cliDir ?: dataDir).resolve(subdir).resolve(binaryName).toAbsolutePath()
        coderConfigPath = dataDir.resolve(subdir).resolve("config").toAbsolutePath()
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

    /**
     * Download the CLI from the deployment if necessary.
     */
    fun downloadCLI(): Boolean {
        val etag = getBinaryETag()
        val conn = remoteBinaryURL.openConnection() as HttpURLConnection
        if (settings.headerCommand.isNotBlank()){
//            get headers
            val headersFromHeaderCommand =CoderRestClient.getHeaders(deploymentURL,settings.headerCommand)
            for ((key, value) in headersFromHeaderCommand) {
                conn.setRequestProperty(key, value)
            }
        }
        if (etag != null) {
            logger.info("Found existing binary at $localBinaryPath; calculated hash as $etag")
            conn.setRequestProperty("If-None-Match", "\"$etag\"")
        }
        conn.setRequestProperty("Accept-Encoding", "gzip")

        if (conn is HttpsURLConnection) {
            conn.sslSocketFactory = coderSocketFactory(settings)
            conn.hostnameVerifier = CoderHostnameVerifier(settings.tlsAlternateHostname)
        }

        try {
            conn.connect()
            logger.info("GET ${conn.responseCode} $remoteBinaryURL")
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    logger.info("Downloading binary to $localBinaryPath")
                    Files.createDirectories(localBinaryPath.parent)
                    conn.inputStream.use {
                        Files.copy(
                            if (conn.contentEncoding == "gzip") GZIPInputStream(it) else it,
                            localBinaryPath,
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    }
                    if (getOS() != OS.WINDOWS) {
                        localBinaryPath.toFile().setExecutable(true)
                    }
                    return true
                }

                HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    logger.info("Using cached binary at $localBinaryPath")
                    return false
                }
            }
        } catch (e: ConnectException) {
            // Add the URL so this is more easily debugged.
            throw ConnectException("${e.message} to $remoteBinaryURL")
        } finally {
            conn.disconnect()
        }
        throw ResponseException("Unexpected response from $remoteBinaryURL", conn.responseCode)
    }

    /**
     * Return the entity tag for the binary on disk, if any.
     */
    @Suppress("ControlFlowWithEmptyBody")
    private fun getBinaryETag(): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-1")
            val fis = FileInputStream(localBinaryPath.toFile())
            val dis = DigestInputStream(BufferedInputStream(fis), md)
            fis.use {
                while (dis.read() != -1) {
                }
            }
            HexBinaryAdapter().marshal(md.digest()).lowercase()
        } catch (e: FileNotFoundException) {
            null
        } catch (e: Exception) {
            logger.warn("Unable to calculate hash for $localBinaryPath", e)
            null
        }
    }

    /**
     * Use the provided token to authenticate the CLI.
     */
    fun login(token: String): String {
        logger.info("Storing CLI credentials in $coderConfigPath")
        return exec(
            "login",
            deploymentURL.toString(),
            "--token",
            token,
            "--global-config",
            coderConfigPath.toString(),
            "--header-command",
            settings.headerCommand
        )
    }

    /**
     * Configure SSH to use this binary.
     */
    @JvmOverloads
    fun configSsh(workspaces: List<WorkspaceAgentModel>, headerCommand: String? = null) {
        writeSSHConfig(modifySSHConfig(readSSHConfig(), workspaces, headerCommand))
    }

    /**
     * Return the contents of the SSH config or null if it does not exist.
     */
    private fun readSSHConfig(): String? {
        return try {
            sshConfigPath.toFile().readText()
        } catch (e: FileNotFoundException) {
            null
        }
    }

    /**
     * Given an existing SSH config modify it to add or remove the config for
     * this deployment and return the modified config or null if it does not
     * need to be modified.
     */
    private fun modifySSHConfig(
        contents: String?,
        workspaces: List<WorkspaceAgentModel>,
        headerCommand: String?,
    ): String? {
        val host = getSafeHost(deploymentURL)
        val startBlock = "# --- START CODER JETBRAINS $host"
        val endBlock = "# --- END CODER JETBRAINS $host"
        val isRemoving = workspaces.isEmpty()
        val proxyArgs = listOfNotNull(
            escape(localBinaryPath.toString()),
            "--global-config", escape(coderConfigPath.toString()),
            if (!headerCommand.isNullOrBlank()) "--header-command" else null,
            if (!headerCommand.isNullOrBlank()) escape(headerCommand) else null,
           "ssh", "--stdio")
        val blockContent = workspaces.joinToString(
            System.lineSeparator(),
            startBlock + System.lineSeparator(),
            System.lineSeparator() + endBlock,
            transform = {
                """
                Host ${getHostName(deploymentURL, it)}
                  HostName coder.${it.name}
                  ProxyCommand ${proxyArgs.joinToString(" ")} ${it.name}
                  ConnectTimeout 0
                  StrictHostKeyChecking no
                  UserKnownHostsFile /dev/null
                  LogLevel ERROR
                  SetEnv CODER_SSH_SESSION_TYPE=JetBrains
                """.trimIndent().replace("\n", System.lineSeparator())
            })

        if (contents == null) {
            logger.info("No existing SSH config to modify")
            return blockContent + System.lineSeparator()
        }

        val start = "(\\s*)$startBlock".toRegex().find(contents)
        val end = "$endBlock(\\s*)".toRegex().find(contents)

        if (start == null && end == null && isRemoving) {
            logger.info("No workspaces and no existing config blocks to remove")
            return null
        }

        if (start == null && end == null) {
            logger.info("Appending config block")
            val toAppend = if (contents.isEmpty()) blockContent else listOf(
                contents,
                blockContent
            ).joinToString(System.lineSeparator())
            return toAppend + System.lineSeparator()
        }

        if (start == null) {
            throw SSHConfigFormatException("End block exists but no start block")
        }
        if (end == null) {
            throw SSHConfigFormatException("Start block exists but no end block")
        }
        if (start.range.first > end.range.first) {
            throw SSHConfigFormatException("Start block found after end block")
        }

        if (isRemoving) {
            logger.info("No workspaces; removing config block")
            return listOf(
                contents.substring(0, start.range.first),
                // Need to keep the trailing newline(s) if we are not at the
                // front of the file otherwise the before and after lines would
                // get joined.
                if (start.range.first > 0) end.groupValues[1] else "",
                contents.substring(end.range.last + 1)
            ).joinToString("")
        }

        logger.info("Replacing existing config block")
        return listOf(
            contents.substring(0, start.range.first),
            start.groupValues[1], // Leading newline(s).
            blockContent,
            end.groupValues[1], // Trailing newline(s).
            contents.substring(end.range.last + 1)
        ).joinToString("")
    }

    /**
     * Write the provided SSH config or do nothing if null.
     */
    private fun writeSSHConfig(contents: String?) {
        if (contents != null) {
            Files.createDirectories(sshConfigPath.parent)
            sshConfigPath.toFile().writeText(contents)
        }
    }

    /**
     * Version output from the CLI's version command.
     */
    private data class Version(
        val version: String,
    )

    /**
     * Return the binary version.
     *
     * Throws if it could not be determined.
     */
    fun version(): CoderSemVer {
        val raw = exec("version", "--output", "json")
        val json = Gson().fromJson(raw, Version::class.java)
        if (json?.version == null) {
            throw MissingVersionException("No version found in output")
        }
        return CoderSemVer.parse(json.version)
    }

    /**
     * Returns true if the CLI has the same major/minor/patch version as the
     * provided version, false if it does not match or either version is
     * invalid, or null if the CLI version could not be determined because the
     * binary could not be executed.
     */
    fun matchesVersion(rawBuildVersion: String): Boolean? {
        val cliVersion = try {
            version()
        } catch (e: Exception) {
            when (e) {
                 is JsonSyntaxException,
                 is IllegalArgumentException -> {
                     logger.info("Got invalid version from $localBinaryPath: ${e.message}")
                     return false
                 }
                else -> {
                    // An error here most likely means the CLI does not exist or
                    // it executed successfully but output no version which
                    // suggests it is not the right binary.
                    logger.info("Unable to determine $localBinaryPath version: ${e.message}")
                    return null
                }
            }
        }

        val buildVersion = try {
            CoderSemVer.parse(rawBuildVersion)
        } catch (e: IllegalArgumentException) {
            logger.info("Got invalid build version: $rawBuildVersion")
            return false
        }

        val matches = cliVersion == buildVersion
        logger.info("$localBinaryPath version $cliVersion matches $buildVersion: $matches")
        return matches
    }

    private fun exec(vararg args: String): String {
        val stdout = ProcessExecutor()
            .command(localBinaryPath.toString(), *args)
            .environment("CODER_HEADER_COMMAND",settings.headerCommand)
            .exitValues(0)
            .readOutput(true)
            .execute()
            .outputUTF8()
        val redactedArgs = listOf(*args).joinToString(" ").replace(tokenRegex, "--token <redacted>")
        logger.info("`$localBinaryPath $redactedArgs`: $stdout")
        return stdout
    }

    companion object {
        val logger = Logger.getInstance(CoderCLIManager::class.java.simpleName)

        private val tokenRegex = "--token [^ ]+".toRegex()

        /**
         * Return the URL and token from the CLI config.
         */
        @JvmStatic
        fun readConfig(env: Environment = Environment()): Pair<String?, String?> {
            val configDir = getConfigDir(env)
            CoderWorkspacesStepView.logger.info("Reading config from $configDir")
            return try {
                val url = Files.readString(configDir.resolve("url"))
                val token = Files.readString(configDir.resolve("session"))
                url to token
            } catch (e: Exception) {
                null to null // Probably has not configured the CLI yet.
            }
        }

        /**
         * Return the config directory used by the CLI.
         */
        @JvmStatic
        @JvmOverloads
        fun getConfigDir(env: Environment = Environment()): Path {
            var dir = env.get("CODER_CONFIG_DIR")
            if (!dir.isNullOrBlank()) {
                return Path.of(dir)
            }
            // The Coder CLI uses https://github.com/kirsle/configdir so this should
            // match how it behaves.
            return when (getOS()) {
                OS.WINDOWS -> Paths.get(env.get("APPDATA"), "coderv2")
                OS.MAC -> Paths.get(env.get("HOME"), "Library/Application Support/coderv2")
                else -> {
                    dir = env.get("XDG_CONFIG_HOME")
                    if (!dir.isNullOrBlank()) {
                        return Paths.get(dir, "coderv2")
                    }
                    return Paths.get(env.get("HOME"), ".config/coderv2")
                }
            }
        }

        /**
         * Return the data directory.
         */
        @JvmStatic
        @JvmOverloads
        fun getDataDir(env: Environment = Environment()): Path {
            return when (getOS()) {
                OS.WINDOWS -> Paths.get(env.get("LOCALAPPDATA"), "coder-gateway")
                OS.MAC -> Paths.get(env.get("HOME"), "Library/Application Support/coder-gateway")
                else -> {
                    val dir = env.get("XDG_DATA_HOME")
                    if (!dir.isNullOrBlank()) {
                        return Paths.get(dir, "coder-gateway")
                    }
                    return Paths.get(env.get("HOME"), ".local/share/coder-gateway")
                }
            }
        }

        /**
         * Convert IDN to ASCII in case the file system cannot support the
         * necessary character set.
         */
        private fun getSafeHost(url: URL): String {
            return IDN.toASCII(url.host, IDN.ALLOW_UNASSIGNED)
        }

        @JvmStatic
        fun getHostName(url: URL, ws: WorkspaceAgentModel): String {
            return "coder-jetbrains--${ws.name}--${getSafeHost(url)}"
        }

        /**
         * Do as much as possible to get a valid, up-to-date CLI.
         */
        @JvmStatic
        @JvmOverloads
        fun ensureCLI(
            deploymentURL: URL,
            buildVersion: String,
            settings: CoderSettingsState,
            indicator: ProgressIndicator? = null,
        ): CoderCLIManager {
            val dataDir =
                if (settings.dataDirectory.isBlank()) getDataDir()
                else Path.of(settings.dataDirectory).toAbsolutePath()
            val binDir =
                if (settings.binaryDirectory.isBlank()) null
                else Path.of(settings.binaryDirectory).toAbsolutePath()

            val cli = CoderCLIManager(settings, deploymentURL, dataDir, binDir, settings.binarySource)

            // Short-circuit if we already have the expected version.  This
            // lets us bypass the 304 which is slower and may not be
            // supported if the binary is downloaded from alternate sources.
            // For CLIs without the JSON output flag we will fall back to
            // the 304 method.
            val cliMatches = cli.matchesVersion(buildVersion)
            if (cliMatches == true) {
                return cli
            }

            // If downloads are enabled download the new version.
            if (settings.enableDownloads) {
                indicator?.text = "Downloading Coder CLI..."
                try {
                    cli.downloadCLI()
                    return cli
                } catch (e: java.nio.file.AccessDeniedException) {
                    // Might be able to fall back.
                    if (binDir == null || binDir == dataDir || !settings.enableBinaryDirectoryFallback) {
                        throw e
                    }
                }
            }

            // Try falling back to the data directory.
            val dataCLI = CoderCLIManager(settings, deploymentURL, dataDir, null, settings.binarySource)
            val dataCLIMatches = dataCLI.matchesVersion(buildVersion)
            if (dataCLIMatches == true) {
                return dataCLI
            }

            if (settings.enableDownloads) {
                indicator?.text = "Downloading Coder CLI..."
                dataCLI.downloadCLI()
                return dataCLI
            }

            // Prefer the binary directory unless the data directory has a
            // working binary and the binary directory does not.
            return if (cliMatches == null && dataCLIMatches != null) dataCLI else cli
        }

        /**
         * Escape a command argument to be used in the ProxyCommand of an SSH
         * config.  Surround with double quotes if the argument contains
         * whitespace and escape any existing double quotes.
         *
         * Throws if the argument is invalid.
         */
        @JvmStatic
        fun escape(s: String): String {
            if (s.contains("\n")) {
                throw Exception("argument cannot contain newlines")
            }
            if (s.contains(" ") || s.contains("\t")) {
                return "\"" + s.replace("\"", "\\\"") + "\""
            }
            return s.replace("\"", "\\\"")
        }
    }
}

class Environment(private val env: Map<String, String> = emptyMap()) {
    fun get(name: String): String? {
        val e = env[name]
        if (e != null) {
            return e
        }
        return System.getenv(name)
    }
}

class ResponseException(message: String, val code: Int) : Exception(message)
class SSHConfigFormatException(message: String) : Exception(message)
class MissingVersionException(message: String) : Exception(message)
