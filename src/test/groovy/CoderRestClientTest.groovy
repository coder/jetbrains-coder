package com.coder.gateway.sdk

import com.coder.gateway.sdk.convertors.InstantConverter
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceResource
import com.coder.gateway.sdk.v2.models.WorkspacesResponse
import com.coder.gateway.services.CoderSettingsState
import com.google.gson.GsonBuilder
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.Unroll

import javax.net.ssl.HttpsURLConnection
import java.time.Instant

@Unroll
class CoderRestClientTest extends Specification {
    private CoderSettingsState settings = new CoderSettingsState()
    /**
     * Create, start, and return a server that mocks the Coder API.
     *
     * The resources map to the workspace index (to avoid having to manually hardcode IDs everywhere since you cannot
     * use variables in the where blocks).
     */
    def mockServer(List<Workspace> workspaces, List<List<WorkspaceResource>> resources = []) {
        HttpServer srv = HttpServer.create(new InetSocketAddress(0), 0)
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
        srv.start()
        return [srv, "http://localhost:" + srv.address.port]
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
}
