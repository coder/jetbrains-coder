package com.coder.gateway.sdk

import spock.lang.Requires
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path

@Unroll
class CoderCLIManagerTest extends spock.lang.Specification {
    // TODO: Probably not good to depend on dev.coder.com being up...should use
    //       a mock?  Or spin up a Coder deployment in CI?
    def "downloads a working cli"() {
        given:
        def ccm = new CoderCLIManager(new URL("https://dev.coder.com"))
        ccm.localBinaryPath.getParent().toFile().deleteDir()

        when:
        def downloaded = ccm.downloadCLI()

        then:
        downloaded
        ccm.version().contains("Coder")
    }

    def "overwrites cli if incorrect version"() {
        given:
        def ccm = new CoderCLIManager(new URL("https://dev.coder.com"))
        Files.createDirectories(ccm.localBinaryPath.getParent())
        ccm.localBinaryPath.toFile().write("cli")

        when:
        def downloaded = ccm.downloadCLI()

        then:
        downloaded
        ccm.version().contains("Coder")
    }

    def "skips cli download if it already exists"() {
        given:
        def ccm = new CoderCLIManager(new URL("https://dev.coder.com"))

        when:
        ccm.downloadCLI()
        def downloaded = ccm.downloadCLI()

        then:
        !downloaded
        ccm.version().contains("Coder")
    }

    def "does not clobber other deployments"() {
        given:
        def ccm1 = new CoderCLIManager(new URL("https://oss.demo.coder.com"))
        def ccm2 = new CoderCLIManager(new URL("https://dev.coder.com"))

        when:
        ccm1.downloadCLI()
        ccm2.downloadCLI()

        then:
        ccm1.localBinaryPath != ccm2.localBinaryPath
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
