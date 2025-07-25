package com.coder.gateway.cli

import com.coder.gateway.cli.downloader.CoderDownloadApi
import com.coder.gateway.cli.downloader.CoderDownloadService
import com.coder.gateway.cli.downloader.DownloadResult
import com.coder.gateway.cli.ex.MissingVersionException
import com.coder.gateway.cli.ex.SSHConfigFormatException
import com.coder.gateway.cli.ex.UnsignedBinaryExecutionDeniedException
import com.coder.gateway.cli.gpg.GPGVerifier
import com.coder.gateway.cli.gpg.VerificationResult
import com.coder.gateway.sdk.v2.models.User
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceAgent
import com.coder.gateway.settings.CoderSettings
import com.coder.gateway.settings.CoderSettingsState
import com.coder.gateway.util.CoderHostnameVerifier
import com.coder.gateway.util.DialogUi
import com.coder.gateway.util.InvalidVersionException
import com.coder.gateway.util.SemVer
import com.coder.gateway.util.coderSocketFactory
import com.coder.gateway.util.coderTrustManagers
import com.coder.gateway.util.escape
import com.coder.gateway.util.escapeSubcommand
import com.coder.gateway.util.safeHost
import com.intellij.openapi.diagnostic.Logger
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.zeroturnaround.exec.ProcessExecutor
import retrofit2.Retrofit
import java.io.EOFException
import java.io.FileNotFoundException
import java.net.URL
import java.nio.file.Path
import javax.net.ssl.X509TrustManager

/**
 * Version output from the CLI's version command.
 */
@JsonClass(generateAdapter = true)
internal data class Version(
    @Json(name = "version") val version: String,
)

/**
 * Do as much as possible to get a valid, up-to-date CLI.
 *
 * 1. Read the binary directory for the provided URL.
 * 2. Abort if we already have an up-to-date version.
 * 3. Download the binary using an ETag.
 * 4. Abort if we get a 304 (covers cases where the binary is older and does not
 *    have a version command).
 * 5. Download on top of the existing binary.
 * 6. Since the binary directory can be read-only, if downloading fails, start
 *    from step 2 with the data directory.
 */
suspend fun ensureCLI(
    deploymentURL: URL,
    buildVersion: String,
    settings: CoderSettings,
    indicator: ((t: String) -> Unit)? = null,
): CoderCLIManager {
    val cli = CoderCLIManager(deploymentURL, settings)

    // Short-circuit if we already have the expected version.  This
    // lets us bypass the 304 which is slower and may not be
    // supported if the binary is downloaded from alternate sources.
    // For CLIs without the JSON output flag we will fall back to
    // the 304 method.
    val cliMatches = cli.matchesVersion(buildVersion)
    if (cliMatches == true) {
        indicator?.invoke("Local CLI version matches server version: $buildVersion")
        return cli
    }

    // If downloads are enabled download the new version.
    if (settings.enableDownloads) {
        indicator?.invoke("Downloading Coder CLI...")
        try {
            cli.download(buildVersion, indicator)
            return cli
        } catch (e: java.nio.file.AccessDeniedException) {
            // Might be able to fall back to the data directory.
            val binPath = settings.binPath(deploymentURL)
            val dataDir = settings.dataDir(deploymentURL)
            if (binPath.parent == dataDir || !settings.enableBinaryDirectoryFallback) {
                throw e
            }
        }
    }

    // Try falling back to the data directory.
    val dataCLI = CoderCLIManager(deploymentURL, settings, true)
    val dataCLIMatches = dataCLI.matchesVersion(buildVersion)
    if (dataCLIMatches == true) {
        indicator?.invoke("Local CLI version from data directory matches server version: $buildVersion")
        return dataCLI
    }

    if (settings.enableDownloads) {
        indicator?.invoke("Downloading Coder CLI to the data directory...")
        dataCLI.download(buildVersion, indicator)
        return dataCLI
    }

    // Prefer the binary directory unless the data directory has a
    // working binary and the binary directory does not.
    return if (cliMatches == null && dataCLIMatches != null) dataCLI else cli
}

/**
 * The supported features of the CLI.
 */
data class Features(
    val disableAutostart: Boolean = false,
    val reportWorkspaceUsage: Boolean = false,
    val wildcardSSH: Boolean = false,
    val buildReason: Boolean = false,
)

