package com.coder.gateway.util

import java.net.URL
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
            "printf 'foo=bar   '"           to mapOf("foo" to "bar   "),
            "exit 0"                        to mapOf(),
            "printf ''"                     to mapOf(),
            "printf 'ignore me' >&2"        to mapOf(),
            "printf 'foo=bar' && printf 'ignore me' >&2" to mapOf("foo" to "bar"),
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
        val tests = mapOf(
            "printf '=foo'"                 to "Header name is missing in \"=foo\"",
            "printf 'foo'"                  to "Header \"foo\" does not have two parts",
            "printf '  =foo'"               to "Header name is missing in \"  =foo\"",
            "printf 'foo  =bar'"            to "Header name cannot contain spaces, got \"foo  \"",
            "printf 'foo  foo=bar'"         to "Header name cannot contain spaces, got \"foo  foo\"",
            "printf '  foo=bar  '"          to "Header name cannot contain spaces, got \"  foo\"",
            "exit 1"                        to "Unexpected exit value: 1",
            "printf 'foobar' >&2 && exit 1" to "foobar",
            "printf 'foo=bar\\r\\n\\r\\n'"  to "Blank lines are not allowed",
            "printf '\\r\\nfoo=bar'"        to "Blank lines are not allowed",
            "printf '\\r\\n'"               to "Blank lines are not allowed",
            "printf 'f=b\\r\\n\\r\\nb=q'"   to "Blank lines are not allowed"
        )
        tests.forEach{
            val ex = assertFailsWith(
                exceptionClass = Exception::class,
                block = { getHeaders(URL("http://localhost"), it.key) })
            assertContains(ex.message.toString(), it.value)
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
