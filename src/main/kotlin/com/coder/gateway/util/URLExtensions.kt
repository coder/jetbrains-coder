package com.coder.gateway.util

import com.coder.gateway.util.WebUrlValidationResult.Invalid
import java.net.IDN
import java.net.URI
import java.net.URL


fun String.toURL(): URL = URI.create(this).toURL()

fun URL.withPath(path: String): URL = URL(
    this.protocol,
    this.host,
    this.port,
    if (path.startsWith("/")) path else "/$path",
)


fun String.validateStrictWebUrl(): WebUrlValidationResult = try {
    val uri = URI(this)

    when {
        uri.isOpaque -> Invalid("$this is opaque, instead of hierarchical")
        !uri.isAbsolute -> Invalid("$this is relative, it must be absolute")
        uri.scheme?.lowercase() !in setOf("http", "https") ->
            Invalid("Scheme for $this must be either http or https")

        uri.authority.isNullOrBlank() ->
            Invalid("$this does not have a hostname")
        else -> WebUrlValidationResult.Valid
    }
} catch (e: Exception) {
    Invalid(e.message ?: "$this could not be parsed as a URI reference")
}

sealed class WebUrlValidationResult {
    object Valid : WebUrlValidationResult()
    data class Invalid(val reason: String) : WebUrlValidationResult()
}

/**
 * Return the host, converting IDN to ASCII in case the file system cannot
 * support the necessary character set.
 */
fun URL.safeHost(): String = IDN.toASCII(this.host, IDN.ALLOW_UNASSIGNED)

fun URI.toQueryParameters(): Map<String, String> = (this.query ?: "")
    .split("&").filter {
        it.isNotEmpty()
    }.associate {
        val parts = it.split("=", limit = 2)
        if (parts.size == 2) {
            parts[0] to parts[1]
        } else {
            parts[0] to ""
        }
    }
