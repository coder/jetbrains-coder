package com.coder.gateway.util

import java.net.URI
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals

internal class URLExtensionsTest {
    @Test
    fun testToURL() {
        assertEquals(
            URL("https", "localhost", 8080, "/path"),
            "https://localhost:8080/path".toURL(),
        )
    }

    @Test
    fun testWithPath() {
        assertEquals(
            URL("https", "localhost", 8080, "/foo/bar"),
            URL("https", "localhost", 8080, "/").withPath("/foo/bar"),
        )

        assertEquals(
            URL("https", "localhost", 8080, "/foo/bar"),
            URL("https", "localhost", 8080, "/old/path").withPath("/foo/bar"),
        )
    }

    @Test
    fun testSafeHost() {
        assertEquals("foobar", URL("https://foobar:8080").safeHost())
        assertEquals("xn--18j4d", URL("https://ほげ").safeHost())
        assertEquals("test.xn--n28h.invalid", URL("https://test.😉.invalid").safeHost())
        assertEquals("dev.xn---coder-vx74e.com", URL("https://dev.😉-coder.com").safeHost())
    }

    @Test
    fun testToQueryParameters() {
        val tests =
            mapOf(
                "" to mapOf(),
                "?" to mapOf(),
                "&" to mapOf(),
                "?&" to mapOf(),
                "?foo" to mapOf("foo" to ""),
                "?foo=" to mapOf("foo" to ""),
                "?foo&" to mapOf("foo" to ""),
                "?foo=bar" to mapOf("foo" to "bar"),
                "?foo=bar&" to mapOf("foo" to "bar"),
                "?foo=bar&baz" to mapOf("foo" to "bar", "baz" to ""),
                "?foo=bar&baz=" to mapOf("foo" to "bar", "baz" to ""),
                "?foo=bar&baz=qux" to mapOf("foo" to "bar", "baz" to "qux"),
                "?foo=bar=bar2&baz=qux" to mapOf("foo" to "bar=bar2", "baz" to "qux"),
            )
        tests.forEach {
            assertEquals(
                it.value,
                URI("http://dev.coder.com" + it.key).toQueryParameters(),
            )
        }
    }

    @Test
    fun `valid http URL should return Valid`() {
        val uri = URI("http://coder.com")
        val result = uri.validateStrictWebUrl()
        assertEquals(WebUrlValidationResult.Valid, result)
    }

    @Test
    fun `valid https URL with path and query should return Valid`() {
        val uri = URI("https://coder.com/bin/coder-linux-amd64?query=1")
        val result = uri.validateStrictWebUrl()
        assertEquals(WebUrlValidationResult.Valid, result)
    }

    @Test
    fun `relative URL should return Invalid with appropriate message`() {
        val uri = URI("/bin/coder-linux-amd64")
        val result = uri.validateStrictWebUrl()
        assertEquals(
            WebUrlValidationResult.Invalid("$uri is relative, it must be absolute"),
            result
        )
    }

    @Test
    fun `opaque URI like mailto should return Invalid`() {
        val uri = URI("mailto:user@coder.com")
        val result = uri.validateStrictWebUrl()
        assertEquals(
            WebUrlValidationResult.Invalid("$uri is opaque, instead of hierarchical"),
            result
        )
    }

    @Test
    fun `unsupported scheme like ftp should return Invalid`() {
        val uri = URI("ftp://coder.com")
        val result = uri.validateStrictWebUrl()
        assertEquals(
            WebUrlValidationResult.Invalid("Scheme for $uri must be either http or https"),
            result
        )
    }

    @Test
    fun `http URL with missing authority should return Invalid`() {
        val uri = URI("http:///bin/coder-linux-amd64")
        val result = uri.validateStrictWebUrl()
        assertEquals(
            WebUrlValidationResult.Invalid("$uri does not have a hostname"),
            result
        )
    }
}
