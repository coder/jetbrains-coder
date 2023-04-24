package com.coder.gateway.sdk

import com.coder.gateway.models.WorkspaceAgentModel
import com.coder.gateway.models.WorkspaceAgentStatus
import com.coder.gateway.models.WorkspaceVersionStatus
import com.coder.gateway.sdk.v2.models.WorkspaceTransition
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.zeroturnaround.exec.InvalidExitValueException
import org.zeroturnaround.exec.ProcessInitException
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

@Unroll
class CoderCLIManagerTest extends Specification {
    @Shared
    private Path tmpdir = Path.of(System.getProperty("java.io.tmpdir")).resolve("coder-gateway-test/cli-manager")

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
                String response = "#!/bin/sh\necho 'http://localhost:${srv.address.port}'"
                String[] etags = exchange.requestHeaders.get("If-None-Match")
                if (exchange.requestURI.path == "/bin/override") {
                    code = HttpURLConnection.HTTP_OK
                    response = "#!/bin/sh\necho 'override binary'"
                } else if (!exchange.requestURI.path.startsWith("/bin/coder-")) {
                    code = HttpURLConnection.HTTP_NOT_FOUND
                    response = "not found"
                } else if (errorCode != 0) {
                    code = errorCode
                    response = "error code $code"
                } else if (etags != null && etags.contains("\"${sha1(response)}\"")) {
                    code = HttpURLConnection.HTTP_NOT_MODIFIED
                    response = "not modified"
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

    String sha1(String input) {
        MessageDigest md = MessageDigest.getInstance("SHA-1")
        md.update(input.getBytes("UTF-8"))
        return new BigInteger(1, md.digest()).toString(16)
    }

    def "hashes correctly"() {
        expect:
        sha1(input) == output

        where:
        input                                     | output
        "#!/bin/sh\necho Coder"                   | "2f1960264fc0f332a2a7fef2fe678f258dcdff9c"
        "#!/bin/sh\necho 'override binary'"       | "1b562a4b8f2617b2b94a828479656daf2dde3619"
        "#!/bin/sh\necho 'http://localhost:5678'" | "fd8d45a8a74475e560e2e57139923254aab75989"
    }

    void setupSpec() {
        // Clean up from previous runs otherwise they get cluttered since the
        // mock server port is random.
        tmpdir.toFile().deleteDir()
    }

    def "uses a sub-directory"() {
        given:
        def ccm = new CoderCLIManager(new URL("https://test.coder.invalid"), tmpdir)

        expect:
        ccm.localBinaryPath.getParent() == tmpdir.resolve("test.coder.invalid")
    }

    def "includes port in sub-directory if included"() {
        given:
        def ccm = new CoderCLIManager(new URL("https://test.coder.invalid:3000"), tmpdir)

        expect:
        ccm.localBinaryPath.getParent() == tmpdir.resolve("test.coder.invalid-3000")
    }

    def "encodes IDN with punycode"() {
        given:
        def ccm = new CoderCLIManager(new URL("https://test.😉.invalid"), tmpdir)

        expect:
        ccm.localBinaryPath.getParent() == tmpdir.resolve("test.xn--n28h.invalid")
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

        // Make sure login failures propagate correctly.
        when:
        ccm.login("jetbrains-ci-test")

        then:
        thrown(InvalidExitValueException)
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
        ccm.localBinaryPath.toFile().text == "#!/bin/sh\necho '$url'"

        cleanup:
        srv.stop(0)
    }

    def "fails to run non-existent binary"() {
        given:
        def ccm = new CoderCLIManager(new URL("https://foo"), tmpdir.resolve("does-not-exist"))

        when:
        ccm.version()

        then:
        thrown(ProcessInitException)
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
        ccm.localBinaryPath.toFile().text == "#!/bin/sh\necho '$url'"

        cleanup:
        srv.stop(0)
    }

    def "skips cli download if it already exists"() {
        given:
        def (srv, url) = mockServer()
        def ccm = new CoderCLIManager(new URL(url), tmpdir)

        when:
        def downloaded1 = ccm.downloadCLI()
        ccm.localBinaryPath.toFile().setLastModified(0)
        def downloaded2 = ccm.downloadCLI()

        then:
        downloaded1
        !downloaded2
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
        ccm1.localBinaryPath.toFile().text == "#!/bin/sh\necho '$url1'"
        ccm2.localBinaryPath.toFile().text == "#!/bin/sh\necho '$url2'"

        cleanup:
        srv1.stop(0)
        srv2.stop(0)
    }

    def "overrides binary URL"() {
        given:
        def (srv, url) = mockServer()
        def ccm = new CoderCLIManager(new URL(url), tmpdir, override.replace("{{url}}", url))

        when:
        def downloaded = ccm.downloadCLI()

        then:
        downloaded
        ccm.localBinaryPath.toFile().text == "#!/bin/sh\necho '${expected.replace("{{url}}", url)}'"

        cleanup:
        srv.stop(0)

        where:
        override               | expected
        "/bin/override"        | "override binary"
        "{{url}}/bin/override" | "override binary"
        "bin/override"         | "override binary"
        ""                     | "{{url}}"
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

    def "configures an SSH file"() {
        given:
        def sshConfigPath = tmpdir.resolve(input + "_to_" + output + ".conf")
        def ccm = new CoderCLIManager(new URL("https://test.coder.invalid"), tmpdir, null, sshConfigPath)
        if (input != null) {
            Files.createDirectories(sshConfigPath.getParent())
            def originalConf = Path.of("src/test/fixtures/inputs").resolve(input + ".conf").toFile().text
                    .replaceAll("\\r?\\n", System.lineSeparator())
            sshConfigPath.toFile().write(originalConf)
        }
        def coderConfigPath = ccm.localBinaryPath.getParent().resolve("config")

        def expectedConf = Path.of("src/test/fixtures/outputs/").resolve(output + ".conf").toFile().text
                .replaceAll("\\r?\\n", System.lineSeparator())
                .replace("/tmp/coder-gateway/test.coder.invalid/config", coderConfigPath.toString())
                .replace("/tmp/coder-gateway/test.coder.invalid/coder-linux-amd64", ccm.localBinaryPath.toString())

        when:
        ccm.configSsh(workspaces.collect { randWorkspace(it) })

        then:
        sshConfigPath.toFile().text == expectedConf

        when:
        ccm.configSsh(List.of())

        then:
        sshConfigPath.toFile().text == Path.of("src/test/fixtures/inputs").resolve(remove + ".conf").toFile().text

        where:
        workspaces     | input                           | output                            | remove
        ["foo", "bar"] | null                            | "multiple-workspaces"             | "blank"
        ["foo-bar"]    | "blank"                         | "append-blank"                    | "blank"
        ["foo-bar"]    | "blank-newlines"                | "append-blank-newlines"           | "blank"
        ["foo-bar"]    | "existing-end"                  | "replace-end"                     | "no-blocks"
        ["foo-bar"]    | "existing-end-no-newline"       | "replace-end-no-newline"          | "no-blocks"
        ["foo-bar"]    | "existing-middle"               | "replace-middle"                  | "no-blocks"
        ["foo-bar"]    | "existing-middle-and-unrelated" | "replace-middle-ignore-unrelated" | "no-related-blocks"
        ["foo-bar"]    | "existing-only"                 | "replace-only"                    | "blank"
        ["foo-bar"]    | "existing-start"                | "replace-start"                   | "no-blocks"
        ["foo-bar"]    | "no-blocks"                     | "append-no-blocks"                | "no-blocks"
        ["foo-bar"]    | "no-related-blocks"             | "append-no-related-blocks"        | "no-related-blocks"
        ["foo-bar"]    | "no-newline"                    | "append-no-newline"               | "no-blocks"
    }

    def "fails if config is malformed"() {
        given:
        def sshConfigPath = tmpdir.resolve("configured" + input + ".conf")
        def ccm = new CoderCLIManager(new URL("https://test.coder.invalid"), tmpdir, null, sshConfigPath)
        Files.createDirectories(sshConfigPath.getParent())
        Files.copy(
                Path.of("src/test/fixtures/inputs").resolve(input + ".conf"),
                sshConfigPath,
                StandardCopyOption.REPLACE_EXISTING,
        )

        when:
        ccm.configSsh(List.of())

        then:
        thrown(SSHConfigFormatException)

        where:
        input << [
                "malformed-mismatched-start",
                "malformed-no-end",
                "malformed-no-start",
                "malformed-start-after-end",
        ]
    }
}
