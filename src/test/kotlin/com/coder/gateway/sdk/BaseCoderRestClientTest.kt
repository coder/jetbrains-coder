package com.coder.gateway.sdk

import kotlin.test.Test
import kotlin.test.assertEquals

import com.coder.gateway.sdk.convertors.InstantConverter
import com.coder.gateway.sdk.ex.WorkspaceResponseException
import com.coder.gateway.sdk.v2.models.*
import com.coder.gateway.services.CoderSettingsState
import com.coder.gateway.settings.CoderSettings
import com.coder.gateway.util.sslContextFromPEMs
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
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
import java.time.Instant
import java.util.UUID
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.test.assertFailsWith

class BaseHttpHandler(private val method: String,
                      private val handler: (exchange: HttpExchange) -> Unit): HttpHandler {
    override fun handle(exchange: HttpExchange) {
        try {
            if (exchange.requestMethod != method) {
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_BAD_METHOD, 0)
            } else {
                handler(exchange)
                if (exchange.responseCode == -1) {
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, 0)
                }
            }
        } catch (ex: Exception) {
            // If we get here it is because of developer error.
            println(ex)
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_INTERNAL_ERROR, 0)
        }
        exchange.close()
    }
}

class BaseCoderRestClientTest {
    data class TestWorkspace(var workspace: Workspace, var resources: List<WorkspaceResource>? = emptyList())

    /**
     * Create, start, and return a server.
     */
    private fun mockServer(): Pair<HttpServer, String> {
        val srv = HttpServer.create(InetSocketAddress(0), 0)
        srv.start()
        return Pair(srv, "http://localhost:" + srv.address.port)
    }

    private fun toJson(src: Any?): String {
        return GsonBuilder().registerTypeAdapter(Instant::class.java, InstantConverter()).create().toJson(src)
    }

    private fun mockTLSServer(certName: String): Pair<HttpServer, String> {
        val srv = HttpsServer.create(InetSocketAddress(0), 0)
        val sslContext = sslContextFromPEMs(
            Path.of("src/test/fixtures/tls", "$certName.crt").toString(),
            Path.of("src/test/fixtures/tls", "$certName.key").toString(),
            "")
        srv.httpsConfigurator = HttpsConfigurator(sslContext)
        srv.start()
        return Pair(srv, "https://localhost:" + srv.address.port)
    }
    private fun mockProxy(): HttpServer {
        val srv = HttpServer.create(InetSocketAddress(0), 0)
        srv.createContext("/", BaseHttpHandler("GET") { exchange ->
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
        })
        srv.start()
        return srv
    }

