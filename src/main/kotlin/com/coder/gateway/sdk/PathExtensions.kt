package com.coder.gateway.sdk

import java.nio.file.Files
import java.nio.file.Path

/**
 * Return true if a directory can be created at the specified path or if one
 * already exists and we can write into it.
 *
 * Unlike File.canWrite() or Files.isWritable() the directory does not need to
 * exist; it only needs a writable parent and the target needs to be
 * non-existent or a directory (not a regular file or nested under one).
 *
 * This check is deficient on Windows since directories that have write
 * permissions but are read-only will still return true.
 */
fun Path.canCreateDirectory(): Boolean {
    var current: Path? = this.toAbsolutePath()
    while (current != null && !Files.exists(current)) {
        current = current.parent
    }
    // On Windows File.canWrite() only checks read-only while Files.isWritable()
    // also checks permissions.  For directories neither of them seem to care if
    // it is read-only.
    return current != null && Files.isWritable(current) && Files.isDirectory(current)
}
