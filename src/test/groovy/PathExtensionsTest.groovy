package com.coder.gateway.sdk

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView

@Unroll
class PathExtensionsTest extends Specification {
    @Shared
    private Path tmpdir = Path.of(System.getProperty("java.io.tmpdir")).resolve("coder-gateway-test/path-extensions/")

    private void setWindowsPermissions(Path path) {
        AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class)
        AclEntry entry = AclEntry.newBuilder()
                .setType(AclEntryType.DENY)
                .setPrincipal(view.getOwner())
                .setPermissions(AclEntryPermission.WRITE_DATA)
                .build()
        List<AclEntry> acl = view.getAcl()
        acl.set(0, entry)
        view.setAcl(acl)
    }

    void setupSpec() {
        // Clean up from the last run, if any.
        tmpdir.toFile().deleteDir()

        // Push out the test files.
        for (String dir in ["read-only-dir", "no-permissions-dir"]) {
            Files.createDirectories(tmpdir.resolve(dir))
            tmpdir.resolve(dir).resolve("file").toFile().write("")
        }
        for (String file in ["read-only-file", "writable-file", "no-permissions-file"]) {
            tmpdir.resolve(file).toFile().write("")
        }

        // On Windows `File.setWritable()` only sets read-only, not permissions
        // so on other platforms "read-only" is the same as "no permissions".
        tmpdir.resolve("read-only-file").toFile().setWritable(false)
        tmpdir.resolve("read-only-dir").toFile().setWritable(false)

        // Create files without actual write permissions on Windows (not just
        // read-only).  On other platforms this is the same as above.
        tmpdir.resolve("no-permissions-dir/file").toFile().write("")
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            setWindowsPermissions(tmpdir.resolve("no-permissions-file"))
            setWindowsPermissions(tmpdir.resolve("no-permissions-dir"))
        } else {
            tmpdir.resolve("no-permissions-file").toFile().setWritable(false)
            tmpdir.resolve("no-permissions-dir").toFile().setWritable(false)
        }
    }

    def "canCreateDirectory"() {
        expect:
        use(PathExtensionsKt) {
            path.canCreateDirectory() == expected
        }

        where:
        path                                                                 | expected
        // A file is not valid for directory creation regardless of writability.
        tmpdir.resolve("read-only-file")                                     | false
        tmpdir.resolve("read-only-file/nested/under/file")                   | false
        tmpdir.resolve("writable-file")                                      | false
        tmpdir.resolve("writable-file/nested/under/file")                    | false
        tmpdir.resolve("read-only-dir/file")                                 | false
        tmpdir.resolve("no-permissions-dir/file")                            | false

        // Windows: can create under read-only directories.
        tmpdir.resolve("read-only-dir")                                      | System.getProperty("os.name").toLowerCase().contains("windows")
        tmpdir.resolve("read-only-dir/nested/under/dir")                     | System.getProperty("os.name").toLowerCase().contains("windows")

        // Cannot create under a directory without permissions.
        tmpdir.resolve("no-permissions-dir")                                 | false
        tmpdir.resolve("no-permissions-dir/nested/under/dir")                | false

        // Can create under a writable directory.
        tmpdir                                                               | true
        tmpdir.resolve("./foo/bar/../../coder-gateway-test/path-extensions") | true
        tmpdir.resolve("nested/under/dir")                                   | true
        tmpdir.resolve("with space")                                         | true

        // Config/data directories should be fine.
        CoderCLIManager.getConfigDir()                                       | true
        CoderCLIManager.getDataDir()                                         | true

        // Relative paths can work as well.
        Path.of("relative/to/project")                                       | true
    }
}