    @Test
    fun testGetsWorkspaces() {
        val tests = listOf(
            emptyList(),
            listOf(DataGen.workspace("ws1")),
            listOf(DataGen.workspace("ws1"),
                DataGen.workspace("ws2")),
        )
        tests.forEach { workspaces ->
            val (srv, url) = mockServer()
            val client = BaseCoderRestClient(URL(url), "token")
            srv.createContext("/api/v2/workspaces", BaseHttpHandler("GET") { exchange ->
                val body = toJson(WorkspacesResponse(workspaces, workspaces.size)).toByteArray()
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.size.toLong())
                exchange.responseBody.write(body)
            })
            assertEquals(workspaces.map{ ws -> ws.name }, client.workspaces().map{ ws -> ws.name })
            srv.stop(0)
        }
    }

    @Test
    fun testGetsResources() {
        val tests = listOf(
            // Nothing, so no resources.
            emptyList(),
            // One workspace with an agent, but no resources.
            listOf(TestWorkspace(DataGen.workspace("ws1", agents = mapOf("agent1" to "3f51da1d-306f-4a40-ac12-62bda5bc5f9a")))),
            // One workspace with an agent and resources that do not match the agent.
            listOf(TestWorkspace(
                workspace = DataGen.workspace("ws1", agents = mapOf("agent1" to "3f51da1d-306f-4a40-ac12-62bda5bc5f9a")),
                resources = listOf(DataGen.resource("agent2", "968eea5e-8787-439d-88cd-5bc440216a34"),
                    DataGen.resource("agent3", "72fbc97b-952c-40c8-b1e5-7535f4407728")))),
            // Multiple workspaces but only one has resources.
            listOf(TestWorkspace(
                workspace = DataGen.workspace("ws1", agents = mapOf("agent1" to "3f51da1d-306f-4a40-ac12-62bda5bc5f9a")),
                resources = emptyList()),
                TestWorkspace(
                    workspace = DataGen.workspace("ws2"),
                    resources = listOf(DataGen.resource("agent2", "968eea5e-8787-439d-88cd-5bc440216a34"),
                        DataGen.resource("agent3", "72fbc97b-952c-40c8-b1e5-7535f4407728"))),
                TestWorkspace(
                    workspace = DataGen.workspace("ws3"),
                    resources = emptyList())),
        )

        val resourceEndpoint = "([^/]+)/resources".toRegex()
        tests.forEach { workspaces ->
            val (srv, url) = mockServer()
            val client = BaseCoderRestClient(URL(url), "token")
            srv.createContext("/api/v2/templateversions", BaseHttpHandler("GET") { exchange ->
                val matches = resourceEndpoint.find(exchange.requestURI.path)
                if (matches != null) {
                    val templateVersionId = UUID.fromString(matches.destructured.toList()[0])
                    val ws = workspaces.firstOrNull { it.workspace.latestBuild.templateVersionID == templateVersionId }
                    if (ws != null) {
                        val body = toJson(ws.resources).toByteArray()
                        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.size.toLong())
                        exchange.responseBody.write(body)
                    }
                }
            })

            workspaces.forEach { ws ->
                assertEquals(ws.resources, client.resources(ws.workspace))
            }

            srv.stop(0)
        }
    }

    @Test
    fun testUpdate() {
        val templates = listOf(DataGen.template("template"))
        val workspaces = listOf(
            DataGen.workspace("ws1", templateID = templates[0].id),
            DataGen.workspace("ws2", templateID = templates[0].id, transition = WorkspaceTransition.STOP))

        val actions = mutableListOf<Pair<String, UUID>>()
        val (srv, url) = mockServer()
        val client = BaseCoderRestClient(URL(url), "token")
        val templateEndpoint = "/api/v2/templates/([^/]+)".toRegex()
        srv.createContext("/api/v2/templates", BaseHttpHandler("GET") { exchange ->
            val templateMatch = templateEndpoint.find(exchange.requestURI.path)
            if (templateMatch != null) {
                val templateId = UUID.fromString(templateMatch.destructured.toList()[0])
                actions.add(Pair("get_template", templateId))
                val template = templates.firstOrNull { it.id == templateId }
                if (template != null) {
                    val body = toJson(template).toByteArray()
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.size.toLong())
                    exchange.responseBody.write(body)
                }
            }
        })
        val buildEndpoint = "/api/v2/workspaces/([^/]+)/builds".toRegex()
        srv.createContext("/api/v2/workspaces", BaseHttpHandler("POST") { exchange ->
            val buildMatch = buildEndpoint.find(exchange.requestURI.path)
            if (buildMatch != null) {
                val workspaceId = UUID.fromString(buildMatch.destructured.toList()[0])
                val json = Gson().fromJson(InputStreamReader(exchange.requestBody), CreateWorkspaceBuildRequest::class.java)
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
                    val body = toJson(DataGen.build(
                        workspaceID = ws.id,
                        workspaceName = ws.name,
                        ownerID = ws.ownerID,
                        ownerName = ws.ownerName,
                        templateVersionID = templateVersionID,
                        transition = json.transition)).toByteArray()
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_CREATED, body.size.toLong())
                    exchange.responseBody.write(body)
                }
            }
        })

        // Fails to stop a non-existent workspace.
        val badWorkspace = DataGen.workspace("bad")
        assertFailsWith(
            exceptionClass = WorkspaceResponseException::class,
            block = { client.updateWorkspace(badWorkspace.id, badWorkspace.name, badWorkspace.latestBuild.transition, badWorkspace.templateID) })
        assertEquals(listOf(Pair("stop", badWorkspace.id)), actions)
        actions.clear()

        // When workspace is started it should stop first.
        with(workspaces[0]) {
            client.updateWorkspace(id, name, latestBuild.transition, templateID)
            val expected = listOf(
                Pair("stop", id),
                Pair("get_template", templateID),
                Pair("update", id))
            assertEquals(expected, actions)
            actions.clear()
        }

        // When workspace is stopped it will not stop first.
        with(workspaces[1]) {
            client.updateWorkspace(id, name, latestBuild.transition, templateID)
            val expected = listOf(
                Pair("get_template", templateID),
                Pair("update", id))
            assertEquals(expected, actions)
            actions.clear()
        }

        srv.stop(0)
    }

    @Test
    fun testValidSelfSignedCert() {
        val settings = CoderSettings(CoderSettingsState(
            tlsCAPath = Path.of("src/test/fixtures/tls", "self-signed.crt").toString(),
            tlsAlternateHostname = "localhost"))
        val user = DataGen.user()
        val (srv, url) = mockTLSServer("self-signed")
        val client = BaseCoderRestClient(URL(url), "token", settings)
        srv.createContext("/api/v2/users/me", BaseHttpHandler("GET") { exchange ->
            val body = toJson(user).toByteArray()
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.size.toLong())
            exchange.responseBody.write(body)
        })

        assertEquals(user.username, client.me().username)

        srv.stop(0)
    }

    @Test
    fun testWrongHostname() {
        val settings = CoderSettings(CoderSettingsState(
            tlsCAPath = Path.of("src/test/fixtures/tls", "self-signed.crt").toString(),
            tlsAlternateHostname = "fake.example.com"))
        val (srv, url) = mockTLSServer("self-signed")
        val client = BaseCoderRestClient(URL(url), "token", settings)

        assertFailsWith(
            exceptionClass = SSLPeerUnverifiedException::class,
            block = { client.me() })

        srv.stop(0)
    }

    @Test
    fun testCertNotTrusted() {
        val settings = CoderSettings(CoderSettingsState(
            tlsCAPath = Path.of("src/test/fixtures/tls", "self-signed.crt").toString()))
        val (srv, url) = mockTLSServer("no-signing")
        val client = BaseCoderRestClient(URL(url), "token", settings)

        assertFailsWith(
            exceptionClass = SSLHandshakeException::class,
            block = { client.me() })

        srv.stop(0)
    }

    @Test
    fun testValidChain() {
        val settings = CoderSettings(CoderSettingsState(
            tlsCAPath = Path.of("src/test/fixtures/tls", "chain-root.crt").toString()))
        val user = DataGen.user()
        val (srv, url) = mockTLSServer("chain")
        val client = BaseCoderRestClient(URL(url), "token", settings)
        srv.createContext("/api/v2/users/me", BaseHttpHandler("GET") { exchange ->
            val body = toJson(user).toByteArray()
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.size.toLong())
            exchange.responseBody.write(body)
        })

        assertEquals(user.username, client.me().username)

        srv.stop(0)
    }

    @Test
    fun usesProxy() {
        val settings = CoderSettings(CoderSettingsState())
        val workspaces = listOf(DataGen.workspace("ws1"))
        val (srv1, url1) = mockServer()
        srv1.createContext("/api/v2/workspaces", BaseHttpHandler("GET") { exchange ->
            val body = toJson(WorkspacesResponse(workspaces, workspaces.size)).toByteArray()
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.size.toLong())
            exchange.responseBody.write(body)
        })
        val srv2 = mockProxy()
        val client = BaseCoderRestClient(URL(url1), "token", settings, ProxyValues(
            "foo",
            "bar",
            true,
            object : ProxySelector() {
                override fun select(uri: URI): List<Proxy> {
                    return listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("localhost", srv2.address.port)))
                }

                override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) {
                    getDefault().connectFailed(uri, sa, ioe);
                }
            }
        ))

        assertEquals(workspaces.map{ ws -> ws.name }, client.workspaces().map{ ws -> ws.name })

        srv1.stop(0)
        srv2.stop(0)
    }
}
