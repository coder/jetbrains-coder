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
    private Path unwritable = tmpdir.resolve("coder-gateway-test/path-extensions/unwritable")

    void setupSpec() {
        if (unwritable.parent.toFile().exists()) {
            unwritable.parent.toFile().setWritable(true)
            unwritable.parent.toFile().deleteDir()
        }
        Files.createDirectories(unwritable.parent)
        unwritable.toFile().write("text")
        unwritable.toFile().setWritable(false)
        unwritable.parent.toFile().setWritable(false)
    }

    def "canWrite"() {
        expect:
        use(PathExtensionsKt) {
            path.canWrite() == expected
        }

        where:
        path                                            | expected
        unwritable                                      | false
        unwritable.resolve("probably/nonexistent")      | false
        Path.of("relative to project")                  | true
        tmpdir.resolve("./foo/bar/../..")               | true
        tmpdir                                          | true
        tmpdir.resolve("some/nested/non-existent/path") | true
        tmpdir.resolve("with space")                    | true
        CoderCLIManager.getConfigDir()                  | true
        CoderCLIManager.getDataDir()                    | true
    }
}
