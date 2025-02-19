package com.coder.gateway.cli

import com.coder.gateway.cli.ex.MissingVersionException
import com.coder.gateway.cli.ex.ResponseException
import com.coder.gateway.cli.ex.SSHConfigFormatException
import com.coder.gateway.sdk.DataGen
import com.coder.gateway.sdk.DataGen.Companion.workspace
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.settings.CODER_SSH_CONFIG_OPTIONS
import com.coder.gateway.settings.CoderSettings
import com.coder.gateway.settings.CoderSettingsState
import com.coder.gateway.settings.Environment
import com.coder.gateway.util.InvalidVersionException
import com.coder.gateway.util.OS
import com.coder.gateway.util.SemVer
import com.coder.gateway.util.escape
import com.coder.gateway.util.getOS
import com.coder.gateway.util.sha1
import com.coder.gateway.util.toURL
import com.squareup.moshi.JsonEncodingException
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.assertDoesNotThrow
import org.zeroturnaround.exec.InvalidExitValueException
import org.zeroturnaround.exec.ProcessInitException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.nio.file.AccessDeniedException
import java.nio.file.Path
import java.util.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

internal class CoderCLIManagerTest {
    /**
     * Return the contents of a script that contains the string.
     */
    private fun mkbin(str: String): String = if (getOS() == OS.WINDOWS) {
        // Must use a .bat extension for this to work.
        listOf("@echo off", str)
    } else {
        listOf("#!/bin/sh", str)
    }.joinToString(System.lineSeparator())

    /**
     * Return the contents of a script that outputs JSON containing the version.
     */
    private fun mkbinVersion(version: String): String = mkbin(echo("""{"version": "$version"}"""))

    private fun mockServer(
        errorCode: Int = 0,
        version: String? = null,
    ): Pair<HttpServer, URL> {
        val srv = HttpServer.create(InetSocketAddress(0), 0)
        srv.createContext("/") { exchange ->
            var code = HttpURLConnection.HTTP_OK
            var response = mkbinVersion(version ?: "${srv.address.port}.0.0")
            val eTags = exchange.requestHeaders["If-None-Match"]
            if (exchange.requestURI.path == "/bin/override") {
                code = HttpURLConnection.HTTP_OK
                response = mkbinVersion("0.0.0")
            } else if (!exchange.requestURI.path.startsWith("/bin/coder-")) {
                code = HttpURLConnection.HTTP_NOT_FOUND
                response = "not found"
            } else if (errorCode != 0) {
                code = errorCode
                response = "error code $code"
            } else if (eTags != null && eTags.contains("\"${sha1(response.byteInputStream())}\"")) {
                code = HttpURLConnection.HTTP_NOT_MODIFIED
                response = "not modified"
            }

            val body = response.toByteArray()
            exchange.sendResponseHeaders(code, if (code == HttpURLConnection.HTTP_OK) body.size.toLong() else -1)
            exchange.responseBody.write(body)
            exchange.close()
        }
        srv.start()
        return Pair(srv, URL("http://localhost:" + srv.address.port))
    }

    @Test
    fun testServerInternalError() {
        val (srv, url) = mockServer(HttpURLConnection.HTTP_INTERNAL_ERROR)
        val ccm = CoderCLIManager(url)

        val ex =
            assertFailsWith(
                exceptionClass = ResponseException::class,
                block = { ccm.download() },
            )
        assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, ex.code)

