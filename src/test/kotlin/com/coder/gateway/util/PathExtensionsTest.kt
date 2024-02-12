package com.coder.gateway.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

import java.io.File
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

internal class PathExtensionsTest {
    private val isWindows = System.getProperty("os.name").lowercase().contains("windows")

    private fun setWindowsPermissions(path: Path) {
        val view = Files.getFileAttributeView(path, AclFileAttributeView::class.java)
        val entry = AclEntry.newBuilder()
                .setType(AclEntryType.DENY)
                .setPrincipal(view.owner)
                .setPermissions(AclEntryPermission.WRITE_DATA)
                .build()
        val acl = view.acl
        acl[0] = entry
        view.acl = acl
    }

    private fun setupDirs(): Path {
        val tmpdir = Path.of(System.getProperty("java.io.tmpdir"))
            .resolve("coder-gateway-test/path-extensions/")

        // Clean up from the last run, if any.
        tmpdir.toFile().deleteRecursively()

        // Push out the test files.
        listOf("read-only-dir", "no-permissions-dir").forEach{
            Files.createDirectories(tmpdir.resolve(it))
            tmpdir.resolve(it).resolve("file").toFile().writeText("")
        }
        listOf("read-only-file", "writable-file", "no-permissions-file").forEach{
            tmpdir.resolve(it).toFile().writeText("")
        }

        // On Windows `File.setWritable()` only sets read-only, not permissions
        // so on other platforms "read-only" is the same as "no permissions".
        tmpdir.resolve("read-only-file").toFile().setWritable(false)
        tmpdir.resolve("read-only-dir").toFile().setWritable(false)

        // Create files without actual write permissions on Windows (not just
        // read-only).  On other platforms this is the same as above.
        tmpdir.resolve("no-permissions-dir/file").toFile().writeText("")
        if (isWindows) {
            setWindowsPermissions(tmpdir.resolve("no-permissions-file"))
            setWindowsPermissions(tmpdir.resolve("no-permissions-dir"))
        } else {
            tmpdir.resolve("no-permissions-file").toFile().setWritable(false)
            tmpdir.resolve("no-permissions-dir").toFile().setWritable(false)
        }

        return tmpdir
    }

    @Test
    fun testCanCreateDirectory() {
        val tmpdir = setupDirs()

        // A file is not valid for directory creation regardless of writability.
        assertFalse(tmpdir.resolve("read-only-file").canCreateDirectory())
        assertFalse(tmpdir.resolve("read-only-file/nested/under/file").canCreateDirectory())
        assertFalse(tmpdir.resolve("writable-file").canCreateDirectory())
        assertFalse(tmpdir.resolve("writable-file/nested/under/file").canCreateDirectory())
        assertFalse(tmpdir.resolve("read-only-dir/file").canCreateDirectory())
        assertFalse(tmpdir.resolve("no-permissions-dir/file").canCreateDirectory())

        // Windows: can create under read-only directories.
        assertEquals(isWindows, tmpdir.resolve("read-only-dir").canCreateDirectory())
        assertEquals(isWindows, tmpdir.resolve("read-only-dir/nested/under/dir").canCreateDirectory())

        // Cannot create under a directory without permissions.
        assertFalse(tmpdir.resolve("no-permissions-dir").canCreateDirectory())
        assertFalse(tmpdir.resolve("no-permissions-dir/nested/under/dir").canCreateDirectory())

        // Can create under a writable directory.
        assertTrue(tmpdir.canCreateDirectory())
        assertTrue(tmpdir.resolve("./foo/bar/../../coder-gateway-test/path-extensions").canCreateDirectory())
        assertTrue(tmpdir.resolve("nested/under/dir").canCreateDirectory())
        assertTrue(tmpdir.resolve("with space").canCreateDirectory())

        // Relative paths can work as well.
        assertTrue(Path.of("relative/to/project").canCreateDirectory())
    }

    @Test
    fun testExpand() {
        val home = System.getProperty("user.home")
        listOf("~", "\$HOME", "\${user.home}").forEach{
            // Only replace at the beginning of the string.
            assertEquals(Paths.get(home, "foo", it, "bar").toString(),
                         expand(Paths.get(it, "foo", it, "bar" ).toString()))

            // Do not replace if part of a larger string.
            assertEquals(home, expand(it))
            assertEquals(home, expand(it + File.separator))
            assertEquals(it+"hello", expand(it + "hello"))
        }
    }
}