package com.coder.gateway.sdk

import java.net.URL


fun String.toURL(): URL {
    return URL(this)
}

fun URL.withPath(path: String): URL {
    return URL(this.protocol, this.host, this.port, path)
}