/**
 * Manage the CLI for a single deployment.
 */
class CoderCLIManager(
    // The URL of the deployment this CLI is for.
    private val deploymentURL: URL,
    // Plugin configuration.
    private val settings: CoderSettings = CoderSettings(CoderSettingsState()),
    // If the binary directory is not writable, this can be used to force the
    // manager to download to the data directory instead.
    private val forceDownloadToData: Boolean = false,
) {
    private val downloader = createDownloadService()
    private val gpgVerifier = GPGVerifier(settings)

    val remoteBinaryURL: URL = settings.binSource(deploymentURL)
    val localBinaryPath: Path = settings.binPath(deploymentURL, forceDownloadToData)
    val coderConfigPath: Path = settings.dataDir(deploymentURL).resolve("config")

    private fun createDownloadService(): CoderDownloadService {
        val okHttpClient = OkHttpClient.Builder()
            .sslSocketFactory(
                coderSocketFactory(settings.tls),
                coderTrustManagers(settings.tls.caPath)[0] as X509TrustManager
            )
            .hostnameVerifier(CoderHostnameVerifier(settings.tls.altHostname))
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(deploymentURL.toString())
            .client(okHttpClient)
            .build()

        val service = retrofit.create(CoderDownloadApi::class.java)
        return CoderDownloadService(settings, service, deploymentURL, forceDownloadToData)
    }

    /**
     * Download the CLI from the deployment if necessary.
     */
    suspend fun download(buildVersion: String, showTextProgress: ((t: String) -> Unit)? = null): Boolean {
        try {
            val cliResult = withContext(Dispatchers.IO) {
                downloader.downloadCli(buildVersion, showTextProgress)
            }.let { result ->
                when {
                    result.isSkipped() -> return false
                    result.isNotFound() -> throw IllegalStateException("Could not find Coder CLI")
                    result.isFailed() -> throw (result as DownloadResult.Failed).error
                    else -> result as DownloadResult.Downloaded
                }
            }

            var signatureResult = withContext(Dispatchers.IO) {
                downloader.downloadSignature(showTextProgress)
            }

            if (signatureResult.isNotDownloaded()) {
                if (settings.fallbackOnCoderForSignatures) {
                    logger.info("Trying to download signature file from releases.coder.com")
                    signatureResult = withContext(Dispatchers.IO) {
                        downloader.downloadReleasesSignature(buildVersion, showTextProgress)
                    }

                    // if we could still not download it, ask the user if he accepts the risk
                    if (signatureResult.isNotDownloaded()) {
                        val acceptsUnsignedBinary = DialogUi(settings)
                            .confirm(
                                "Security Warning",
                                "Could not fetch any signatures for ${cliResult.source} from releases.coder.com. Would you like to run it anyway?"
                            )

                        if (acceptsUnsignedBinary) {
                            downloader.commit()
                            return true
                        } else {
                            throw UnsignedBinaryExecutionDeniedException("Running unsigned CLI from ${cliResult.source} was denied by the user")
                        }
                    }
                } else {
                    // we are not allowed to fetch signatures from releases.coder.com
                    // so we will ask the user if he wants to continue
                    val acceptsUnsignedBinary = DialogUi(settings)
                        .confirm(
                            "Security Warning",
                            "No signatures were found for ${cliResult.source} and fallback to releases.coder.com is not allowed. Would you like to run it anyway?"
                        )

                    if (acceptsUnsignedBinary) {
                        downloader.commit()
                        return true
                    } else {
                        throw UnsignedBinaryExecutionDeniedException("Running unsigned CLI from ${cliResult.source} was denied by the user")
                    }
                }
            }

            // we have the cli, and signature is downloaded, let's verify the signature
            signatureResult = signatureResult as DownloadResult.Downloaded
            gpgVerifier.verifySignature(cliResult.dst, signatureResult.dst).let { result ->
                when {
                    result.isValid() -> {
                        downloader.commit()
                        return true
                    }

                    else -> {
                        logFailure(result, cliResult, signatureResult)
                        // prompt the user if he wants to accept the risk
                        val shouldRunAnyway = DialogUi(settings)
                            .confirm(
                                "Security Warning",
                                "Could not verify the authenticity of the ${cliResult.source}, it may be tampered with. Would you like to run it anyway?"
                            )

                        if (shouldRunAnyway) {
                            downloader.commit()
                            return true
                        } else {
                            throw UnsignedBinaryExecutionDeniedException("Running unverified CLI from ${cliResult.source} was denied by the user")
                        }
                    }
                }
            }
        } finally {
            downloader.cleanup()
        }
    }

    private fun logFailure(
        result: VerificationResult,
        cliResult: DownloadResult.Downloaded,
        signatureResult: DownloadResult.Downloaded
    ) {
        when {
            result.isInvalid() -> {
                val reason = (result as VerificationResult.Invalid).reason
                logger.error("Signature of ${cliResult.dst} is invalid." + reason?.let { " Reason: $it" }
                    .orEmpty())
            }

            result.signatureIsNotFound() -> {
                logger.error("Can't verify signature of ${cliResult.dst} because ${signatureResult.dst} does not exist")
            }

            else -> {
                val failure = result as VerificationResult.Failed
                UnsignedBinaryExecutionDeniedException(result.error.message)
                logger.error("Failed to verify signature for ${cliResult.dst}", failure.error)
            }
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
        )
    }

    /**
     * Configure SSH to use this binary.
     *
     * This can take supported features for testing purposes only.
     */
    fun configSsh(
        workspacesAndAgents: Set<Pair<Workspace, WorkspaceAgent>>,
        currentUser: User,
        feats: Features = features,
    ) {
        logger.info("Configuring SSH config at ${settings.sshConfigPath}")
        writeSSHConfig(modifySSHConfig(readSSHConfig(), workspacesAndAgents, feats, currentUser))
    }

    /**
     * Return the contents of the SSH config or null if it does not exist.
     */
    private fun readSSHConfig(): String? = try {
        settings.sshConfigPath.toFile().readText()
    } catch (e: FileNotFoundException) {
        null
    }

    /**
     * Given an existing SSH config modify it to add or remove the config for
     * this deployment and return the modified config or null if it does not
     * need to be modified.
     *
     * If features are not provided, calculate them based on the binary
     * version.
     */
    private fun modifySSHConfig(
        contents: String?,
        workspaceNames: Set<Pair<Workspace, WorkspaceAgent>>,
        feats: Features,
        currentUser: User,
    ): String? {
        val host = deploymentURL.safeHost()
        val startBlock = "# --- START CODER JETBRAINS $host"
        val endBlock = "# --- END CODER JETBRAINS $host"
        val baseArgs =
            listOfNotNull(
                escape(localBinaryPath.toString()),
                "--global-config",
                escape(coderConfigPath.toString()),
                // CODER_URL might be set, and it will override the URL file in
                // the config directory, so override that here to make sure we
                // always use the correct URL.
                "--url",
                escape(deploymentURL.toString()),
                if (settings.headerCommand.isNotBlank()) "--header-command" else null,
                if (settings.headerCommand.isNotBlank()) escapeSubcommand(settings.headerCommand) else null,
                "ssh",
                "--stdio",
                if (settings.disableAutostart && feats.disableAutostart) "--disable-autostart" else null,
            )
        val proxyArgs = baseArgs + listOfNotNull(
            if (settings.sshLogDirectory.isNotBlank()) "--log-dir" else null,
            if (settings.sshLogDirectory.isNotBlank()) escape(settings.sshLogDirectory) else null,
            if (feats.reportWorkspaceUsage) "--usage-app=jetbrains" else null,
        )
        val backgroundProxyArgs =
            baseArgs + listOfNotNull(if (feats.reportWorkspaceUsage) "--usage-app=disable" else null)
        val extraConfig =
            if (settings.sshConfigOptions.isNotBlank()) {
                "\n" + settings.sshConfigOptions.prependIndent("  ")
            } else {
                ""
            }
        val sshOpts = """
            ConnectTimeout 0
            StrictHostKeyChecking no
            UserKnownHostsFile /dev/null
            LogLevel ERROR
            SetEnv CODER_SSH_SESSION_TYPE=JetBrains
        """.trimIndent()
        val blockContent =
            if (feats.wildcardSSH) {
                startBlock + System.lineSeparator() +
                        """
                    Host ${getHostPrefix()}--*
                      ProxyCommand ${proxyArgs.joinToString(" ")} --ssh-host-prefix ${getHostPrefix()}-- %h
                    """.trimIndent()
                            .plus("\n" + sshOpts.prependIndent("  "))
                            .plus(extraConfig)
                            .plus("\n\n")
                            .plus(
                                """
                            Host ${getHostPrefix()}-bg--*
                              ProxyCommand ${backgroundProxyArgs.joinToString(" ")} --ssh-host-prefix ${getHostPrefix()}-bg-- %h
                            """.trimIndent()
                                    .plus("\n" + sshOpts.prependIndent("  "))
                                    .plus(extraConfig),
                            ).replace("\n", System.lineSeparator()) +
                        System.lineSeparator() + endBlock
            } else if (workspaceNames.isEmpty()) {
                ""
            } else {
                workspaceNames.joinToString(
                    System.lineSeparator(),
                    startBlock + System.lineSeparator(),
                    System.lineSeparator() + endBlock,
                    transform = {
                        """
                    Host ${getHostName(it.first, currentUser, it.second)}
                      ProxyCommand ${proxyArgs.joinToString(" ")} ${getWorkspaceParts(it.first, it.second)}
                        """.trimIndent()
                            .plus("\n" + sshOpts.prependIndent("  "))
                            .plus(extraConfig)
                            .plus("\n")
                            .plus(
                                """
                            Host ${getBackgroundHostName(it.first, currentUser, it.second)}
                              ProxyCommand ${backgroundProxyArgs.joinToString(" ")} ${
                                    getWorkspaceParts(
                                        it.first,
                                        it.second
                                    )
                                }
                                """.trimIndent()
                                    .plus("\n" + sshOpts.prependIndent("  "))
                                    .plus(extraConfig),
                            ).replace("\n", System.lineSeparator())
                    },
                )
            }

        if (contents == null) {
            logger.info("No existing SSH config to modify")
            return blockContent + System.lineSeparator()
        }

        val start = "(\\s*)$startBlock".toRegex().find(contents)
        val end = "$endBlock(\\s*)".toRegex().find(contents)

        val isRemoving = blockContent.isEmpty()

        if (start == null && end == null && isRemoving) {
            logger.info("No workspaces and no existing config blocks to remove")
            return null
        }

        if (start == null && end == null) {
            logger.info("Appending config block")
            val toAppend =
                if (contents.isEmpty()) {
                    blockContent
                } else {
                    listOf(
                        contents,
                        blockContent,
                    ).joinToString(System.lineSeparator())
                }
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
                contents.substring(end.range.last + 1),
            ).joinToString("")
        }

        logger.info("Replacing existing config block")
        return listOf(
            contents.substring(0, start.range.first),
            start.groupValues[1], // Leading newline(s).
            blockContent,
            end.groupValues[1], // Trailing newline(s).
            contents.substring(end.range.last + 1),
        ).joinToString("")
    }

    /**
     * Write the provided SSH config or do nothing if null.
     */
    private fun writeSSHConfig(contents: String?) {
        if (contents != null) {
            settings.sshConfigPath.parent.toFile().mkdirs()
            settings.sshConfigPath.toFile().writeText(contents)
            // The Coder cli will *not* create the log directory.
            if (settings.sshLogDirectory.isNotBlank()) {
                Path.of(settings.sshLogDirectory).toFile().mkdirs()
            }
        }
    }

    /**
     * Return the binary version.
     *
     * Throws if it could not be determined.
     */
    fun version(): SemVer {
        val raw = exec("version", "--output", "json")
        try {
            val json = Moshi.Builder().build().adapter(Version::class.java).fromJson(raw)
            if (json?.version == null || json.version.isBlank()) {
                throw MissingVersionException("No version found in output")
            }
            return SemVer.parse(json.version)
        } catch (exception: JsonDataException) {
            throw MissingVersionException("No version found in output")
        } catch (exception: EOFException) {
            throw MissingVersionException("No version found in output")
        }
    }

    /**
     * Like version(), but logs errors instead of throwing them.
     */
    private fun tryVersion(): SemVer? = try {
        version()
    } catch (e: Exception) {
        when (e) {
            is InvalidVersionException -> {
                logger.info("Got invalid version from $localBinaryPath: ${e.message}")
            }

            else -> {
                // An error here most likely means the CLI does not exist or
                // it executed successfully but output no version which
                // suggests it is not the right binary.
                logger.info("Unable to determine $localBinaryPath version: ${e.message}")
            }
        }
        null
    }

    /**
     * Returns true if the CLI has the same major/minor/patch version as the
     * provided version, false if it does not match, or null if the CLI version
     * could not be determined because the binary could not be executed or the
     * version could not be parsed.
     */
    fun matchesVersion(rawBuildVersion: String): Boolean? {
        val cliVersion = tryVersion() ?: return null
        val buildVersion =
            try {
                SemVer.parse(rawBuildVersion)
            } catch (e: InvalidVersionException) {
                logger.info("Got invalid build version: $rawBuildVersion")
                return null
            }

        val matches = cliVersion == buildVersion
        logger.info("$localBinaryPath version $cliVersion matches $buildVersion: $matches")
        return matches
    }

    /**
     * Start a workspace.
     *
     * Throws if the command execution fails.
     */
    fun startWorkspace(workspaceOwner: String, workspaceName: String, feats: Features = features): String {
        val args = mutableListOf(
            "--global-config",
            coderConfigPath.toString(),
            "start",
            "--yes",
            workspaceOwner + "/" + workspaceName
        )

        if (feats.buildReason) {
            args.addAll(listOf("--reason", "jetbrains_connection"))
        }

        return exec(*args.toTypedArray())
    }

    private fun exec(vararg args: String): String {
        val stdout =
            ProcessExecutor()
                .command(localBinaryPath.toString(), *args)
                .environment("CODER_HEADER_COMMAND", settings.headerCommand)
                .exitValues(0)
                .readOutput(true)
                .execute()
                .outputUTF8()
        val redactedArgs = listOf(*args).joinToString(" ").replace(tokenRegex, "--token <redacted>")
        logger.info("`$localBinaryPath $redactedArgs`: $stdout")
        return stdout
    }

    val features: Features
        get() {
            val version = tryVersion()
            return if (version == null) {
                Features()
            } else {
                Features(
                    disableAutostart = version >= SemVer(2, 5, 0),
                    reportWorkspaceUsage = version >= SemVer(2, 13, 0),
                    wildcardSSH = version >= SemVer(2, 19, 0),
                    buildReason = version >= SemVer(2, 25, 0),
                )
            }
        }

    /*
     * This function returns the ssh-host-prefix used for Host entries.
     */
    fun getHostPrefix(): String = "coder-jetbrains-${deploymentURL.safeHost()}"

    /**
     * This function returns the ssh host name generated for connecting to the workspace.
     */
    fun getHostName(
        workspace: Workspace,
        currentUser: User,
        agent: WorkspaceAgent,
    ): String = if (features.wildcardSSH) {
        "${getHostPrefix()}--${workspace.ownerName}--${workspace.name}.${agent.name}"
    } else {
        // For a user's own workspace, we use the old syntax without a username for backwards compatibility,
        // since the user might have recent connections that still use the old syntax.
        if (currentUser.username == workspace.ownerName) {
            "coder-jetbrains--${workspace.name}.${agent.name}--${deploymentURL.safeHost()}"
        } else {
            "coder-jetbrains--${workspace.ownerName}--${workspace.name}.${agent.name}--${deploymentURL.safeHost()}"
        }
    }

    fun getBackgroundHostName(
        workspace: Workspace,
        currentUser: User,
        agent: WorkspaceAgent,
    ): String = if (features.wildcardSSH) {
        "${getHostPrefix()}-bg--${workspace.ownerName}--${workspace.name}.${agent.name}"
    } else {
        getHostName(workspace, currentUser, agent) + "--bg"
    }

    companion object {
        val logger = Logger.getInstance(CoderCLIManager::class.java.simpleName)

        private val tokenRegex = "--token [^ ]+".toRegex()

        /**
         * This function returns the identifier for the workspace to pass to the
         * coder ssh proxy command.
         */
        @JvmStatic
        fun getWorkspaceParts(
            workspace: Workspace,
            agent: WorkspaceAgent,
        ): String = "${workspace.ownerName}/${workspace.name}.${agent.name}"

        @JvmStatic
        fun getBackgroundHostName(
            hostname: String,
        ): String {
            val parts = hostname.split("--").toMutableList()
            if (parts.size < 2) {
                throw SSHConfigFormatException("Invalid hostname: $hostname")
            }
            // non-wildcard case
            if (parts[0] == "coder-jetbrains") {
                return hostname + "--bg"
            }
            // wildcard case
            parts[0] += "-bg"
            return parts.joinToString("--")
        }
    }
}
