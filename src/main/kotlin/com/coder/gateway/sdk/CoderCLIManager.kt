package com.coder.gateway.sdk

import com.coder.gateway.models.WorkspaceAgentModel
import com.coder.gateway.views.steps.CoderWorkspacesStepView
import com.intellij.openapi.diagnostic.Logger
import org.zeroturnaround.exec.ProcessExecutor
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.IDN
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import javax.xml.bind.annotation.adapters.HexBinaryAdapter


/**
 * Manage the CLI for a single deployment.
 */
class CoderCLIManager @JvmOverloads constructor(private val deploymentURL: URL, destinationDir: Path = getDataDir()) {
    private var remoteBinaryUrl: URL
    var localBinaryPath: Path
    private var coderConfigPath: Path

    init {
        val binaryName = getCoderCLIForOS(getOS(), getArch())
        remoteBinaryUrl = URL(
            deploymentURL.protocol,
            deploymentURL.host,
            deploymentURL.port,
            "/bin/$binaryName"
        )
        // Convert IDN to ASCII in case the file system cannot support the
        // necessary character set.
        val host = getSafeHost(deploymentURL)
        val subdir = if (deploymentURL.port > 0) "${host}-${deploymentURL.port}" else host
        localBinaryPath = destinationDir.resolve(subdir).resolve(binaryName)
        coderConfigPath = destinationDir.resolve(subdir).resolve("config")
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
        val conn = remoteBinaryUrl.openConnection() as HttpURLConnection
        if (etag != null) {
            logger.info("Found existing binary at ${localBinaryPath.toAbsolutePath()}; calculated hash as $etag")
            conn.setRequestProperty("If-None-Match", "\"$etag\"")
        }
        conn.setRequestProperty("Accept-Encoding", "gzip")

        try {
            conn.connect()
            logger.info("GET ${conn.responseCode} $remoteBinaryUrl")
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    logger.info("Downloading binary to ${localBinaryPath.toAbsolutePath()}")
                    Files.createDirectories(localBinaryPath.parent)
                    conn.inputStream.use {
                        Files.copy(
                            if (conn.contentEncoding == "gzip") GZIPInputStream(it) else it,
                            localBinaryPath,
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    }
                    if (getOS() != OS.WINDOWS) {
                        Files.setPosixFilePermissions(
                            localBinaryPath,
                            PosixFilePermissions.fromString("rwxr-x---")
                        )
                    }
                    return true
                }

                HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    logger.info("Using cached binary at ${localBinaryPath.toAbsolutePath()}")
                    return false
                }
            }
        } finally {
            conn.disconnect()
        }
        throw ResponseException("Unexpected response from $remoteBinaryUrl", conn.responseCode)
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
            logger.warn("Unable to calculate hash for ${localBinaryPath.toAbsolutePath()}", e)
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
            coderConfigPath.toAbsolutePath().toString(),
        )
    }

    /**
     * Configure SSH to use this binary.
     */
    fun configSsh(
        workspaces: List<WorkspaceAgentModel>,
        sshConfigPath: Path = Path.of(System.getProperty("user.home")).resolve(".ssh/config"),
    ) {
        val host = getSafeHost(deploymentURL)
        val startBlock = "# --- START CODER JETBRAINS $host"
        val endBlock = "# --- END CODER JETBRAINS $host"
        val isRemoving = workspaces.isEmpty()
        val blockContent = workspaces.joinToString(
            System.lineSeparator(),
            startBlock + System.lineSeparator(),
            System.lineSeparator() + endBlock,
            transform = {
                """
                Host ${getHostName(deploymentURL, it)}
                  HostName coder.${it.name}
                  ProxyCommand "${localBinaryPath.toAbsolutePath()}" --global-config "${coderConfigPath.toAbsolutePath()}" ssh --stdio ${it.name}
                  ConnectTimeout 0
                  StrictHostKeyChecking no
                  UserKnownHostsFile /dev/null
                  LogLevel ERROR
                  SetEnv CODER_SSH_SESSION_TYPE=JetBrains
                """.trimIndent().replace("\n", System.lineSeparator())
            })
        Files.createDirectories(sshConfigPath.parent)
        try {
            val contents = sshConfigPath.toFile().readText()
            val start = "(\\s*)$startBlock".toRegex().find(contents)
            val end = "$endBlock(\\s*)".toRegex().find(contents)
            if (start == null && end == null && isRemoving) {
                logger.info("Leaving $sshConfigPath alone since there are no workspaces and no config to remove")
            } else if (start == null && end == null) {
                logger.info("Appending config to $sshConfigPath")
                sshConfigPath.toFile().writeText(
                    if (contents.isEmpty()) blockContent else listOf(
                        contents,
                        blockContent
                    ).joinToString(System.lineSeparator())
                )
            } else if (start == null) {
                throw SSHConfigFormatException("End block exists but no start block")
            } else if (end == null) {
                throw SSHConfigFormatException("Start block exists but no end block")
            } else if (start.range.first > end.range.first) {
                throw SSHConfigFormatException("Start block found after end block")
            } else if (isRemoving) {
                logger.info("Removing config from $sshConfigPath")
                sshConfigPath.toFile().writeText(
                    listOf(
                        contents.substring(0, start.range.first),
                        // Need to keep the trailing newline(s) if we are not at
                        // the front of the file otherwise the before and after
                        // lines would get joined.
                        if (start.range.first > 0) end.groupValues[1] else "",
                        contents.substring(end.range.last + 1)
                    ).joinToString("")
                )
            } else {
                logger.info("Replacing config in $sshConfigPath")
                sshConfigPath.toFile().writeText(
                    listOf(
                        contents.substring(0, start.range.first),
                        start.groupValues[1], // Leading newline(s).
                        blockContent,
                        end.groupValues[1], // Trailing newline(s).
                        contents.substring(end.range.last + 1)
                    ).joinToString("")
                )
            }
        } catch (e: FileNotFoundException) {
            logger.info("Writing config to $sshConfigPath")
            sshConfigPath.toFile().writeText(blockContent)
        }
    }

    /**
     * Return the binary version.
     */
    fun version(): String {
        return exec("version")
    }

    private fun exec(vararg args: String): String {
        val stdout = ProcessExecutor()
            .command(localBinaryPath.toString(), *args)
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

        private fun getSafeHost(url: URL): String {
            return IDN.toASCII(url.host, IDN.ALLOW_UNASSIGNED)
        }

        @JvmStatic
        fun getHostName(url: URL, ws: WorkspaceAgentModel): String {
            return "coder-jetbrains--${ws.name}--${getSafeHost(url)}"
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
