package com.coder.gateway.sdk

import com.coder.gateway.sdk.convertors.InstantConverter
import com.coder.gateway.sdk.v2.models.Role
import com.coder.gateway.sdk.v2.models.User
import com.coder.gateway.sdk.v2.models.UserStatus
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceResource
import com.coder.gateway.sdk.v2.models.WorkspacesResponse
import com.coder.gateway.services.CoderSettings
import com.coder.gateway.services.CoderSettingsState
import com.google.gson.GsonBuilder
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.Unroll

import javax.net.ssl.HttpsURLConnection
import java.nio.file.Path
import java.time.Instant

@Unroll
class CoderRestClientTest extends Specification {
    private CoderSettings settings = new CoderSettings(new CoderSettingsState())

    /**
     * Create, start, and return a server that mocks the Coder API.
     *
     * The resources map to the workspace index (to avoid having to manually hardcode IDs everywhere since you cannot
     * use variables in the where blocks).
     */
    def mockServer(List<Workspace> workspaces, List<List<WorkspaceResource>> resources = []) {
        HttpServer srv = HttpServer.create(new InetSocketAddress(0), 0)
        addServerContext(srv, workspaces, resources)
        srv.start()
        return [srv, "http://localhost:" + srv.address.port]
    }

    def addServerContext(HttpServer srv, List<Workspace> workspaces, List<List<WorkspaceResource>> resources = []) {
        srv.createContext("/", new HttpHandler() {
            void handle(HttpExchange exchange) {
                int code = HttpURLConnection.HTTP_NOT_FOUND
                String response = "not found"
                try {
                    def matcher = exchange.requestURI.path =~ /\/api\/v2\/templateversions\/([^\/]+)\/resources/
                    if (matcher.size() == 1) {
                        UUID templateVersionId = UUID.fromString(matcher[0][1])
                        int idx = workspaces.findIndexOf { it.latestBuild.templateVersionID == templateVersionId }
                        code = HttpURLConnection.HTTP_OK
                        response = new GsonBuilder().registerTypeAdapter(Instant.class, new InstantConverter())
                                .create().toJson(resources[idx])
                    } else if (exchange.requestURI.path == "/api/v2/workspaces") {
                        code = HttpsURLConnection.HTTP_OK
                        response = new GsonBuilder().registerTypeAdapter(Instant.class, new InstantConverter())
                                .create().toJson(new WorkspacesResponse(workspaces, workspaces.size()))
                    } else if (exchange.requestURI.path == "/api/v2/users/me") {
                        code = HttpsURLConnection.HTTP_OK
                        def user = new User(
                                UUID.randomUUID(),
                                "tester",
                                "tester@example.com",
                                Instant.now(),
                                Instant.now(),
                                UserStatus.ACTIVE,
                                List<UUID>.of(),
                                List<Role>.of(),
                                ""
                        )
                        response = new GsonBuilder().registerTypeAdapter(Instant.class, new InstantConverter())
                                .create().toJson(user)
                    }
                } catch (error) {
                    // This will be a developer error.
                    code = HttpURLConnection.HTTP_INTERNAL_ERROR
                    response = error.message
                    println(error.message) // Print since it will not show up in the error.
                }

                byte[] body = response.getBytes()
                exchange.sendResponseHeaders(code, body.length)
                exchange.responseBody.write(body)
                exchange.close()
            }
        })
    }

    def mockTLSServer(String certName, List<Workspace> workspaces, List<List<WorkspaceResource>> resources = []) {
        HttpsServer srv = HttpsServer.create(new InetSocketAddress(0), 0)
        def sslContext = CoderRestClientServiceKt.SSLContextFromPEMs(
                Path.of("src/test/fixtures/tls", certName + ".crt").toString(),
                Path.of("src/test/fixtures/tls", certName + ".key").toString(),
                "")
        srv.setHttpsConfigurator(new HttpsConfigurator(sslContext))
        addServerContext(srv, workspaces, resources)
        srv.start()
        return [srv, "https://localhost:" + srv.address.port]
    }

