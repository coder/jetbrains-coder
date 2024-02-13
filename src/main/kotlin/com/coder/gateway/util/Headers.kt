package com.coder.gateway.util

import java.net.URL
import org.zeroturnaround.exec.ProcessExecutor

private val newlineRegex = "\r?\n".toRegex()
private val endingNewlineRegex = "\r?\n$".toRegex()

fun getHeaders(url: URL, headerCommand: String?): Map<String, String> {
    if (headerCommand.isNullOrBlank()) {
        return emptyMap()
    }
    val (shell, caller) = when (getOS()) {
        OS.WINDOWS -> Pair("cmd.exe", "/c")
        else -> Pair("sh", "-c")
    }
    return ProcessExecutor()
        .command(shell, caller, headerCommand)
        .environment("CODER_URL", url.toString())
        .exitValues(0)
        .readOutput(true)
        .execute()
        .outputUTF8()
        .replaceFirst(endingNewlineRegex, "")
        .split(newlineRegex)
        .associate {
            // Header names cannot be blank or contain whitespace and the Coder
            // CLI requires that there be an equals sign (the value can be blank
            // though).  The second case is taken care of by the destructure
            // here, as it will throw if there are not enough parts.
            val (name, value) = it.split("=", limit=2)
            if (name.contains(" ") || name == "") {
                throw Exception("\"$name\" is not a valid header name")
            }
            name to value
        }
}
