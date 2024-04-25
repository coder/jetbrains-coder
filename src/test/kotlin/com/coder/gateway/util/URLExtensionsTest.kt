package com.coder.gateway.util

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
}
