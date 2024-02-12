package com.coder.gateway.util

import java.net.IDN
import java.net.URL

fun String.toURL(): URL {
    return URL(this)
}

fun URL.withPath(path: String): URL {
    return URL(
        this.protocol, this.host, this.port,
        if (path.startsWith("/")) path else "/$path"
    )
}

/**
 * Return the host, converting IDN to ASCII in case the file system cannot
 * support the necessary character set.
 */
fun URL.safeHost(): String {
    return IDN.toASCII(this.host, IDN.ALLOW_UNASSIGNED)
}
