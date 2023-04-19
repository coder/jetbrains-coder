package com.coder.gateway.sdk

import java.nio.file.Files
import java.nio.file.Path

/**
 * Return true if the path can be created.
 *
 * Unlike File.canWrite() or Files.isWritable() the file does not need to exist;
 * it only needs a writable parent.
 */
fun Path.canWrite(): Boolean {
    var current: Path? = this.toAbsolutePath()
    while (current != null && !Files.exists(current)) {
        current = current.parent
    }
    // On Windows File.canWrite() only checks read-only while Files.isWritable()
    // actually checks permissions.
    return current != null && Files.isWritable(current)
}
