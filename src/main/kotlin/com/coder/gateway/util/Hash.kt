package com.coder.gateway.util

import java.io.BufferedInputStream
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest

fun ByteArray.toHex() = joinToString(separator = "") { byte -> "%02x".format(byte) }

/**
 * Return the SHA-1 for the provided stream.
 */
fun sha1(stream: InputStream): String = digest("SHA-1", stream)

/**
 * Return the SHA-256 for the provided stream.
 */
fun sha256(stream: InputStream): String = digest("SHA-256", stream)

@Suppress("ControlFlowWithEmptyBody")
private fun digest(algorithm: String, stream: InputStream): String {
    val md = MessageDigest.getInstance(algorithm)
    val dis = DigestInputStream(BufferedInputStream(stream), md)
    stream.use {
        while (dis.read() != -1) {
        }
    }
    return md.digest().toHex()
}