        srv.stop(0)
    }

    @Test
    fun testUsesSettings() {
        val settings =
            CoderSettings(
                CoderSettingsState(
                    dataDirectory = tmpdir.resolve("cli-data-dir").toString(),
                    binaryDirectory = tmpdir.resolve("cli-bin-dir").toString(),
                ),
            )
        val url = URL("http://localhost")

        val ccm1 = CoderCLIManager(url, settings)
        assertEquals(settings.binSource(url), ccm1.remoteBinaryURL)
        assertEquals(settings.dataDir(url), ccm1.coderConfigPath.parent)
        assertEquals(settings.binPath(url), ccm1.localBinaryPath)

        // Can force using data directory.
        val ccm2 = CoderCLIManager(url, settings, true)
        assertEquals(settings.binSource(url), ccm2.remoteBinaryURL)
        assertEquals(settings.dataDir(url), ccm2.coderConfigPath.parent)
        assertEquals(settings.binPath(url, true), ccm2.localBinaryPath)
    }

    @Test
    fun testFailsToWrite() {
        if (getOS() == OS.WINDOWS) {
            return // setWritable(false) does not work the same way on Windows.
        }

        val (srv, url) = mockServer()
        val ccm =
            CoderCLIManager(
                url,
                CoderSettings(
                    CoderSettingsState(
                        dataDirectory = tmpdir.resolve("cli-dir-fail-to-write").toString(),
                    ),
                ),
            )

        ccm.localBinaryPath.parent.toFile().mkdirs()
        ccm.localBinaryPath.parent.toFile().setWritable(false)

        assertFailsWith(
            exceptionClass = AccessDeniedException::class,
            block = { ccm.download() },
        )

        srv.stop(0)
    }

    // This test uses a real deployment if possible to make sure we really
    // download a working CLI and that it runs on each platform.
    @Test
    fun testDownloadRealCLI() {
        var url = System.getenv("CODER_GATEWAY_TEST_DEPLOYMENT")
        if (url == "mock") {
            return
        } else if (url == null) {
            url = "https://dev.coder.com"
        }

        val ccm =
            CoderCLIManager(
                url.toURL(),
                CoderSettings(
                    CoderSettingsState(
                        dataDirectory = tmpdir.resolve("real-cli").toString(),
                    ),
                ),
            )

        assertTrue(ccm.download())
        assertDoesNotThrow { ccm.version() }

        // It should skip the second attempt.
        assertFalse(ccm.download())

        // Make sure login failures propagate.
        assertFailsWith(
            exceptionClass = InvalidExitValueException::class,
            block = { ccm.login("jetbrains-ci-test") },
        )
    }

    @Test
    fun testDownloadMockCLI() {
        val (srv, url) = mockServer()
        var ccm =
            CoderCLIManager(
                url,
                CoderSettings(
                    CoderSettingsState(
                        dataDirectory = tmpdir.resolve("mock-cli").toString(),
                    ),
                    binaryName = "coder.bat",
                ),
            )

        assertEquals(true, ccm.download())
        assertEquals(SemVer(url.port.toLong(), 0, 0), ccm.version())

        // It should skip the second attempt.
        assertEquals(false, ccm.download())

        // Should use the source override.
        ccm =
            CoderCLIManager(
                url,
                CoderSettings(
                    CoderSettingsState(
                        binarySource = "/bin/override",
                        dataDirectory = tmpdir.resolve("mock-cli").toString(),
                    ),
                ),
            )

        assertEquals(true, ccm.download())
        assertContains(ccm.localBinaryPath.toFile().readText(), "0.0.0")

        srv.stop(0)
    }

    @Test
    fun testRunNonExistentBinary() {
        val ccm =
            CoderCLIManager(
                URL("https://foo"),
                CoderSettings(
                    CoderSettingsState(
                        dataDirectory = tmpdir.resolve("does-not-exist").toString(),
                    ),
                ),
            )

        assertFailsWith(
            exceptionClass = ProcessInitException::class,
            block = { ccm.login("fake-token") },
        )
    }

    @Test
    fun testOverwitesWrongVersion() {
        val (srv, url) = mockServer()
        val ccm =
            CoderCLIManager(
                url,
                CoderSettings(
                    CoderSettingsState(
                        dataDirectory = tmpdir.resolve("overwrite-cli").toString(),
                    ),
                ),
            )

        ccm.localBinaryPath.parent.toFile().mkdirs()
        ccm.localBinaryPath.toFile().writeText("cli")
        ccm.localBinaryPath.toFile().setLastModified(0)

        assertEquals("cli", ccm.localBinaryPath.toFile().readText())
        assertEquals(0, ccm.localBinaryPath.toFile().lastModified())

        assertTrue(ccm.download())

        assertNotEquals("cli", ccm.localBinaryPath.toFile().readText())
        assertNotEquals(0, ccm.localBinaryPath.toFile().lastModified())
        assertContains(ccm.localBinaryPath.toFile().readText(), url.port.toString())

        srv.stop(0)
    }

    @Test
    fun testMultipleDeployments() {
        val (srv1, url1) = mockServer()
        val (srv2, url2) = mockServer()

        val settings =
            CoderSettings(
                CoderSettingsState(
                    dataDirectory = tmpdir.resolve("clobber-cli").toString(),
                ),
            )

        val ccm1 = CoderCLIManager(url1, settings)
        val ccm2 = CoderCLIManager(url2, settings)

        assertTrue(ccm1.download())
        assertTrue(ccm2.download())

        srv1.stop(0)
        srv2.stop(0)
    }

    data class SSHTest(
        val workspaces: List<Workspace>,
        val input: String?,
        val output: String,
        val remove: String,
        val headerCommand: String = "",
        val disableAutostart: Boolean = false,
        // Default to the most common feature settings.
        val features: Features = Features(
            disableAutostart = false,
            reportWorkspaceUsage = true,
        ),
        val extraConfig: String = "",
        val env: Environment = Environment(),
        val sshLogDirectory: Path? = null,
        val url: URL? = null,
    )

    @Test
    fun testConfigureSSH() {
        val workspace = workspace("foo", agents = mapOf("agent1" to UUID.randomUUID().toString()))
        val workspace2 = workspace("bar", agents = mapOf("agent1" to UUID.randomUUID().toString()))
        val betterWorkspace = workspace("foo", agents = mapOf("agent1" to UUID.randomUUID().toString()), ownerName = "bettertester")
        val workspaceWithMultipleAgents = workspace("foo", agents = mapOf("agent1" to UUID.randomUUID().toString(), "agent2" to UUID.randomUUID().toString()))

        val extraConfig =
            listOf(
                "ServerAliveInterval 5",
                "ServerAliveCountMax 3",
            ).joinToString(System.lineSeparator())
        val tests =
            listOf(
                SSHTest(listOf(workspace, workspace2), null, "multiple-workspaces", "blank"),
                SSHTest(listOf(workspace, workspace2), null, "multiple-workspaces", "blank"),
                SSHTest(listOf(workspace), "blank", "append-blank", "blank"),
                SSHTest(listOf(workspace), "blank-newlines", "append-blank-newlines", "blank"),
                SSHTest(listOf(workspace), "existing-end", "replace-end", "no-blocks"),
                SSHTest(listOf(workspace), "existing-end-no-newline", "replace-end-no-newline", "no-blocks"),
                SSHTest(listOf(workspace), "existing-middle", "replace-middle", "no-blocks"),
                SSHTest(listOf(workspace), "existing-middle-and-unrelated", "replace-middle-ignore-unrelated", "no-related-blocks"),
                SSHTest(listOf(workspace), "existing-only", "replace-only", "blank"),
                SSHTest(listOf(workspace), "existing-start", "replace-start", "no-blocks"),
                SSHTest(listOf(workspace), "no-blocks", "append-no-blocks", "no-blocks"),
                SSHTest(listOf(workspace), "no-related-blocks", "append-no-related-blocks", "no-related-blocks"),
                SSHTest(listOf(workspace), "no-newline", "append-no-newline", "no-blocks"),
                if (getOS() == OS.WINDOWS) {
                    SSHTest(
                        listOf(workspace),
                        null,
                        "header-command-windows",
                        "blank",
                        """"C:\Program Files\My Header Command\HeaderCommand.exe" --url="%CODER_URL%" --test="foo bar"""",
                    )
                } else {
                    SSHTest(
                        listOf(workspace),
                        null,
                        "header-command",
                        "blank",
                        "my-header-command --url=\"\$CODER_URL\" --test=\"foo bar\" --literal='\$CODER_URL'",
                    )
                },
                SSHTest(
                    listOf(workspace),
                    null,
                    "disable-autostart",
                    "blank",
                    "",
                    true,
                    Features(
                        disableAutostart = true,
                        reportWorkspaceUsage = true,
                    ),
                ),
                SSHTest(listOf(workspace), null, "no-disable-autostart", "blank", ""),
                SSHTest(
                    listOf(workspace),
                    null,
                    "no-report-usage",
                    "blank",
                    "",
                    true,
                    Features(
                        disableAutostart = false,
                        reportWorkspaceUsage = false,
                    ),
                ),
                SSHTest(
                    listOf(workspace),
                    null,
                    "extra-config",
                    "blank",
                    extraConfig = extraConfig,
                ),
                SSHTest(
                    listOf(workspace),
                    null,
                    "extra-config",
                    "blank",
                    env = Environment(mapOf(CODER_SSH_CONFIG_OPTIONS to extraConfig)),
                ),
                SSHTest(
                    listOf(workspace),
                    null,
                    "log-dir",
                    "blank",
                    sshLogDirectory = tmpdir.resolve("ssh-logs"),
                ),
                SSHTest(
                    listOf(workspace),
                    input = null,
                    output = "url",
                    remove = "blank",
                    url = URL("https://test.coder.invalid?foo=bar&baz=qux"),
                ),
                SSHTest(
                    listOf(workspace, betterWorkspace),
                    input = null,
                    output = "multiple-users",
                    remove = "blank",
                ),
                SSHTest(
                    listOf(workspaceWithMultipleAgents),
                    input = null,
                    output = "multiple-agents",
                    remove = "blank",
                ),
                SSHTest(
                    listOf(workspace),
                    input = null,
                    output = "wildcard",
                    remove = "wildcard",
                    features = Features(
                        wildcardSSH = true,
                    ),
                ),
            )

        val newlineRe = "\r?\n".toRegex()

        tests.forEach {
            val settings =
                CoderSettings(
                    CoderSettingsState(
                        disableAutostart = it.disableAutostart,
                        dataDirectory = tmpdir.resolve("configure-ssh").toString(),
                        headerCommand = it.headerCommand,
                        sshConfigOptions = it.extraConfig,
                        sshLogDirectory = it.sshLogDirectory?.toString() ?: "",
                    ),
                    sshConfigPath = tmpdir.resolve(it.input + "_to_" + it.output + ".conf"),
                    env = it.env,
                )

            val ccm = CoderCLIManager(it.url ?: URL("https://test.coder.invalid"), settings)

            // Input is the configuration that we start with, if any.
            if (it.input != null) {
                settings.sshConfigPath.parent.toFile().mkdirs()
                val originalConf =
                    Path.of("src/test/fixtures/inputs").resolve(it.input + ".conf").toFile().readText()
                        .replace(newlineRe, System.lineSeparator())
                settings.sshConfigPath.toFile().writeText(originalConf)
            }

            // Output is the configuration we expect to have after configuring.
            val coderConfigPath = ccm.localBinaryPath.parent.resolve("config")
            val expectedConf =
                Path.of("src/test/fixtures/outputs/").resolve(it.output + ".conf").toFile().readText()
                    .replace(newlineRe, System.lineSeparator())
                    .replace("/tmp/coder-gateway/test.coder.invalid/config", escape(coderConfigPath.toString()))
                    .replace("/tmp/coder-gateway/test.coder.invalid/coder-linux-amd64", escape(ccm.localBinaryPath.toString()))
                    .let { conf ->
                        if (it.sshLogDirectory != null) {
                            conf.replace("/tmp/coder-gateway/test.coder.invalid/logs", it.sshLogDirectory.toString())
                        } else {
                            conf
                        }
                    }

            val inputConf =
                Path.of("src/test/fixtures/inputs/").resolve(it.remove + ".conf").toFile().readText()
                    .replace(newlineRe, System.lineSeparator())
                    .replace("/tmp/coder-gateway/test.coder.invalid/config", escape(coderConfigPath.toString()))
                    .replace("/tmp/coder-gateway/test.coder.invalid/coder-linux-amd64", escape(ccm.localBinaryPath.toString()))
                    .let { conf ->
                        if (it.sshLogDirectory != null) {
                            conf.replace("/tmp/coder-gateway/test.coder.invalid/logs", it.sshLogDirectory.toString())
                        } else {
                            conf
                        }
                    }

            // Add workspaces.
            ccm.configSsh(
                it.workspaces.flatMap { ws ->
                    ws.latestBuild.resources.filter { r -> r.agents != null }.flatMap { r -> r.agents!! }.map { a ->
                        ws to a
                    }
                }.toSet(),
                DataGen.user(),
                it.features,
            )

            assertEquals(expectedConf, settings.sshConfigPath.toFile().readText())

            // SSH log directory should have been created.
            if (it.sshLogDirectory != null) {
                assertTrue(it.sshLogDirectory.toFile().exists())
            }

            // Remove configuration.
            ccm.configSsh(emptySet(), DataGen.user(), it.features)

            // Remove is the configuration we expect after removing.
            assertEquals(
                settings.sshConfigPath.toFile().readText(),
                inputConf
            )
        }
    }

    @Test
    fun testMalformedConfig() {
        val tests =
            listOf(
                "malformed-mismatched-start",
                "malformed-no-end",
                "malformed-no-start",
                "malformed-start-after-end",
            )

        tests.forEach {
            val settings =
                CoderSettings(
                    CoderSettingsState(),
                    sshConfigPath = tmpdir.resolve("configured$it.conf"),
                )
            settings.sshConfigPath.parent.toFile().mkdirs()
            Path.of("src/test/fixtures/inputs").resolve("$it.conf").toFile().copyTo(
                settings.sshConfigPath.toFile(),
                true,
            )

            val ccm = CoderCLIManager(URL("https://test.coder.invalid"), settings)

            assertFailsWith(
                exceptionClass = SSHConfigFormatException::class,
                block = { ccm.configSsh(emptySet(), DataGen.user()) },
            )
        }
    }

    @Test
    fun testMalformedHeader() {
        val tests =
            listOf(
                "new\nline",
            )

        val workspace = workspace("foo", agents = mapOf("agentid1" to UUID.randomUUID().toString(), "agentid2" to UUID.randomUUID().toString()))
        val withAgents = workspace.latestBuild.resources.filter { it.agents != null }.flatMap { it.agents!! }.map {
            workspace to it
        }

        tests.forEach {
            val ccm =
                CoderCLIManager(
                    URL("https://test.coder.invalid"),
                    CoderSettings(
                        CoderSettingsState(
                            headerCommand = it,
                        ),
                    ),
                )

            assertFailsWith(
                exceptionClass = Exception::class,
                block = { ccm.configSsh(withAgents.toSet(), DataGen.user()) },
            )
        }
    }

    /**
     * Return an echo command for the OS.
     */
    private fun echo(str: String): String = if (getOS() == OS.WINDOWS) {
        "echo $str"
    } else {
        "echo '$str'"
    }

    /**
     * Return an exit command for the OS.
     */
    private fun exit(code: Number): String = if (getOS() == OS.WINDOWS) {
        "exit /b $code"
    } else {
        "exit $code"
    }

    @Test
    fun testFailVersionParse() {
        val tests =
            mapOf(
                null to ProcessInitException::class,
                echo("""{"foo": true, "baz": 1}""") to MissingVersionException::class,
                echo("""{"version": ""}""") to MissingVersionException::class,
                echo("""v0.0.1""") to JsonEncodingException::class,
                echo("""{"version: """) to JsonEncodingException::class,
                echo("""{"version": "invalid"}""") to InvalidVersionException::class,
                exit(0) to MissingVersionException::class,
                exit(1) to InvalidExitValueException::class,
            )

        val ccm =
            CoderCLIManager(
                URL("https://test.coder.parse-fail.invalid"),
                CoderSettings(
                    CoderSettingsState(
                        binaryDirectory = tmpdir.resolve("bad-version").toString(),
                    ),
                    binaryName = "coder.bat",
                ),
            )
        ccm.localBinaryPath.parent.toFile().mkdirs()

        tests.forEach {
            if (it.key == null) {
                ccm.localBinaryPath.toFile().deleteRecursively()
            } else {
                ccm.localBinaryPath.toFile().writeText(mkbin(it.key!!))
                if (getOS() != OS.WINDOWS) {
                    ccm.localBinaryPath.toFile().setExecutable(true)
                }
            }
            assertFailsWith(
                exceptionClass = it.value,
                block = { ccm.version() },
            )
        }
    }

    @Test
    fun testMatchesVersion() {
        val test =
            listOf(
                Triple(null, "v1.0.0", null),
                Triple(echo("""{"version": "v1.0.0"}"""), "v1.0.0", true),
                Triple(echo("""{"version": "v1.0.0", "foo": "bar"}"""), "v1.0.0", true),
                Triple(echo("""{"version": "v1.0.0"}"""), "v1.0.0-devel+b5b5b5b5", true),
                Triple(echo("""{"version": "v1.0.0-devel+b5b5b5b5"}"""), "v1.0.0-devel+b5b5b5b5", true),
                Triple(echo("""{"version": "v1.0.0-devel+b5b5b5b5"}"""), "v1.0.0", true),
                Triple(echo("""{"version": "v1.0.0-devel+b5b5b5b5"}"""), "v1.0.0-devel+c6c6c6c6", true),
                Triple(echo("""{"version": "v1.0.0-prod+b5b5b5b5"}"""), "v1.0.0-devel+b5b5b5b5", true),
                Triple(echo("""{"version": "v1.0.0"}"""), "v1.0.1", false),
                Triple(echo("""{"version": "v1.0.0"}"""), "v1.1.0", false),
                Triple(echo("""{"version": "v1.0.0"}"""), "v2.0.0", false),
                Triple(echo("""{"version": "v1.0.0"}"""), "v0.0.0", false),
                Triple(echo("""{"version": ""}"""), "v1.0.0", null),
                Triple(echo("""{"version": "v1.0.0"}"""), "", null),
                Triple(echo("""{"version"""), "v1.0.0", null),
                Triple(exit(0), "v1.0.0", null),
                Triple(exit(1), "v1.0.0", null),
            )

        val ccm =
            CoderCLIManager(
                URL("https://test.coder.matches-version.invalid"),
                CoderSettings(
                    CoderSettingsState(
                        binaryDirectory = tmpdir.resolve("matches-version").toString(),
                    ),
                    binaryName = "coder.bat",
                ),
            )
        ccm.localBinaryPath.parent.toFile().mkdirs()

        test.forEach {
            if (it.first == null) {
                ccm.localBinaryPath.toFile().deleteRecursively()
            } else {
                ccm.localBinaryPath.toFile().writeText(mkbin(it.first!!))
                if (getOS() != OS.WINDOWS) {
                    ccm.localBinaryPath.toFile().setExecutable(true)
                }
            }

            assertEquals(it.third, ccm.matchesVersion(it.second), it.first)
        }
    }

    enum class Result {
        ERROR, // Tried to download but got an error.
        NONE, // Skipped download; binary does not exist.
        DL_BIN, // Downloaded the binary to bin.
        DL_DATA, // Downloaded the binary to data.
        USE_BIN, // Used existing binary in bin.
        USE_DATA, // Used existing binary in data.
    }

    data class EnsureCLITest(
        val version: String?,
        val fallbackVersion: String?,
        val buildVersion: String,
        val writable: Boolean,
        val enableDownloads: Boolean,
        val enableFallback: Boolean,
        val expect: Result,
    )

    @Test
    fun testEnsureCLI() {
        if (getOS() == OS.WINDOWS) {
            // TODO: setWritable() does not work the same way on Windows but we
            //       should test what we can.
            return
        }

        @Suppress("BooleanLiteralArgument")
        val tests =
            listOf(
                // CLI is writable.
                EnsureCLITest(null, null, "1.0.0", true, true, true, Result.DL_BIN), // Download.
                EnsureCLITest(null, null, "1.0.0", true, false, true, Result.NONE), // No download, error when used.
                EnsureCLITest("1.0.1", null, "1.0.0", true, true, true, Result.DL_BIN), // Update.
                EnsureCLITest("1.0.1", null, "1.0.0", true, false, true, Result.USE_BIN), // No update, use outdated.
                EnsureCLITest("1.0.0", null, "1.0.0", true, false, true, Result.USE_BIN), // Use existing.
                // CLI is *not* writable and fallback disabled.
                EnsureCLITest(null, null, "1.0.0", false, true, false, Result.ERROR), // Fail to download.
                EnsureCLITest(null, null, "1.0.0", false, false, false, Result.NONE), // No download, error when used.
                EnsureCLITest("1.0.1", null, "1.0.0", false, true, false, Result.ERROR), // Fail to update.
                EnsureCLITest("1.0.1", null, "1.0.0", false, false, false, Result.USE_BIN), // No update, use outdated.
                EnsureCLITest("1.0.0", null, "1.0.0", false, false, false, Result.USE_BIN), // Use existing.
                // CLI is *not* writable and fallback enabled.
                EnsureCLITest(null, null, "1.0.0", false, true, true, Result.DL_DATA), // Download to fallback.
                EnsureCLITest(null, null, "1.0.0", false, false, true, Result.NONE), // No download, error when used.
                EnsureCLITest("1.0.1", "1.0.1", "1.0.0", false, true, true, Result.DL_DATA), // Update fallback.
                EnsureCLITest("1.0.1", "1.0.2", "1.0.0", false, false, true, Result.USE_BIN), // No update, use outdated.
                EnsureCLITest(null, "1.0.2", "1.0.0", false, false, true, Result.USE_DATA), // No update, use outdated fallback.
                EnsureCLITest("1.0.0", null, "1.0.0", false, false, true, Result.USE_BIN), // Use existing.
                EnsureCLITest("1.0.1", "1.0.0", "1.0.0", false, false, true, Result.USE_DATA), // Use existing fallback.
            )

        val (srv, url) = mockServer()

        tests.forEach {
            val settings =
                CoderSettings(
                    CoderSettingsState(
                        enableDownloads = it.enableDownloads,
                        enableBinaryDirectoryFallback = it.enableFallback,
                        dataDirectory = tmpdir.resolve("ensure-data-dir").toString(),
                        binaryDirectory = tmpdir.resolve("ensure-bin-dir").toString(),
                    ),
                )

            // Clean up from previous test.
            tmpdir.resolve("ensure-data-dir").toFile().deleteRecursively()
            tmpdir.resolve("ensure-bin-dir").toFile().deleteRecursively()

            // Create a binary in the regular location.
            if (it.version != null) {
                settings.binPath(url).parent.toFile().mkdirs()
                settings.binPath(url).toFile().writeText(mkbinVersion(it.version))
                settings.binPath(url).toFile().setExecutable(true)
            }

            // This not being writable will make it fall back, if enabled.
            if (!it.writable) {
                settings.binPath(url).parent.toFile().mkdirs()
                settings.binPath(url).parent.toFile().setWritable(false)
            }

            // Create a binary in the fallback location.
            if (it.fallbackVersion != null) {
                settings.binPath(url, true).parent.toFile().mkdirs()
                settings.binPath(url, true).toFile().writeText(mkbinVersion(it.fallbackVersion))
                settings.binPath(url, true).toFile().setExecutable(true)
            }

            when (it.expect) {
                Result.ERROR -> {
                    assertFailsWith(
                        exceptionClass = AccessDeniedException::class,
                        block = { ensureCLI(url, it.buildVersion, settings) },
                    )
                }
                Result.NONE -> {
                    val ccm = ensureCLI(url, it.buildVersion, settings)
                    assertEquals(settings.binPath(url), ccm.localBinaryPath)
                    assertFailsWith(
                        exceptionClass = ProcessInitException::class,
                        block = { ccm.version() },
                    )
                }
                Result.DL_BIN -> {
                    val ccm = ensureCLI(url, it.buildVersion, settings)
                    assertEquals(settings.binPath(url), ccm.localBinaryPath)
                    assertEquals(SemVer(url.port.toLong(), 0, 0), ccm.version())
                }
                Result.DL_DATA -> {
                    val ccm = ensureCLI(url, it.buildVersion, settings)
                    assertEquals(settings.binPath(url, true), ccm.localBinaryPath)
                    assertEquals(SemVer(url.port.toLong(), 0, 0), ccm.version())
                }
                Result.USE_BIN -> {
                    val ccm = ensureCLI(url, it.buildVersion, settings)
                    assertEquals(settings.binPath(url), ccm.localBinaryPath)
                    assertEquals(SemVer.parse(it.version ?: ""), ccm.version())
                }
                Result.USE_DATA -> {
                    val ccm = ensureCLI(url, it.buildVersion, settings)
                    assertEquals(settings.binPath(url, true), ccm.localBinaryPath)
                    assertEquals(SemVer.parse(it.fallbackVersion ?: ""), ccm.version())
                }
            }

            // Make writable again so it can get cleaned up.
            if (!it.writable) {
                settings.binPath(url).parent.toFile().setWritable(true)
            }
        }

        srv.stop(0)
    }

    @Test
    fun testFeatures() {
        val tests =
            listOf(
                Pair("2.5.0", Features(true)),
                Pair("2.13.0", Features(true, true)),
                Pair("4.9.0", Features(true, true, true)),
                Pair("2.4.9", Features(false)),
                Pair("1.0.1", Features(false)),
            )

        tests.forEach {
            val (srv, url) = mockServer(version = it.first)
            val ccm =
                CoderCLIManager(
                    url,
                    CoderSettings(
                        CoderSettingsState(
                            dataDirectory = tmpdir.resolve("features").toString(),
                        ),
                        binaryName = "coder.bat",
                    ),
                )
            assertEquals(true, ccm.download())
            assertEquals(it.second, ccm.features, "version: ${it.first}")

            srv.stop(0)
        }
    }

    companion object {
        private val tmpdir: Path = Path.of(System.getProperty("java.io.tmpdir")).resolve("coder-gateway-test/cli-manager")

        @JvmStatic
        @BeforeAll
        fun cleanup() {
            // Clean up from previous runs otherwise they get cluttered since the
            // mock server port is random.
            tmpdir.toFile().deleteRecursively()
        }
    }
}
