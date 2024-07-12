package com.coder.gateway.util

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Return true if a directory can be created at the specified path or if one
 * already exists and we can write into it.
 *
 * Unlike File.canWrite() or Files.isWritable() the directory does not need to
 * exist; it only needs a writable parent and the target needs to be
 * non-existent or a directory (not a regular file or nested under one).
 */
fun Path.canCreateDirectory(): Boolean {
    var current: Path? = this.toAbsolutePath()
    while (current != null && !Files.exists(current)) {
        current = current.parent
    }
    // On Windows File.canWrite() only checks read-only while Files.isWritable()
    // also checks permissions so use the latter.  Both check read-only only on
    // files, not directories; on Windows you are allowed to create files inside
    // read-only directories.
    return current != null && Files.isWritable(current) && Files.isDirectory(current)
}

/**
 * Expand ~, $HOME, and ${user_home} at the beginning of a path.
 */
fun expand(path: String): String {
    if (path == "~" || path == "\$HOME" || path == "\${user.home}") {
        return System.getProperty("user.home")
    }
    // On Windows also allow /.  Windows seems to work fine with mixed slashes
    // like c:\users\coder/my/path/here.
    val os = getOS()
    if (path.startsWith("~" + File.separator) || (os == OS.WINDOWS && path.startsWith("~/"))) {
        return Path.of(System.getProperty("user.home"), path.substring(1)).toString()
    }
    if (path.startsWith("\$HOME" + File.separator) || (os == OS.WINDOWS && path.startsWith("\$HOME/"))) {
        return Path.of(System.getProperty("user.home"), path.substring(5)).toString()
    }
    if (path.startsWith("\${user.home}" + File.separator) || (os == OS.WINDOWS && path.startsWith("\${user.home}/"))) {
        return Path.of(System.getProperty("user.home"), path.substring(12)).toString()
    }
    return path
}
