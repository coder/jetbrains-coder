package com.coder.gateway.sdk

import com.coder.gateway.sdk.convertors.InstantConverter
import com.coder.gateway.sdk.convertors.UUIDConverter
import com.coder.gateway.sdk.ex.APIResponseException
import com.coder.gateway.sdk.v2.models.CreateWorkspaceBuildRequest
import com.coder.gateway.sdk.v2.models.Response
import com.coder.gateway.sdk.v2.models.Template
import com.coder.gateway.sdk.v2.models.User
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceBuild
import com.coder.gateway.sdk.v2.models.WorkspaceResource
import com.coder.gateway.sdk.v2.models.WorkspaceTransition
import com.coder.gateway.sdk.v2.models.WorkspacesResponse
import com.coder.gateway.settings.CoderSettings
import com.coder.gateway.settings.CoderSettingsState
import com.coder.gateway.util.sslContextFromPEMs
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import okio.buffer
import okio.source
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.util.UUID
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class BaseHttpHandler(
    private val method: String,
    private val handler: (exchange: HttpExchange) -> Unit,
) : HttpHandler {
    private val moshi = Moshi.Builder().build()

    override fun handle(exchange: HttpExchange) {
        try {
            if (exchange.requestMethod != method) {
                val response = Response("Not allowed", "Expected $method but got ${exchange.requestMethod}")
                val body = moshi.adapter(Response::class.java).toJson(response).toByteArray()
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_BAD_METHOD, body.size.toLong())
                exchange.responseBody.write(body)
            } else {
                handler(exchange)
                if (exchange.responseCode == -1) {
                    val response = Response("Not found", "The requested resource could not be found")
                    val body = moshi.adapter(Response::class.java).toJson(response).toByteArray()
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, body.size.toLong())
                    exchange.responseBody.write(body)
                }
            }
        } catch (ex: Exception) {
            val response = Response("Handler threw an exception", ex.message ?: "unknown error")
            val body = moshi.adapter(Response::class.java).toJson(response).toByteArray()
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_BAD_REQUEST, body.size.toLong())
            exchange.responseBody.write(body)
        }
        exchange.close()
    }
}

class CoderRestClientTest {
    private val moshi =
        Moshi.Builder()
            .add(InstantConverter())
            .add(UUIDConverter())
            .build()

    data class TestWorkspace(var workspace: Workspace, var resources: List<WorkspaceResource>? = emptyList())

    /**
     * Create, start, and return a server.
     */
    private fun mockServer(): Pair<HttpServer, String> {
        val srv = HttpServer.create(InetSocketAddress(0), 0)
        srv.start()
        return Pair(srv, "http://localhost:" + srv.address.port)
    }

    private fun mockTLSServer(certName: String): Pair<HttpServer, String> {
        val srv = HttpsServer.create(InetSocketAddress(0), 0)
        val sslContext =
            sslContextFromPEMs(
                Path.of("src/test/fixtures/tls", "$certName.crt").toString(),
                Path.of("src/test/fixtures/tls", "$certName.key").toString(),
                "",
            )
        srv.httpsConfigurator = HttpsConfigurator(sslContext)
        srv.start()
        return Pair(srv, "https://localhost:" + srv.address.port)
    }

