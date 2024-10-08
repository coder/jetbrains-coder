package com.coder.gateway.util

/**
 * Escape an argument to be used in the ProxyCommand of an SSH config.
 *
 * Escaping happens by:
 * 1. Surrounding with double quotes if the argument contains whitespace, ?, or
 *    & (to handle query parameters in URLs) as these characters have special
 *    meaning in shells.
 * 2. Always escaping existing double quotes.
 *
 * Double quotes does not preserve the literal values of $, `, \, *, @, and !
 * (when history expansion is enabled); these are not currently handled.
 *
 * Throws if the argument is invalid.
 */
fun escape(s: String): String {
    if (s.contains("\n")) {
        throw Exception("argument cannot contain newlines")
    }
    if (s.contains(" ") || s.contains("\t") || s.contains("&") || s.contains("?")) {
        return "\"" + s.replace("\"", "\\\"") + "\""
    }
    return s.replace("\"", "\\\"")
}

/**
 * Escape an argument to be executed by the Coder binary such that expansions
 * happen in the binary and not in SSH.
 *
 * Escaping happens by wrapping in single quotes on Linux and escaping % on
 * Windows.
 *
 * Throws if the argument is invalid.
 */
fun escapeSubcommand(s: String): String {
    if (s.contains("\n")) {
        throw Exception("argument cannot contain newlines")
    }
    return if (getOS() == OS.WINDOWS) {
        // On Windows variables are in the format %VAR%.  % is interpreted by
        // SSH as a special sequence and can be escaped with %%.  Do not use
        // single quotes on Windows; they appear to only be used literally.
        return escape(s).replace("%", "%%")
    } else {
        // On *nix and similar systems variables are in the format $VAR.  SSH
        // will expand these before executing the proxy command; we can prevent
        // this by using single quotes.  You cannot escape single quotes inside
        // single quotes, so if there are existing quotes you end the current
        // quoted string, output an escaped quote, then start the quoted string
        // again.
        "'" + s.replace("'", "'\\''") + "'"
    }
}
