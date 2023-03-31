package com.coder.gateway.sdk

import spock.lang.Requires
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
        ccm.version().contains("Coder")
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

    /**
     * Get a config dir using default environment variable values.
     */
    def configDir(Map<String, String> env = [:]) {
        return CoderCLIManager.getConfigDir(new Environment([
                "APPDATA"         : "/tmp/coder-gateway-test/appdata",
                "HOME"            : "/tmp/coder-gateway-test/home",
                "XDG_CONFIG_HOME" : "/tmp/coder-gateway-test/xdg",
                "CODER_CONFIG_DIR": "",
        ] + env))
    }

    // Mostly just a sanity check to make sure the default System.getenv runs
    // without throwing any errors.
    def "gets config dir"() {
        when:
        def dir = CoderCLIManager.getConfigDir(new Environment([
                "CODER_CONFIG_DIR": "",
        ]))

        then:
        dir.toString().contains("coderv2")
    }

    def "gets config dir from CODER_CONFIG_DIR"() {
        expect:
        Path.of(path) == configDir(env)

        where:
        env                                                  || path
        ["CODER_CONFIG_DIR": "/tmp/coder-gateway-test/conf"] || "/tmp/coder-gateway-test/conf"
    }

    @Requires({ os.linux })
    def "gets config dir from XDG or HOME"() {
        expect:
        Path.of(path) == configDir(env)

        where:
        env                     || path
        [:]                     || "/tmp/coder-gateway-test/xdg/coderv2"
        ["XDG_CONFIG_HOME": ""] || "/tmp/coder-gateway-test/home/.config/coderv2"
    }

    @Requires({ os.macOs })
    def "gets config dir from HOME"() {
        expect:
        Path.of("/tmp/coder-gateway-test/home/Library/Application Support/coderv2") == configDir()
    }

    @Requires({ os.windows })
    def "gets config dir from APPDATA"() {
        expect:
        Path.of("/tmp/coder-gateway-test/appdata/coderv2") == configDir()
    }
}
