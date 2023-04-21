package com.coder.gateway.sdk

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path

@Unroll
class PathExtensionsTest extends Specification {
    @Shared
    private Path tmpdir = Path.of(System.getProperty("java.io.tmpdir"))
    @Shared
    private Path unwritableFile = tmpdir.resolve("coder-gateway-test/path-extensions/unwritable/file")
    @Shared
    private Path writableFile = tmpdir.resolve("coder-gateway-test/path-extensions/writable-file")

    void setupSpec() {
        // TODO: On Windows setWritable() only sets read-only; how do we set
        // actual permissions?  Initially I tried an existing dir like WINDIR
        // which worked locally but in CI that is writable for some reason.
        if (unwritableFile.parent.toFile().exists()) {
            unwritableFile.parent.toFile().setWritable(true)
            unwritableFile.parent.toFile().deleteDir()
        }
        Files.createDirectories(unwritableFile.parent)
        unwritableFile.toFile().write("text")
        writableFile.toFile().write("text")
        unwritableFile.toFile().setWritable(false)
        unwritableFile.parent.toFile().setWritable(false)
    }

    def "canCreateDirectory"() {
        expect:
        use(PathExtensionsKt) {
            path.canCreateDirectory() == expected
        }

        where:
        path                                                                 | expected
        unwritableFile                                                       | false
        unwritableFile.resolve("probably/nonexistent")                       | false
        // TODO: Java reports read-only directories on Windows as writable.
        unwritableFile.parent.resolve("probably/nonexistent")                | System.getProperty("os.name").toLowerCase().contains("windows")
        writableFile                                                         | false
        writableFile.parent                                                  | true
        writableFile.resolve("nested/under/file")                            | false
        writableFile.parent.resolve("nested/under/dir")                      | true
        Path.of("relative to project")                                       | true
        tmpdir.resolve("./foo/bar/../../coder-gateway-test/path-extensions") | true
        tmpdir                                                               | true
        tmpdir.resolve("some/nested/non-existent/path")                      | true
        tmpdir.resolve("with space")                                         | true
        CoderCLIManager.getConfigDir()                                       | true
        CoderCLIManager.getDataDir()                                         | true
    }
}
