package com.coder.gateway.util

import org.zeroturnaround.exec.ProcessExecutor
import java.io.OutputStream
import java.net.URL

private val newlineRegex = "\r?\n".toRegex()
private val endingNewlineRegex = "\r?\n$".toRegex()

fun getHeaders(
    url: URL,
    headerCommand: String?,
): Map<String, String> {
    if (headerCommand.isNullOrBlank()) {
        return emptyMap()
    }
    val (shell, caller) =
        when (getOS()) {
            OS.WINDOWS -> Pair("cmd.exe", "/c")
            else -> Pair("sh", "-c")
        }
    val output =
        ProcessExecutor()
            .command(shell, caller, headerCommand)
            .environment("CODER_URL", url.toString())
            // By default stderr is in the output, but we want to ignore it.  stderr
            // will still be included in the exception if something goes wrong.
            .redirectError(OutputStream.nullOutputStream())
            .exitValues(0)
            .readOutput(true)
            .execute()
            .outputUTF8()

    // The Coder CLI will allow no output, but not blank lines.  Possibly we
    // should skip blank lines, but it is better to have parity so commands will
    // not sometimes work in one context and not another.
    return if (output == "") {
        mapOf()
    } else {
        output
            .replaceFirst(endingNewlineRegex, "")
            .split(newlineRegex)
            .associate {
                // Header names cannot be blank or contain whitespace and the Coder
                // CLI requires there be an equals sign (the value can be blank).
                val parts = it.split("=", limit = 2)
                if (it.isBlank()) {
                    throw Exception("Blank lines are not allowed")
                } else if (parts.size != 2) {
                    throw Exception("Header \"$it\" does not have two parts")
                } else if (parts[0].isBlank()) {
                    throw Exception("Header name is missing in \"$it\"")
                } else if (parts[0].contains(" ")) {
                    throw Exception("Header name cannot contain spaces, got \"${parts[0]}\"")
                }
                parts[0] to parts[1]
            }
    }
}
