package com.coder.gateway.sdk

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path

@Unroll
class CoderCLIManagerTest extends spock.lang.Specification {
    @Shared
    private Path tmpdir = Path.of(System.getProperty("java.io.tmpdir")).resolve("coder-gateway-test")
    @Shared
    private String deploymentURL = System.getenv("CODER_GATEWAY_TEST_DEPLOYMENT")
    @Shared
    private String altDeploymentURL = System.getenv("CODER_GATEWAY_TEST_DEPLOYMENT_ALT")
    @Shared
    private def servers = []

    String startMockServer() {
        HttpServer srv = HttpServer.create(new InetSocketAddress(0), 0)
        srv.createContext("/", new HttpHandler() {
            void handle(HttpExchange exchange) {
                int code = HttpURLConnection.HTTP_OK
                String response = "not a real binary"

                String[] etags = exchange.requestHeaders.get("If-None-Match")
                if (etags != null && etags.contains("\"cb25cf6f41bb3127d7e05b0c3c6403be9ab052bc\"")) {
                    code = HttpURLConnection.HTTP_NOT_MODIFIED
                    response = "not modified"
                }

                if (!exchange.requestURI.path.startsWith("/bin/coder-")) {
                    code = HttpURLConnection.HTTP_NOT_FOUND
                    response = "not found"
                }

                byte[] body = response.getBytes()
                exchange.sendResponseHeaders(code, code == HttpURLConnection.HTTP_OK ? body.length : -1)
                exchange.responseBody.write(body)
                exchange.close()
            }
        })
        servers << srv
        srv.start()
        return "http://localhost:" + srv.address.port
    }

    void setupSpec() {
        // Clean up from previous runs otherwise they get cluttered since the
        // port is random.
        tmpdir.toFile().deleteDir()

        // Spin up mock server(s).
        if (deploymentURL == null) {
            deploymentURL = startMockServer()
        }
        if (altDeploymentURL == null) {
            altDeploymentURL = startMockServer()
        }
    }

    void cleanupSpec() {
        servers.each {
            it.stop(0)
        }
    }

    /**
     * Create a CLI manager pointing to the URLs in the environment or to mocked
     * servers.
     */
    CoderCLIManager createCLIManager(Boolean alternate = false) {
        return new CoderCLIManager(new URL(alternate ? altDeploymentURL : deploymentURL), tmpdir)
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
        ccm.localBinaryPath.toFile().canExecute()
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
        ccm.localBinaryPath.toFile().canExecute()
    }

    def "skips cli download if it already exists"() {
        given:
        def ccm = createCLIManager()

        when:
        ccm.downloadCLI()
        def downloaded = ccm.downloadCLI()

        then:
        !downloaded
        ccm.localBinaryPath.toFile().canExecute()
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

    Map<String, String> testEnv = [
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
    Path configDir(Map<String, String> env = [:]) {
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
    Path dataDir(Map<String, String> env = [:]) {
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
