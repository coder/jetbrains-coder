package com.coder.gateway.util

/**
 * Escape a command argument to be used in the ProxyCommand of an SSH
 * config.  Surround with double quotes if the argument contains whitespace
 * and escape any existing double quotes.
 *
 * Throws if the argument is invalid.
 */
fun escape(s: String): String {
    if (s.contains("\n")) {
        throw Exception("argument cannot contain newlines")
    }
    if (s.contains(" ") || s.contains("\t")) {
        return "\"" + s.replace("\"", "\\\"") + "\""
    }
    return s.replace("\"", "\\\"")
}