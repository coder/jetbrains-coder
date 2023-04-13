package com.coder.gateway.sdk

import com.coder.gateway.models.WorkspaceAgentModel
import com.coder.gateway.models.WorkspaceAgentStatus
import com.coder.gateway.models.WorkspaceVersionStatus
import com.coder.gateway.sdk.v2.models.WorkspaceTransition
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
    private String mockBinaryContent = "#!/bin/sh\necho Coder"

    /**
     * Create, start, and return a server that mocks Coder.
     */
    def mockServer(errorCode = 0) {
        HttpServer srv = HttpServer.create(new InetSocketAddress(0), 0)
        srv.createContext("/", new HttpHandler() {
            void handle(HttpExchange exchange) {
                int code = HttpURLConnection.HTTP_OK
                // TODO: Is there some simple way to create an executable file
                // on Windows without having to execute something to generate
                // said executable or having to commit one to the repo?
                String response = mockBinaryContent

                String[] etags = exchange.requestHeaders.get("If-None-Match")
                if (etags != null && etags.contains("\"2f1960264fc0f332a2a7fef2fe678f258dcdff9c\"")) {
                    code = HttpURLConnection.HTTP_NOT_MODIFIED
                    response = "not modified"
                }

                if (!exchange.requestURI.path.startsWith("/bin/coder-")) {
                    code = HttpURLConnection.HTTP_NOT_FOUND
                    response = "not found"
                }

                if (errorCode != 0) {
                    code = errorCode
                    response = "error code ${code}"
                }

                byte[] body = response.getBytes()
                exchange.sendResponseHeaders(code, code == HttpURLConnection.HTTP_OK ? body.length : -1)
                exchange.responseBody.write(body)
                exchange.close()
            }
        })
        srv.start()
        return [srv, "http://localhost:" + srv.address.port]
    }

    void setupSpec() {
        // Clean up from previous runs otherwise they get cluttered since the
        // mock server port is random.
        tmpdir.toFile().deleteDir()
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

    def "encodes IDN with punycode"() {
        given:
        def ccm = new CoderCLIManager(new URL("https://test.😉.invalid"))

        expect:
        ccm.localBinaryPath.getParent() == CoderCLIManager.getDataDir().resolve("test.xn--n28h.invalid")
    }

    def "fails to download"() {
        given:
        def (srv, url) = mockServer(HttpURLConnection.HTTP_INTERNAL_ERROR)
        def ccm = new CoderCLIManager(new URL(url), tmpdir)

        when:
        ccm.downloadCLI()

        then:
        def e = thrown(ResponseException)
        e.code == HttpURLConnection.HTTP_INTERNAL_ERROR

        cleanup:
        srv.stop(0)
    }

    // This test uses a real deployment if possible to make sure we really
    // download a working CLI and that it runs on each platform.
    @Requires({ env["CODER_GATEWAY_TEST_DEPLOYMENT"] != "mock" })
    def "downloads a real working cli"() {
        given:
        def url = System.getenv("CODER_GATEWAY_TEST_DEPLOYMENT")
        if (url == null) {
            url = "https://dev.coder.com"
        }
        def ccm = new CoderCLIManager(new URL(url), tmpdir)
        ccm.localBinaryPath.getParent().toFile().deleteDir()

        when:
        def downloaded = ccm.downloadCLI()

        then:
        downloaded
        ccm.version().contains("Coder")
    }

    def "downloads a mocked cli"() {
        given:
        def (srv, url) = mockServer()
        def ccm = new CoderCLIManager(new URL(url), tmpdir)
        ccm.localBinaryPath.getParent().toFile().deleteDir()

        when:
        def downloaded = ccm.downloadCLI()

        then:
        downloaded
        // The mock does not serve a binary that works on Windows so do not
        // actually execute.  Checking the contents works just as well as proof
        // that the binary was correctly downloaded anyway.
        ccm.localBinaryPath.toFile().readBytes() == mockBinaryContent.getBytes()

        cleanup:
        srv.stop(0)
    }

    def "overwrites cli if incorrect version"() {
        given:
        def (srv, url) = mockServer()
        def ccm = new CoderCLIManager(new URL(url), tmpdir)
        Files.createDirectories(ccm.localBinaryPath.getParent())
        ccm.localBinaryPath.toFile().write("cli")
        ccm.localBinaryPath.toFile().setLastModified(0)

        when:
        def downloaded = ccm.downloadCLI()

        then:
        downloaded
        ccm.localBinaryPath.toFile().readBytes() != "cli".getBytes()
        ccm.localBinaryPath.toFile().lastModified() > 0

        cleanup:
        srv.stop(0)
    }

    def "skips cli download if it already exists"() {
        given:
        def (srv, url) = mockServer()
        def ccm = new CoderCLIManager(new URL(url), tmpdir)

        when:
        ccm.downloadCLI()
        ccm.localBinaryPath.toFile().setLastModified(0)
        def downloaded = ccm.downloadCLI()

        then:
        !downloaded
        ccm.localBinaryPath.toFile().lastModified() == 0

        cleanup:
        srv.stop(0)
    }

    def "does not clobber other deployments"() {
        setup:
        def (srv1, url1) = mockServer()
        def (srv2, url2) = mockServer()
        def ccm1 = new CoderCLIManager(new URL(url1), tmpdir)
        def ccm2 = new CoderCLIManager(new URL(url2), tmpdir)

        when:
        ccm1.downloadCLI()
        ccm2.downloadCLI()

        then:
        ccm1.localBinaryPath != ccm2.localBinaryPath
        ccm1.localBinaryPath.toFile().exists()
        ccm2.localBinaryPath.toFile().exists()

        cleanup:
        srv1.stop(0)
        srv2.stop(0)
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

    private WorkspaceAgentModel randWorkspace(String name) {
        return new WorkspaceAgentModel(
                UUID.randomUUID(),
                name,
                name,
                UUID.randomUUID(),
                "template-name",
                "template-icon-path",
                null,
                WorkspaceVersionStatus.UPDATED,
                WorkspaceAgentStatus.RUNNING,
                WorkspaceTransition.START,
                null,
                null,
                null
        )
    }

    def "configures empty SSH file with multiple hosts"() {
        given:
        def ccm = new CoderCLIManager(new URL("https://test.coder.invalid"), tmpdir)
        def sshConfigPath = tmpdir.resolve("config-nonexistent")
        def coderConfigPath = ccm.localBinaryPath.getParent().resolve("config")

        when:
        ccm.configSsh(List.of(randWorkspace("foo"), randWorkspace("bar")), sshConfigPath)

        then:
        sshConfigPath.toFile().text == """\
          # --- START CODER JETBRAINS test.coder.invalid
          Host coder-jetbrains--foo--test.coder.invalid
            HostName coder.foo
            ProxyCommand "${ccm.localBinaryPath}" --global-config "$coderConfigPath" ssh --stdio foo
            ConnectTimeout 0
            StrictHostKeyChecking no
            UserKnownHostsFile /dev/null
            LogLevel ERROR
            SetEnv CODER_SSH_SESSION_TYPE=JetBrains
          Host coder-jetbrains--bar--test.coder.invalid
            HostName coder.bar
            ProxyCommand "${ccm.localBinaryPath}" --global-config "$coderConfigPath" ssh --stdio bar
            ConnectTimeout 0
            StrictHostKeyChecking no
            UserKnownHostsFile /dev/null
            LogLevel ERROR
            SetEnv CODER_SSH_SESSION_TYPE=JetBrains
          # --- END CODER JETBRAINS test.coder.invalid""".stripIndent().replace("\n", System.lineSeparator())
    }

    def "configures existing SSH file with one host"() {
        given:
        def ccm = new CoderCLIManager(new URL("https://test.coder.invalid"), tmpdir)
        def sshConfigPath = tmpdir.resolve("config-existing")
        Files.createDirectories(sshConfigPath.getParent())
        sshConfigPath.toFile().write(contents.stripIndent().replace("\n", System.lineSeparator()))

        when:
        ccm.configSsh(List.of(randWorkspace("foo-bar")), sshConfigPath)

        then:
        sshConfigPath.toFile().text == expected.stripIndent().replace("\n", System.lineSeparator())

        where:
        contents << {
            // Blank file.
            ["",

             // Preserve existing newlines.
             """\


             """,

             // Append to config; ignore unrelated blocks.
             """\
             Host test
               Port 80
             # ------------START-CODER-----------
             some coder config
             # ------------END-CODER------------""",

             // Replace config at end without newlines.
             """\
             Host test
               Port 80 # --- START CODER JETBRAINS test.coder.invalid
               some jetbrains config # --- END CODER JETBRAINS test.coder.invalid""",

             // Replace config at the middle; ignore unrelated blocks.
             """\
             Host test
               Port 80
             # ------------START-CODER-----------
             some coder config
             # ------------END-CODER------------
             # --- START CODER JETBRAINS test.coder.invalid
             some jetbrains config
             # --- END CODER JETBRAINS test.coder.invalid
             Host *
               HostName localhost
             # --- START CODER JETBRAINS test.coder.unrelated
             some jetbrains config
             # --- END CODER JETBRAINS test.coder.unrelated""",

             // Replace config at the start without leading newline.
             """\
             # --- START CODER JETBRAINS test.coder.invalid
             some jetbrains config
             # --- END CODER JETBRAINS test.coder.invalid
             Host test
               Port 80""",

             // Replace config at the end without trailing newline.
             """\
             Host test
               Port 80
             # --- START CODER JETBRAINS test.coder.invalid
             some jetbrains config
             # --- END CODER JETBRAINS test.coder.invalid""",

             // Replace config at the end with newlines.
             """\
             Host test
               Port 80
             # --- START CODER JETBRAINS test.coder.invalid
             some jetbrains config
             # --- END CODER JETBRAINS test.coder.invalid
             """,
            ]
        }()

        expected << {
            // Unfortunately you cannot access local vars in the where block so
            // they have to be recreated.
            def lbp = new CoderCLIManager(new URL("https://test.coder.invalid"), tmpdir).localBinaryPath
            def cp = lbp.getParent().resolve("config")
            ["""\
             # --- START CODER JETBRAINS test.coder.invalid
             Host coder-jetbrains--foo-bar--test.coder.invalid
               HostName coder.foo-bar
               ProxyCommand "$lbp" --global-config "$cp" ssh --stdio foo-bar
               ConnectTimeout 0
               StrictHostKeyChecking no
               UserKnownHostsFile /dev/null
               LogLevel ERROR
               SetEnv CODER_SSH_SESSION_TYPE=JetBrains
             # --- END CODER JETBRAINS test.coder.invalid""",

             """\



             # --- START CODER JETBRAINS test.coder.invalid
             Host coder-jetbrains--foo-bar--test.coder.invalid
               HostName coder.foo-bar
               ProxyCommand "$lbp" --global-config "$cp" ssh --stdio foo-bar
               ConnectTimeout 0
               StrictHostKeyChecking no
               UserKnownHostsFile /dev/null
               LogLevel ERROR
               SetEnv CODER_SSH_SESSION_TYPE=JetBrains
             # --- END CODER JETBRAINS test.coder.invalid""",

             """\
             Host test
               Port 80
             # ------------START-CODER-----------
             some coder config
             # ------------END-CODER------------
             # --- START CODER JETBRAINS test.coder.invalid
             Host coder-jetbrains--foo-bar--test.coder.invalid
               HostName coder.foo-bar
               ProxyCommand "$lbp" --global-config "$cp" ssh --stdio foo-bar
               ConnectTimeout 0
               StrictHostKeyChecking no
               UserKnownHostsFile /dev/null
               LogLevel ERROR
               SetEnv CODER_SSH_SESSION_TYPE=JetBrains
             # --- END CODER JETBRAINS test.coder.invalid""",

             """\
             Host test
               Port 80 # --- START CODER JETBRAINS test.coder.invalid
             Host coder-jetbrains--foo-bar--test.coder.invalid
               HostName coder.foo-bar
               ProxyCommand "$lbp" --global-config "$cp" ssh --stdio foo-bar
               ConnectTimeout 0
               StrictHostKeyChecking no
               UserKnownHostsFile /dev/null
               LogLevel ERROR
               SetEnv CODER_SSH_SESSION_TYPE=JetBrains
             # --- END CODER JETBRAINS test.coder.invalid""",

             """\
             Host test
               Port 80
             # ------------START-CODER-----------
             some coder config
             # ------------END-CODER------------
             # --- START CODER JETBRAINS test.coder.invalid
             Host coder-jetbrains--foo-bar--test.coder.invalid
               HostName coder.foo-bar
               ProxyCommand "$lbp" --global-config "$cp" ssh --stdio foo-bar
               ConnectTimeout 0
               StrictHostKeyChecking no
               UserKnownHostsFile /dev/null
               LogLevel ERROR
               SetEnv CODER_SSH_SESSION_TYPE=JetBrains
             # --- END CODER JETBRAINS test.coder.invalid
             Host *
               HostName localhost
             # --- START CODER JETBRAINS test.coder.unrelated
             some jetbrains config
             # --- END CODER JETBRAINS test.coder.unrelated""",

             """\
             # --- START CODER JETBRAINS test.coder.invalid
             Host coder-jetbrains--foo-bar--test.coder.invalid
               HostName coder.foo-bar
               ProxyCommand "$lbp" --global-config "$cp" ssh --stdio foo-bar
               ConnectTimeout 0
               StrictHostKeyChecking no
               UserKnownHostsFile /dev/null
               LogLevel ERROR
               SetEnv CODER_SSH_SESSION_TYPE=JetBrains
             # --- END CODER JETBRAINS test.coder.invalid
             Host test
               Port 80""",

             """\
             Host test
               Port 80
             # --- START CODER JETBRAINS test.coder.invalid
             Host coder-jetbrains--foo-bar--test.coder.invalid
               HostName coder.foo-bar
               ProxyCommand "$lbp" --global-config "$cp" ssh --stdio foo-bar
               ConnectTimeout 0
               StrictHostKeyChecking no
               UserKnownHostsFile /dev/null
               LogLevel ERROR
               SetEnv CODER_SSH_SESSION_TYPE=JetBrains
             # --- END CODER JETBRAINS test.coder.invalid""",

             """\
             Host test
               Port 80
             # --- START CODER JETBRAINS test.coder.invalid
             Host coder-jetbrains--foo-bar--test.coder.invalid
               HostName coder.foo-bar
               ProxyCommand "$lbp" --global-config "$cp" ssh --stdio foo-bar
               ConnectTimeout 0
               StrictHostKeyChecking no
               UserKnownHostsFile /dev/null
               LogLevel ERROR
               SetEnv CODER_SSH_SESSION_TYPE=JetBrains
             # --- END CODER JETBRAINS test.coder.invalid
             """,
            ]
        }()
    }

    def "removes empty block including newlines"() {
        def ccm = new CoderCLIManager(new URL("https://test.coder.invalid"), tmpdir)
        def configPath = tmpdir.resolve("config-remove")
        Files.createDirectories(configPath.getParent())
        configPath.toFile().write(contents.stripIndent().replace("\n", System.lineSeparator()))

        when:
        ccm.configSsh(List.of(), configPath)

        then:
        configPath.toFile().text == expected.stripIndent().replace("\n", System.lineSeparator())

        where:
        contents << [
                // Nothing to remove.
                """\
                Host test
                  Port 80
                Host test2
                  Port 443""",

                // Remove at the end without a trailing newline.
                """\
                Host test
                  Port 80
                Host test2
                  Port 443
                # --- START CODER JETBRAINS test.coder.invalid
                some jetbrains config
                # --- END CODER JETBRAINS test.coder.invalid""",

                // Remove in the middle without a leading newline.
                """\
                Host test
                  Port 80 # --- START CODER JETBRAINS test.coder.invalid
                  some jetbrains config
                  # --- END CODER JETBRAINS test.coder.invalid
                Host test2
                  Port 443""",

                // Remove at the middle without any newlines.
                """\
                Host test
                  Port 80
                Host test2
                  Port 443 # --- START CODER JETBRAINS test.coder.invalid
                  some jetbrains config # --- END CODER JETBRAINS test.coder.invalid""",

                // Remove at the start.
                """\
                # --- START CODER JETBRAINS test.coder.invalid
                some jetbrains config
                # --- END CODER JETBRAINS test.coder.invalid
                Host test
                  Port 80
                Host test2
                  Port 443""",

                // Remove at the end with a trailing newline.
                """\
                Host test
                  Port 80
                Host test2
                  Port 443
                  # --- START CODER JETBRAINS test.coder.invalid
                  some jetbrains config
                  # --- END CODER JETBRAINS test.coder.invalid
                """,

                // Remove everything.
                """\
                # --- START CODER JETBRAINS test.coder.invalid
                some jetbrains config
                # --- END CODER JETBRAINS test.coder.invalid
                """
        ]

        expected << [
                """\
                Host test
                  Port 80
                Host test2
                  Port 443""",

                """\
                Host test
                  Port 80
                Host test2
                  Port 443""",

                """\
                Host test
                  Port 80
                Host test2
                  Port 443""",

                """\
                Host test
                  Port 80
                Host test2
                  Port 443""",

                """\
                Host test
                  Port 80
                Host test2
                  Port 443""",

                """\
                Host test
                  Port 80
                Host test2
                  Port 443
                """,

                """""",
        ]
    }

    def "fails if config is malformed"() {
        given:
        def ccm = new CoderCLIManager(new URL("https://test.coder.invalid"), tmpdir)
        def configPath = tmpdir.resolve("config-malformed")
        Files.createDirectories(configPath.getParent())
        configPath.toFile().write(content.stripIndent().replace("\n", System.lineSeparator()))

        when:
        ccm.configSsh(List.of(), configPath)

        then:
        thrown(SSHConfigFormatException)

        where:
        content << [
                """# --- START CODER JETBRAINS test.coder.invalid
                some jetbrains config""",
                """some jetbrains config
                # --- END CODER JETBRAINS test.coder.invalid""",
                """# --- END CODER JETBRAINS test.coder.invalid
                some jetbrains config
                # --- START CODER JETBRAINS test.coder.invalid""",
                """# --- START CODER JETBRAINS test.coder.something-else
                some jetbrains config
                # --- END CODER JETBRAINS test.coder.invalid""",
        ]
    }
}
