package com.coder.gateway.util

import kotlin.test.Test
import kotlin.test.assertEquals

internal class HashTest {
    @Test
    fun testToHex() {
        val tests = mapOf(
            "foobar" to "8843d7f92416211de9ebb963ff4ce28125932878",
            "test"   to "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3",
        )
        tests.forEach {
            assertEquals(it.value, sha1(it.key.byteInputStream()))
        }
    }
}
