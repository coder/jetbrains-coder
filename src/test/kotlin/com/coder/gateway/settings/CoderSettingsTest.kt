package com.coder.gateway.settings

import com.coder.gateway.services.CoderSettingsState
import com.coder.gateway.util.OS
import com.coder.gateway.util.getOS
import com.coder.gateway.util.withPath
import org.junit.Assert.assertNotEquals
import java.net.URL
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

internal class CoderSettingsTest {
    @Test
    fun testExpands() {
        val state = CoderSettingsState()
        val settings = CoderSettings(state)
        val url = URL("http://localhost")
        val home = Path.of(System.getProperty("user.home"))

        state.binaryDirectory = Path.of("~/coder-gateway-test/expand-bin-dir").toString()
        var expected = home.resolve("coder-gateway-test/expand-bin-dir/localhost")
        assertEquals(expected.toAbsolutePath(), settings.binPath(url).parent)

        state.dataDirectory = Path.of("~/coder-gateway-test/expand-data-dir").toString()
        expected = home.resolve("coder-gateway-test/expand-data-dir/localhost")
        assertEquals(expected.toAbsolutePath(), settings.dataDir(url))
    }

    @Test
    fun testDataDir() {
        val state = CoderSettingsState()
        val url = URL("http://localhost")
        var settings = CoderSettings(state,
            env = Environment(mapOf(
                "LOCALAPPDATA"     to "/tmp/coder-gateway-test/localappdata",
                "HOME"             to "/tmp/coder-gateway-test/home",
                "XDG_DATA_HOME"    to "/tmp/coder-gateway-test/xdg-data")))
        var expected = when(getOS()) {
            OS.WINDOWS -> "/tmp/coder-gateway-test/localappdata/coder-gateway/localhost"
            OS.MAC -> "/tmp/coder-gateway-test/home/Library/Application Support/coder-gateway/localhost"
            else -> "/tmp/coder-gateway-test/xdg-data/coder-gateway/localhost"
        }

        assertEquals(Path.of(expected).toAbsolutePath(), settings.dataDir(url))
        assertEquals(Path.of(expected).toAbsolutePath(), settings.binPath(url).parent)

        // Fall back to HOME on Linux.
        if (getOS() == OS.LINUX) {
            settings = CoderSettings(state,
                env = Environment(mapOf(
                    "XDG_DATA_HOME" to "",
                    "HOME" to "/tmp/coder-gateway-test/home")))
            expected = "/tmp/coder-gateway-test/home/.local/share/coder-gateway/localhost"

            assertEquals(Path.of(expected).toAbsolutePath(), settings.dataDir(url))
            assertEquals(Path.of(expected).toAbsolutePath(), settings.binPath(url).parent)
        }

        // Override environment with settings.
        state.dataDirectory = "/tmp/coder-gateway-test/data-dir"
        settings = CoderSettings(state,
            env = Environment(mapOf(
                "LOCALAPPDATA"     to "/ignore",
                "HOME"             to "/ignore",
                "XDG_DATA_HOME"    to "/ignore")))
        expected = "/tmp/coder-gateway-test/data-dir/localhost"
        assertEquals(Path.of(expected).toAbsolutePath(), settings.dataDir(url))
        assertEquals(Path.of(expected).toAbsolutePath(), settings.binPath(url).parent)

        // Check that the URL is encoded and includes the port, also omit environment.
        val newUrl = URL("https://dev.😉-coder.com:8080")
        state.dataDirectory = "/tmp/coder-gateway-test/data-dir"
        settings = CoderSettings(state)
        expected = "/tmp/coder-gateway-test/data-dir/dev.xn---coder-vx74e.com-8080"
        assertEquals(Path.of(expected).toAbsolutePath(), settings.dataDir(newUrl))
        assertEquals(Path.of(expected).toAbsolutePath(), settings.binPath(newUrl).parent)
    }

    @Test
    fun testBinPath() {
        val state = CoderSettingsState()
        val settings = CoderSettings(state)
        val settings2 = CoderSettings(state, binaryName = "foo-bar.baz")
        // The binary path should fall back to the data directory but that is
        // already tested in the data directory tests.
        val url = URL("http://localhost")

        // Override with settings.
        state.binaryDirectory = "/tmp/coder-gateway-test/bin-dir"
        var expected = "/tmp/coder-gateway-test/bin-dir/localhost"
        assertEquals(Path.of(expected).toAbsolutePath(), settings.binPath(url).parent)
        assertEquals(Path.of(expected).toAbsolutePath(), settings2.binPath(url).parent)

        // Second argument bypasses override.
        state.dataDirectory = "/tmp/coder-gateway-test/data-dir"
        expected = "/tmp/coder-gateway-test/data-dir/localhost"
        assertEquals(Path.of(expected).toAbsolutePath(), settings.binPath(url, true).parent)
        assertEquals(Path.of(expected).toAbsolutePath(), settings2.binPath(url, true).parent)

        assertNotEquals("foo-bar.baz", settings.binPath(url).fileName)
        assertEquals("foo-bar.baz", settings2.binPath(url).fileName.toString())
    }

