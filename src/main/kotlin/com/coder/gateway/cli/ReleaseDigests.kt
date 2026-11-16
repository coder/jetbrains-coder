package com.coder.gateway.cli

import com.coder.gateway.util.InvalidVersionException
import com.coder.gateway.util.SemVer
import com.coder.gateway.util.sha256
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.nio.file.Path
import kotlin.io.path.inputStream

private const val BUNDLED_DIGESTS = "/META-INF/trusted-releases/release-digests.json"

/**
 * SHA-256 digests of the Coder CLI binaries in releases that are not signed
 * with the current release key, keyed by version (major.minor.patch) and then
 * by binary name (for example coder-linux-amd64). Defaults to the digests
 * bundled with the plugin.
 */
class ReleaseDigests(loadDigests: () -> Map<String, Map<String, String>> = ::loadBundledDigests) {
    private val digests by lazy(loadDigests)

    /**
     * Whether the file at [cli] is the released [binaryName] for [buildVersion].
     * A binary from a different version or platform does not match.
     */
    fun matches(cli: Path, buildVersion: String, binaryName: String): Boolean {
        val version = try {
            SemVer.parse(buildVersion)
        } catch (_: InvalidVersionException) {
            return false
        }
        val expected = digests["${version.major}.${version.minor}.${version.patch}"]?.get(binaryName) ?: return false
        return sha256(cli.inputStream()) == expected
    }
}

internal fun loadBundledDigests(): Map<String, Map<String, String>> {
    val json = ReleaseDigests::class.java.getResourceAsStream(BUNDLED_DIGESTS)?.use { it.reader().readText() }
        ?: throw IllegalStateException("Release digests not found")
    val type = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        Types.newParameterizedType(Map::class.java, String::class.java, String::class.java),
    )
    return Moshi.Builder().build().adapter<Map<String, Map<String, String>>>(type).fromJson(json)
        ?: throw IllegalStateException("Release digests are empty")
}
