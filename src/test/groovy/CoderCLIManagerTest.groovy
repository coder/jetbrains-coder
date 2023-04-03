package com.coder.gateway.sdk

import org.zeroturnaround.exec.ProcessExecutor
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path

@Unroll
class CoderCLIManagerTest extends spock.lang.Specification {
    def "deletes old versions"() {
        given:
        // Simulate downloading an old version.
        def oldVersion = new CoderCLIManager(new URL("https://test.coder.invalid"), "0.0.1").localBinaryPath.toFile()
        def dir = oldVersion.toPath().getParent()
        dir.toFile().deleteDir()
        Files.createDirectories(dir)
        oldVersion.write("old-version")

        // Simulate downloading a new version.
        def ccm = new CoderCLIManager(new URL("https://test.coder.invalid"), "1.0.2")
        def newVersion = ccm.localBinaryPath.toFile()
        newVersion.write("new-version")

        // Anything that does not start with the expected prefix is ignored.
        def otherOsVersion = dir.resolve("coder-alpine-amd64-1.0.2").toFile()
        otherOsVersion.write("alpine")

        // Anything else matching the prefix is deleted.
        def invalidVersion = dir.resolve(newVersion.getName() + "-extra-random-text").toFile()
        invalidVersion.write("invalid")

        when:
        ccm.removeOldCli()

        then:
        !oldVersion.exists()
        newVersion.exists()
        otherOsVersion.exists()
        !invalidVersion.exists()
    }

    // TODO: Probably not good to depend on dev.coder.com being up...should use
    //       a mock?  Or spin up a Coder deployment in CI?
    def "downloads a working cli"() {
        given:
        def ccm = new CoderCLIManager(new URL("https://dev.coder.com"), "1.0.1")
        def dir = Path.of(System.getProperty("java.io.tmpdir")).resolve("coder-gateway/dev.coder.com")
        ccm.localBinaryPath.getParent().toFile().deleteDir()

        when:
        def downloaded = ccm.downloadCLI()

        then:
        downloaded
        ccm.localBinaryPath.getParent() == dir
        ccm.localBinaryPath.toFile().exists()
        new ProcessExecutor().command(ccm.localBinaryPath.toString(), "version").readOutput(true).execute().outputUTF8().contains("Coder")
    }

    def "skips cli download if it already exists"() {
        given:
        def ccm = new CoderCLIManager(new URL("https://dev.coder.com"), "1.0.1")
        Files.createDirectories(ccm.localBinaryPath.getParent())
        ccm.localBinaryPath.toFile().write("cli")

        when:
        def downloaded = ccm.downloadCLI()

        then:
        !downloaded
        ccm.localBinaryPath.toFile().readLines() == ["cli"]
    }
}
