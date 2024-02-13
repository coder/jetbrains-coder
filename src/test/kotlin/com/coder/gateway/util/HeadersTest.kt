package com.coder.gateway.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

import java.net.URL

internal class HeadersTest {
    @Test
    fun testGetHeadersOK() {
        val tests = mapOf(
            null to emptyMap(),
            "" to emptyMap(),
            "printf 'foo=bar\\nbaz=qux'"    to mapOf("foo" to "bar", "baz" to "qux"),
            "printf 'foo=bar\\r\\nbaz=qux'" to mapOf("foo" to "bar", "baz" to "qux"),
            "printf 'foo=bar\\r\\n'"        to mapOf("foo" to "bar"),
            "printf 'foo=bar'"              to mapOf("foo" to "bar"),
            "printf 'foo=bar='"             to mapOf("foo" to "bar="),
            "printf 'foo=bar=baz'"          to mapOf("foo" to "bar=baz"),
            "printf 'foo='"                 to mapOf("foo" to ""),
        )
        tests.forEach{
            assertEquals(
                it.value,
                getHeaders(URL("http://localhost"), it.key),
            )
        }
    }

    @Test
    fun testGetHeadersFail() {
        val tests = listOf(
            "printf 'foo=bar\\r\\n\\r\\n'",
            "printf '\\r\\nfoo=bar'",
            "printf '=foo'",
            "printf 'foo'",
            "printf '  =foo'",
            "printf 'foo  =bar'",
            "printf 'foo  foo=bar'",
            "printf ''",
            "exit 0",
            "exit 1",
        )
        tests.forEach{
            assertFailsWith(
                exceptionClass = Exception::class,
                block = { getHeaders(URL("http://localhost"), it) })
        }
    }

    @Test
    fun testSetsEnvironment() {
        val headers = if (getOS() == OS.WINDOWS) {
            getHeaders(URL("http://localhost12345"), "printf url=%CODER_URL%")
        } else {
            getHeaders(URL("http://localhost12345"), "printf url=\$CODER_URL")
        }
        assertEquals(mapOf("url" to "http://localhost12345"), headers)
    }
}