    @Test
    fun testCoderConfigDir() {
        val state = CoderSettingsState()
        var settings = CoderSettings(state,
            env = Environment(mapOf(
                "APPDATA"        to "/tmp/coder-gateway-test/cli-appdata",
                "HOME"             to "/tmp/coder-gateway-test/cli-home",
                "XDG_CONFIG_HOME"  to "/tmp/coder-gateway-test/cli-xdg-config")))
        var expected = when(getOS()) {
            OS.WINDOWS -> "/tmp/coder-gateway-test/cli-appdata/coderv2"
            OS.MAC -> "/tmp/coder-gateway-test/cli-home/Library/Application Support/coderv2"
            else -> "/tmp/coder-gateway-test/cli-xdg-config/coderv2"
        }
        assertEquals(Path.of(expected), settings.coderConfigDir)

        // Fall back to HOME on Linux.
        if (getOS() == OS.LINUX) {
            settings = CoderSettings(state,
                env = Environment(mapOf(
                    "XDG_CONFIG_HOME" to "",
                    "HOME" to "/tmp/coder-gateway-test/cli-home")))
            expected = "/tmp/coder-gateway-test/cli-home/.config/coderv2"
            assertEquals(Path.of(expected), settings.coderConfigDir)
        }

        // Read CODER_CONFIG_DIR.
        settings = CoderSettings(state,
            env = Environment(mapOf(
                "CODER_CONFIG_DIR" to "/tmp/coder-gateway-test/coder-config-dir",
                "APPDATA"          to "/ignore",
                "HOME"             to "/ignore",
                "XDG_CONFIG_HOME"  to "/ignore")))
        expected = "/tmp/coder-gateway-test/coder-config-dir"
        assertEquals(Path.of(expected), settings.coderConfigDir)
    }

    @Test
    fun binSource() {
        val state = CoderSettingsState()
        val settings = CoderSettings(state)
        // As-is if no source override.
        val url = URL("http://localhost/")
        assertContains(settings.binSource(url).toString(),
            url.withPath("/bin/coder-").toString())

        // Override with absolute URL.
        val absolute = URL("http://dev.coder.com/some-path")
        state.binarySource = absolute.toString()
        assertEquals(absolute, settings.binSource(url))

        // Override with relative URL.
        state.binarySource = "/relative/path"
        assertEquals(url.withPath("/relative/path"), settings.binSource(url))
    }

    @Test
    fun testReadConfig() {
        val tmp = Path.of(System.getProperty("java.io.tmpdir"))

        val expected = tmp.resolve("coder-gateway-test/test-config")
        expected.toFile().mkdirs()
        expected.resolve("url").toFile().writeText("http://test.gateway.coder.com$expected")
        expected.resolve("session").toFile().writeText("fake-token")

        val got = CoderSettings(CoderSettingsState()).readConfig(expected)
        assertEquals(Pair("http://test.gateway.coder.com$expected", "fake-token"), got)
    }

    @Test
    fun testSettings() {
        // Make sure the remaining settings are being conveyed.
        val settings = CoderSettings(
            CoderSettingsState(
                enableDownloads = false,
                enableBinaryDirectoryFallback = true,
                headerCommand = "test header",
                tlsCertPath = "tls cert path",
                tlsKeyPath = "tls key path",
                tlsCAPath = "tls ca path",
                tlsAlternateHostname = "tls alt hostname",
                disableAutostart = true,
            )
        )

        assertEquals(false, settings.enableDownloads)
        assertEquals(true, settings.enableBinaryDirectoryFallback)
        assertEquals("test header", settings.headerCommand)
        assertEquals("tls cert path", settings.tls.certPath)
        assertEquals("tls key path", settings.tls.keyPath)
        assertEquals("tls ca path", settings.tls.caPath)
        assertEquals("tls alt hostname", settings.tls.altHostname)
        assertEquals(true, settings.disableAutostart)
    }
}
