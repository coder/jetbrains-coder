package com.coder.gateway.util

import java.io.BufferedInputStream
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest

fun ByteArray.toHex() = joinToString(separator = "") { byte -> "%02x".format(byte) }

/**
 * Return the SHA-1 for the provided stream.
 */
@Suppress("ControlFlowWithEmptyBody")
fun sha1(stream: InputStream): String {
    val md = MessageDigest.getInstance("SHA-1")
    val dis = DigestInputStream(BufferedInputStream(stream), md)
    stream.use {
        while (dis.read() != -1) {
        }
    }
    return md.digest().toHex()
}