    private fun mockProxy(): HttpServer {
        val srv = HttpServer.create(InetSocketAddress(0), 0)
        srv.createContext(
            "/",
            BaseHttpHandler("GET") { exchange ->
                if (exchange.requestHeaders.getFirst("Proxy-Authorization") != "Basic Zm9vOmJhcg==") {
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_PROXY_AUTH, 0)
                } else {
                    val conn = URL(exchange.requestURI.toString()).openConnection()
                    exchange.requestHeaders.forEach {
                        conn.setRequestProperty(it.key, it.value.joinToString(","))
                    }
                    val body = InputStreamReader(conn.inputStream).use { it.readText() }.toByteArray()
                    exchange.sendResponseHeaders((conn as HttpURLConnection).responseCode, body.size.toLong())
                    exchange.responseBody.write(body)
                }
            },
        )
        srv.start()
        return srv
    }

    @Test
    fun testUnauthorized() {
        val workspace = DataGen.workspace("ws1")
        val tests = listOf<Pair<String, (CoderRestClient) -> Unit>>(
            "/api/v2/workspaces" to { it.workspaces() },
            "/api/v2/users/me" to { it.me() },
            "/api/v2/buildinfo" to { it.buildInfo() },
            "/api/v2/templates/${workspace.templateID}" to { it.updateWorkspace(workspace) },
        )
        tests.forEach { (endpoint, block) ->
            val (srv, url) = mockServer()
            val client = CoderRestClient(URL(url), "token")
            srv.createContext(
                endpoint,
                BaseHttpHandler("GET") { exchange ->
                    val response = Response("Unauthorized", "You do not have permission to the requested resource")
                    val body = moshi.adapter(Response::class.java).toJson(response).toByteArray()
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_UNAUTHORIZED, body.size.toLong())
                    exchange.responseBody.write(body)
                },
            )
            val ex =
                assertFailsWith(
                    exceptionClass = APIResponseException::class,
                    block = { block(client) },
                )
            assertEquals(true, ex.isUnauthorized)
            srv.stop(0)
        }
    }

    @Test
    fun testToken() {
        val user = DataGen.user()
        val (srv, url) = mockServer()
        srv.createContext(
            "/api/v2/users/me",
            BaseHttpHandler("GET") { exchange ->
                if (exchange.requestHeaders.getFirst("Coder-Session-Token") != "token") {
                    val response = Response("Unauthorized", "You do not have permission to the requested resource")
                    val body = moshi.adapter(Response::class.java).toJson(response).toByteArray()
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_UNAUTHORIZED, body.size.toLong())
                    exchange.responseBody.write(body)
                } else {
                    val body = moshi.adapter(User::class.java).toJson(user).toByteArray()
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.size.toLong())
                    exchange.responseBody.write(body)
                }
            },
        )

        val client = CoderRestClient(URL(url), "token")
        assertEquals(user.username, client.me().username)

        val tests = listOf("invalid", null)
        tests.forEach { token ->
            val ex =
                assertFailsWith(
                    exceptionClass = APIResponseException::class,
                    block = { CoderRestClient(URL(url), token).me() },
                )
            assertEquals(true, ex.isUnauthorized)
        }

        srv.stop(0)
    }

    @Test
    fun testGetsWorkspaces() {
        val tests =
            listOf(
                emptyList(),
                listOf(DataGen.workspace("ws1")),
                listOf(
                    DataGen.workspace("ws1"),
                    DataGen.workspace("ws2"),
                ),
            )
        tests.forEach { workspaces ->
            val (srv, url) = mockServer()
            val client = CoderRestClient(URL(url), "token")
            srv.createContext(
                "/api/v2/workspaces",
                BaseHttpHandler("GET") { exchange ->
                    val response = WorkspacesResponse(workspaces)
                    val body = moshi.adapter(WorkspacesResponse::class.java).toJson(response).toByteArray()
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.size.toLong())
                    exchange.responseBody.write(body)
                },
            )
            assertEquals(workspaces.map { ws -> ws.name }, client.workspaces().map { ws -> ws.name })
            srv.stop(0)
        }
    }

    @Test
    fun testGetsResources() {
        val tests =
            listOf(
                // Nothing, so no resources.
                emptyList(),
                // One workspace with an agent, but no resources.
                listOf(TestWorkspace(DataGen.workspace("ws1", agents = mapOf("agent1" to "3f51da1d-306f-4a40-ac12-62bda5bc5f9a")))),
                // One workspace with an agent and resources that do not match the agent.
                listOf(
                    TestWorkspace(
                        workspace = DataGen.workspace("ws1", agents = mapOf("agent1" to "3f51da1d-306f-4a40-ac12-62bda5bc5f9a")),
                        resources =
                        listOf(
                            DataGen.resource("agent2", "968eea5e-8787-439d-88cd-5bc440216a34"),
                            DataGen.resource("agent3", "72fbc97b-952c-40c8-b1e5-7535f4407728"),
                        ),
                    ),
                ),
                // Multiple workspaces but only one has resources.
                listOf(
                    TestWorkspace(
                        workspace = DataGen.workspace("ws1", agents = mapOf("agent1" to "3f51da1d-306f-4a40-ac12-62bda5bc5f9a")),
                        resources = emptyList(),
                    ),
                    TestWorkspace(
                        workspace = DataGen.workspace("ws2"),
                        resources =
                        listOf(
                            DataGen.resource("agent2", "968eea5e-8787-439d-88cd-5bc440216a34"),
                            DataGen.resource("agent3", "72fbc97b-952c-40c8-b1e5-7535f4407728"),
                        ),
                    ),
                    TestWorkspace(
                        workspace = DataGen.workspace("ws3"),
                        resources = emptyList(),
                    ),
                ),
            )

        val resourceEndpoint = "([^/]+)/resources".toRegex()
        tests.forEach { workspaces ->
            val (srv, url) = mockServer()
            val client = CoderRestClient(URL(url), "token")
            srv.createContext(
                "/api/v2/templateversions",
                BaseHttpHandler("GET") { exchange ->
                    val matches = resourceEndpoint.find(exchange.requestURI.path)
                    if (matches != null) {
                        val templateVersionId = UUID.fromString(matches.destructured.toList()[0])
                        val ws = workspaces.firstOrNull { it.workspace.latestBuild.templateVersionID == templateVersionId }
                        if (ws != null) {
                            val body =
                                moshi.adapter<List<WorkspaceResource>>(
                                    Types.newParameterizedType(List::class.java, WorkspaceResource::class.java),
                                )
                                    .toJson(ws.resources).toByteArray()
                            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.size.toLong())
                            exchange.responseBody.write(body)
                        }
                    }
                },
            )

            workspaces.forEach { ws ->
                assertEquals(ws.resources, client.resources(ws.workspace))
            }

            srv.stop(0)
        }
    }

    @Test
    fun testUpdate() {
        val templates = listOf(DataGen.template())
        val workspaces = listOf(DataGen.workspace("ws1", templateID = templates[0].id))

        val actions = mutableListOf<Pair<String, UUID>>()
        val (srv, url) = mockServer()
        val client = CoderRestClient(URL(url), "token")
        val templateEndpoint = "/api/v2/templates/([^/]+)".toRegex()
        srv.createContext(
            "/api/v2/templates",
            BaseHttpHandler("GET") { exchange ->
                val templateMatch = templateEndpoint.find(exchange.requestURI.path)
                if (templateMatch != null) {
                    val templateId = UUID.fromString(templateMatch.destructured.toList()[0])
                    actions.add(Pair("get_template", templateId))
                    val template = templates.firstOrNull { it.id == templateId }
                    if (template != null) {
                        val body = moshi.adapter(Template::class.java).toJson(template).toByteArray()
                        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.size.toLong())
                        exchange.responseBody.write(body)
                    }
                }
            },
        )
        val buildEndpoint = "/api/v2/workspaces/([^/]+)/builds".toRegex()
        srv.createContext(
            "/api/v2/workspaces",
            BaseHttpHandler("POST") { exchange ->
                val buildMatch = buildEndpoint.find(exchange.requestURI.path)
                if (buildMatch != null) {
                    val workspaceId = UUID.fromString(buildMatch.destructured.toList()[0])
                    val json = moshi.adapter(CreateWorkspaceBuildRequest::class.java).fromJson(exchange.requestBody.source().buffer())
                    if (json == null) {
                        val response = Response("No body", "No body for create workspace build request")
                        val body = moshi.adapter(Response::class.java).toJson(response).toByteArray()
                        exchange.sendResponseHeaders(HttpURLConnection.HTTP_BAD_REQUEST, body.size.toLong())
                        exchange.responseBody.write(body)
                        return@BaseHttpHandler
                    }
                    val ws = workspaces.firstOrNull { it.id == workspaceId }
                    val templateVersionID = json.templateVersionID ?: ws?.latestBuild?.templateVersionID
                    if (json.templateVersionID != null) {
                        actions.add(Pair("update", workspaceId))
                    } else {
                        when (json.transition) {
                            WorkspaceTransition.START -> actions.add(Pair("start", workspaceId))
                            WorkspaceTransition.STOP -> actions.add(Pair("stop", workspaceId))
                            WorkspaceTransition.DELETE -> Unit
                        }
                    }
                    if (ws != null && templateVersionID != null) {
                        val body =
                            moshi.adapter(WorkspaceBuild::class.java).toJson(
                                DataGen.build(
                                    templateVersionID = templateVersionID,
                                ),
                            ).toByteArray()
                        exchange.sendResponseHeaders(HttpURLConnection.HTTP_CREATED, body.size.toLong())
                        exchange.responseBody.write(body)
                    }
                }
            },
        )

        // Fails to stop a non-existent workspace.
        val badWorkspace = DataGen.workspace("bad", templates[0].id)
        val ex =
            assertFailsWith(
                exceptionClass = APIResponseException::class,
                block = { client.updateWorkspace(badWorkspace) },
            )
        assertEquals(
            listOf(
                Pair("get_template", badWorkspace.templateID),
                Pair("update", badWorkspace.id),
            ),
            actions,
        )
        assertContains(ex.message.toString(), "The requested resource could not be found")
        actions.clear()

        with(workspaces[0]) {
            client.updateWorkspace(this)
            val expected =
                listOf(
                    Pair("get_template", templateID),
                    Pair("update", id),
                )
            assertEquals(expected, actions)
            actions.clear()
        }

        srv.stop(0)
    }

    @Test
    fun testValidSelfSignedCert() {
        val settings =
            CoderSettings(
                CoderSettingsState(
                    tlsCAPath = Path.of("src/test/fixtures/tls", "self-signed.crt").toString(),
                    tlsAlternateHostname = "localhost",
                ),
            )
        val user = DataGen.user()
        val (srv, url) = mockTLSServer("self-signed")
        val client = CoderRestClient(URL(url), "token", settings)
        srv.createContext(
            "/api/v2/users/me",
            BaseHttpHandler("GET") { exchange ->
                val body = moshi.adapter(User::class.java).toJson(user).toByteArray()
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.size.toLong())
                exchange.responseBody.write(body)
            },
        )

        assertEquals(user.username, client.me().username)

        srv.stop(0)
    }

    @Test
    fun testWrongHostname() {
        val settings =
            CoderSettings(
                CoderSettingsState(
                    tlsCAPath = Path.of("src/test/fixtures/tls", "self-signed.crt").toString(),
                    tlsAlternateHostname = "fake.example.com",
                ),
            )
        val (srv, url) = mockTLSServer("self-signed")
        val client = CoderRestClient(URL(url), "token", settings)

        assertFailsWith(
            exceptionClass = SSLPeerUnverifiedException::class,
            block = { client.me() },
        )

        srv.stop(0)
    }

    @Test
    fun testCertNotTrusted() {
        val settings =
            CoderSettings(
                CoderSettingsState(
                    tlsCAPath = Path.of("src/test/fixtures/tls", "self-signed.crt").toString(),
                ),
            )
        val (srv, url) = mockTLSServer("no-signing")
        val client = CoderRestClient(URL(url), "token", settings)

        assertFailsWith(
            exceptionClass = SSLHandshakeException::class,
            block = { client.me() },
        )

        srv.stop(0)
    }

    @Test
    fun testValidChain() {
        val settings =
            CoderSettings(
                CoderSettingsState(
                    tlsCAPath = Path.of("src/test/fixtures/tls", "chain-root.crt").toString(),
                ),
            )
        val user = DataGen.user()
        val (srv, url) = mockTLSServer("chain")
        val client = CoderRestClient(URL(url), "token", settings)
        srv.createContext(
            "/api/v2/users/me",
            BaseHttpHandler("GET") { exchange ->
                val body = moshi.adapter(User::class.java).toJson(user).toByteArray()
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.size.toLong())
                exchange.responseBody.write(body)
            },
        )

        assertEquals(user.username, client.me().username)

        srv.stop(0)
    }

    @Test
    fun usesProxy() {
        val settings = CoderSettings(CoderSettingsState())
        val workspaces = listOf(DataGen.workspace("ws1"))
        val (srv1, url1) = mockServer()
        srv1.createContext(
            "/api/v2/workspaces",
            BaseHttpHandler("GET") { exchange ->
                val response = WorkspacesResponse(workspaces)
                val body = moshi.adapter(WorkspacesResponse::class.java).toJson(response).toByteArray()
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.size.toLong())
                exchange.responseBody.write(body)
            },
        )
        val srv2 = mockProxy()
        val client =
            CoderRestClient(
                URL(url1),
                "token",
                settings,
                ProxyValues(
                    "foo",
                    "bar",
                    true,
                    object : ProxySelector() {
                        override fun select(uri: URI): List<Proxy> {
                            return listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("localhost", srv2.address.port)))
                        }

                        override fun connectFailed(
                            uri: URI,
                            sa: SocketAddress,
                            ioe: IOException,
                        ) {
                            getDefault().connectFailed(uri, sa, ioe)
                        }
                    },
                ),
            )

        assertEquals(workspaces.map { ws -> ws.name }, client.workspaces().map { ws -> ws.name })

        srv1.stop(0)
        srv2.stop(0)
    }
}
