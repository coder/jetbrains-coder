package com.coder.gateway.util

import kotlin.test.Test
import kotlin.test.assertEquals

internal class EscapeTest {
    @Test
    fun testEscape() {
        val tests = mapOf(
            """/tmp/coder"""                                  to """/tmp/coder""",
            """/tmp/c o d e r"""                              to """"/tmp/c o d e r"""",
            """C:\no\spaces.exe"""                            to """C:\no\spaces.exe""",
            """C:\"quote after slash""""                      to """"C:\\"quote after slash\""""",
            """C:\echo "hello world""""                       to """"C:\echo \"hello world\""""",
            """C:\"no"\"spaces""""                            to """C:\\"no\"\\"spaces\"""",
            """"C:\Program Files\HeaderCommand.exe" --flag""" to """"\"C:\Program Files\HeaderCommand.exe\" --flag"""",
        )
        tests.forEach {
            assertEquals(it.value, escape(it.key))
        }
    }
}
