package com.coder.gateway.sdk

import spock.lang.Requires
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path

@Unroll
class CoderCLIManagerTest extends spock.lang.Specification {
    /**
     * Create a CLI manager pointing to the URL in the environment or to the
       default URLs.
     *
     * @TODO: Implement a mock.
     */
    def createCLIManager(Boolean alternate = false) {
        var url = System.getenv("CODER_GATEWAY_TEST_DEPLOYMENT")
        if (url == null) {
            url = "https://dev.coder.com"
        }
        if (alternate) {
            url = System.getenv("CODER_GATEWAY_TEST_DEPLOYMENT_ALT")
            if (url == null) {
                url = "https://oss.demo.coder.com"
            }
        }
        var tmpdir = Path.of(System.getProperty("java.io.tmpdir")).resolve("coder-gateway-test")
        return new CoderCLIManager(new URL(url), tmpdir)
    }

    def "defaults to a sub-directory in the data directory"() {
        given:
        def ccm = new CoderCLIManager(new URL("https://test.coder.invalid"))

        expect:
        ccm.localBinaryPath.getParent() == CoderCLIManager.getDataDir().resolve("test.coder.invalid")
    }

    def "includes port in sub-directory if included"() {
        given:
        def ccm = new CoderCLIManager(new URL("https://test.coder.invalid:3000"))

        expect:
        ccm.localBinaryPath.getParent() == CoderCLIManager.getDataDir().resolve("test.coder.invalid-3000")
    }

    def "downloads a working cli"() {
        given:
        def ccm = createCLIManager()
        ccm.localBinaryPath.getParent().toFile().deleteDir()

        when:
        def downloaded = ccm.downloadCLI()

        then:
        downloaded
        ccm.version().contains("Coder")
    }

    def "overwrites cli if incorrect version"() {
        given:
        def ccm = createCLIManager()
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
        def ccm = createCLIManager()

        when:
        ccm.downloadCLI()
        def downloaded = ccm.downloadCLI()

        then:
        !downloaded
        ccm.version().contains("Coder")
    }

    def "does not clobber other deployments"() {
        given:
        def ccm1 = createCLIManager(true)
        def ccm2 = createCLIManager()

        when:
        ccm1.downloadCLI()
        ccm2.downloadCLI()

        then:
        ccm1.localBinaryPath != ccm2.localBinaryPath
    }

    def testEnv = [
            "APPDATA"         : "/tmp/coder-gateway-test/appdata",
            "LOCALAPPDATA"    : "/tmp/coder-gateway-test/localappdata",
            "HOME"            : "/tmp/coder-gateway-test/home",
            "XDG_CONFIG_HOME" : "/tmp/coder-gateway-test/xdg-config",
            "XDG_DATA_HOME"   : "/tmp/coder-gateway-test/xdg-data",
            "CODER_CONFIG_DIR": "",
    ]

    /**
     * Get a config dir using default environment variable values.
     */
    def configDir(Map<String, String> env = [:]) {
        return CoderCLIManager.getConfigDir(new Environment(testEnv + env))
    }

    // Mostly just a sanity check to make sure the default System.getenv runs
    // without throwing any errors.
    def "gets config dir"() {
        when:
        def dir = CoderCLIManager.getConfigDir()

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
    def "gets config dir from XDG_CONFIG_HOME or HOME"() {
        expect:
        Path.of(path) == configDir(env)

        where:
        env                     || path
        [:]                     || "/tmp/coder-gateway-test/xdg-config/coderv2"
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

    /**
     * Get a data dir using default environment variable values.
     */
    def dataDir(Map<String, String> env = [:]) {
        return CoderCLIManager.getDataDir(new Environment(testEnv + env))
    }
    // Mostly just a sanity check to make sure the default System.getenv runs
    // without throwing any errors.
    def "gets data dir"() {
        when:
        def dir = CoderCLIManager.getDataDir()

        then:
        dir.toString().contains("coder-gateway")
    }

    @Requires({ os.linux })
    def "gets data dir from XDG_DATA_HOME or HOME"() {
        expect:
        Path.of(path) == dataDir(env)

        where:
        env                   || path
        [:]                   || "/tmp/coder-gateway-test/xdg-data/coder-gateway"
        ["XDG_DATA_HOME": ""] || "/tmp/coder-gateway-test/home/.local/share/coder-gateway"
    }

    @Requires({ os.macOs })
    def "gets data dir from HOME"() {
        expect:
        Path.of("/tmp/coder-gateway-test/home/Library/Application Support/coder-gateway") == dataDir()
    }

    @Requires({ os.windows })
    def "gets data dir from LOCALAPPDATA"() {
        expect:
        Path.of("/tmp/coder-gateway-test/localappdata/coder-gateway") == dataDir()
    }
}
