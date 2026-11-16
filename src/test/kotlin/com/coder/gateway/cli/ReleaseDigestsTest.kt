package com.coder.gateway.cli

import com.coder.gateway.util.sha256
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class ReleaseDigestsTest {
    @TempDir
    lateinit var tmpdir: Path

    @Test
    fun `matches only the released binary of the version and platform`() {
        val cli = tmpdir.resolve("coder")
        cli.writeText("released")
        val digests = ReleaseDigests {
            mapOf("2.30.1" to mapOf("coder-linux-amd64" to sha256("released".byteInputStream())))
        }

        assertTrue(digests.matches(cli, "2.30.1", "coder-linux-amd64"))
        assertTrue(digests.matches(cli, "v2.30.1+de07351b8", "coder-linux-amd64"))
        assertFalse(digests.matches(cli, "2.30.2", "coder-linux-amd64"))
        assertFalse(digests.matches(cli, "2.30.1", "coder-linux-arm64"))
        assertFalse(digests.matches(cli, "not a version", "coder-linux-amd64"))

        cli.writeText("tampered")
        assertFalse(digests.matches(cli, "2.30.1", "coder-linux-amd64"))
    }

    @Test
    fun `bundled digests hold a SHA-256 digest for every platform of each release`() {
        val platforms = setOf(
            "coder-darwin-amd64",
            "coder-darwin-arm64",
            "coder-linux-amd64",
            "coder-linux-arm64",
            "coder-linux-armv7",
            "coder-windows-amd64.exe",
            "coder-windows-arm64.exe",
        )
        val version = Regex("""\d+\.\d+\.\d+""")
        val digest = Regex("[0-9a-f]{64}")

        val bundled = loadBundledDigests()

        assertTrue(bundled.isNotEmpty())
        bundled.forEach { (release, binaries) ->
            assertTrue(version.matches(release), release)
            assertEquals(platforms, binaries.keys, release)
            binaries.forEach { (name, sha) -> assertTrue(digest.matches(sha), "$release $name") }
        }
    }
}