    def mockProxy() {
        HttpServer srv = HttpServer.create(new InetSocketAddress(0), 0)
        srv.createContext("/", new HttpHandler() {
            void handle(HttpExchange exchange) {
                int code
                String response

                if (exchange.requestHeaders.getFirst("Proxy-Authorization") != "Basic Zm9vOmJhcg==") {
                    code = HttpURLConnection.HTTP_PROXY_AUTH
                    response = "authentication required"
                } else {
                    try {
                        HttpURLConnection conn = new URL(exchange.getRequestURI().toString()).openConnection()
                        exchange.requestHeaders.each{
                            conn.setRequestProperty(it.key, it.value.join(","))
                        }
                        BufferedReader br = new BufferedReader(new InputStreamReader(conn.inputStream))
                        StringBuilder responseBuilder = new StringBuilder();
                        String line
                        while ((line = br.readLine()) != null) {
                            responseBuilder.append(line)
                        }
                        br.close()
                        response = responseBuilder.toString()
                        code = conn.responseCode
                    } catch (Exception error) {
                        code = HttpURLConnection.HTTP_INTERNAL_ERROR
                        response = error.message
                        println(error) // Print since it will not show up in the error.
                    }
                }

                byte[] body = response.getBytes()
                exchange.sendResponseHeaders(code, body.length)
                exchange.responseBody.write(body)
                exchange.close()
            }
        })
        srv.start()
        return srv
    }

    def "gets workspaces"() {
        given:
        def (srv, url) = mockServer(workspaces)
        def client = new CoderRestClient(new URL(url), "token", "test", settings)

        expect:
        client.workspaces()*.name == expected

        cleanup:
        srv.stop(0)

        where:
        workspaces                                           | expected
        []                                                   | []
        [DataGen.workspace("ws1")]                           | ["ws1"]
        [DataGen.workspace("ws1"), DataGen.workspace("ws2")] | ["ws1", "ws2"]
    }

    def "gets resources"() {
        given:
        def (srv, url) = mockServer(workspaces, resources)
        def client = new CoderRestClient(new URL(url), "token", "test", settings)

        expect:
        client.agents(workspaces).collect { it.agentID.toString() } == expected

        cleanup:
        srv.stop(0)

        where:
        workspaces << [
                [],
                [DataGen.workspace("ws1", [agent1: "3f51da1d-306f-4a40-ac12-62bda5bc5f9a"])],
                [DataGen.workspace("ws1", [agent1: "3f51da1d-306f-4a40-ac12-62bda5bc5f9a"])],
                [DataGen.workspace("ws1", [agent1: "3f51da1d-306f-4a40-ac12-62bda5bc5f9a"]),
                 DataGen.workspace("ws2"),
                 DataGen.workspace("ws3")],
        ]
        resources << [
                [],
                [[]],
                [[DataGen.resource("agent2", "968eea5e-8787-439d-88cd-5bc440216a34"),
                  DataGen.resource("agent3", "72fbc97b-952c-40c8-b1e5-7535f4407728")]],
                [[],
                 [DataGen.resource("agent2", "968eea5e-8787-439d-88cd-5bc440216a34"),
                  DataGen.resource("agent3", "72fbc97b-952c-40c8-b1e5-7535f4407728")],
                 []],
        ]
        expected << [
                // Nothing, so no agents.
                [],
                // One workspace with an agent, but resources get overridden by the resources endpoint that returns
                // nothing so we end up with a workspace without an agent.
                ["null"],
                // One workspace with an agent, but resources get overridden by the resources endpoint.
                ["968eea5e-8787-439d-88cd-5bc440216a34", "72fbc97b-952c-40c8-b1e5-7535f4407728"],
                // Multiple workspaces but only one has resources from the resources endpoint.
                ["null", "968eea5e-8787-439d-88cd-5bc440216a34", "72fbc97b-952c-40c8-b1e5-7535f4407728", "null"],
        ]
    }

