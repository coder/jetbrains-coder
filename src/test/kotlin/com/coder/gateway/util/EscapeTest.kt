package com.coder.gateway.util

import kotlin.test.Test
import kotlin.test.assertEquals

internal class EscapeTest {
    @Test
    fun testEscape() {
        val tests =
            mapOf(
                """/tmp/coder""" to """/tmp/coder""",
                """/tmp/c o d e r""" to """"/tmp/c o d e r"""",
                """C:\no\spaces.exe""" to """C:\no\spaces.exe""",
                """C:\"quote after slash"""" to """"C:\\"quote after slash\""""",
                """C:\echo "hello world"""" to """"C:\echo \"hello world\""""",
                """C:\"no"\"spaces"""" to """C:\\"no\"\\"spaces\"""",
                """"C:\Program Files\HeaderCommand.exe" --flag""" to """"\"C:\Program Files\HeaderCommand.exe\" --flag"""",
                "https://coder.com" to """https://coder.com""",
                "https://coder.com/?question" to """"https://coder.com/?question"""",
                "https://coder.com/&ampersand" to """"https://coder.com/&ampersand"""",
                "https://coder.com/?with&both" to """"https://coder.com/?with&both"""",
            )
        tests.forEach {
            assertEquals(it.value, escape(it.key))
        }
    }

    @Test
    fun testEscapeSubcommand() {
        val tests =
            if (getOS() == OS.WINDOWS) {
                mapOf(
                    "auth.exe --url=%CODER_URL%" to "\"auth.exe --url=%%CODER_URL%%\"",
                    "\"my auth.exe\" --url=%CODER_URL%" to "\"\\\"my auth.exe\\\" --url=%%CODER_URL%%\"",
                )
            } else {
                mapOf(
                    "auth --url=\$CODER_URL" to "'auth --url=\$CODER_URL'",
                    "'my auth program' --url=\$CODER_URL" to "''\\''my auth program'\\'' --url=\$CODER_URL'",
                )
            }
        tests.forEach {
            assertEquals(it.value, escapeSubcommand(it.key))
        }
    }
}