    def "gets headers"() {
        expect:
        CoderRestClient.getHeaders(new URL("http://localhost"), command) == expected

        where:
        command                         | expected
        null                            | [:]
        ""                              | [:]
        "printf 'foo=bar\\nbaz=qux'"    | ["foo": "bar", "baz": "qux"]
        "printf 'foo=bar\\r\\nbaz=qux'" | ["foo": "bar", "baz": "qux"]
        "printf 'foo=bar\\r\\n'"        | ["foo": "bar"]
        "printf 'foo=bar'"              | ["foo": "bar"]
        "printf 'foo=bar='"             | ["foo": "bar="]
        "printf 'foo=bar=baz'"          | ["foo": "bar=baz"]
        "printf 'foo='"                 | ["foo": ""]
    }

    def "fails to get headers"() {
        when:
        CoderRestClient.getHeaders(new URL("http://localhost"), command)

        then:
        thrown(Exception)

        where:
        command << [
            "printf 'foo=bar\\r\\n\\r\\n'",
            "printf '\\r\\nfoo=bar'",
            "printf '=foo'",
            "printf 'foo'",
            "printf '  =foo'",
            "printf 'foo  =bar'",
            "printf 'foo  foo=bar'",
            "printf ''",
            "exit 1",
        ]
    }

    @IgnoreIf({ os.windows })
    def "has access to environment variables"() {
        expect:
        CoderRestClient.getHeaders(new URL("http://localhost"), "printf url=\$CODER_URL") == [
            "url": "http://localhost",
        ]
    }

    @Requires({ os.windows })
    def "has access to environment variables"() {
        expect:
        CoderRestClient.getHeaders(new URL("http://localhost"), "printf url=%CODER_URL%") == [
            "url": "http://localhost",
        ]

    }

    def "valid self-signed cert"() {
        given:
        def state = new CoderSettingsState()
        def settings = new CoderSettings(state)
        state.tlsCAPath = Path.of("src/test/fixtures/tls", "self-signed.crt").toString()
        state.tlsAlternateHostname = "localhost"
        def (srv, url) = mockTLSServer("self-signed", null)
        def client = new CoderRestClient(new URL(url), "token", "test", settings)

        expect:
        client.me().username == "tester"

        cleanup:
        srv.stop(0)
    }

    def "wrong hostname for cert"() {
        given:
        def state = new CoderSettingsState()
        def settings = new CoderSettings(state)
        state.tlsCAPath = Path.of("src/test/fixtures/tls", "self-signed.crt").toString()
        state.tlsAlternateHostname = "fake.example.com"
        def (srv, url) = mockTLSServer("self-signed", null)
        def client = new CoderRestClient(new URL(url), "token", "test", settings)

        when:
        client.me()

        then:
        thrown(javax.net.ssl.SSLPeerUnverifiedException)

        cleanup:
        srv.stop(0)
    }

    def "server cert not trusted"() {
        given:
        def state = new CoderSettingsState()
        def settings = new CoderSettings(state)
        state.tlsCAPath = Path.of("src/test/fixtures/tls", "self-signed.crt").toString()
        def (srv, url) = mockTLSServer("no-signing", null)
        def client = new CoderRestClient(new URL(url), "token", "test", settings)

        when:
        client.me()

        then:
        thrown(javax.net.ssl.SSLHandshakeException)

        cleanup:
        srv.stop(0)
    }

    def "server using valid chain cert"() {
        given:
        def state = new CoderSettingsState()
        def settings = new CoderSettings(state)
        state.tlsCAPath = Path.of("src/test/fixtures/tls", "chain-root.crt").toString()
        def (srv, url) = mockTLSServer("chain", null)
        def client = new CoderRestClient(new URL(url), "token", "test", settings)

        expect:
        client.me().username == "tester"

        cleanup:
        srv.stop(0)
    }

    def "uses proxy"() {
        given:
        def (srv1, url1) = mockServer([DataGen.workspace("ws1")])
        def srv2 = mockProxy()
        def client = new CoderRestClient(new URL(url1), "token", "test", settings, new ProxyValues(
                "foo",
                "bar",
                true,
                new ProxySelector() {
                    @Override
                    List<Proxy> select(URI uri) {
                        return [new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", srv2.address.port))]
                    }

                    @Override
                    void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                        getDefault().connectFailed(uri, sa, ioe);
                    }
                }
        ))

        expect:
        client.workspaces()*.name == ["ws1"]

        cleanup:
        srv1.stop(0)
        srv2.stop(0)
    }
}
