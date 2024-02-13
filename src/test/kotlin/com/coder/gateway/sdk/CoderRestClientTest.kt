package com.coder.gateway.sdk

import kotlin.test.Test
import kotlin.test.assertEquals

import com.coder.gateway.sdk.convertors.InstantConverter
import com.coder.gateway.sdk.v2.models.*
import com.coder.gateway.services.CoderSettings
import com.coder.gateway.services.CoderSettingsState
import com.coder.gateway.util.sslContextFromPEMs

import com.google.gson.GsonBuilder
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
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.test.assertFailsWith

class CoderRestClientTest {
    data class TestWorkspace(var workspace: Workspace, var resources: List<WorkspaceResource>? = emptyList())

    /**
     * Create, start, and return a server that mocks the Coder API.
     *
     * The resources map to the workspace index (to avoid having to manually
     * hardcode IDs everywhere since you cannot use variables in the where
     * blocks).
     */
    private fun mockServer(workspaces: List<TestWorkspace>): Pair<HttpServer, String> {
        val srv = HttpServer.create(InetSocketAddress(0), 0)
        addServerContext(srv, workspaces)
        srv.start()
        return Pair(srv, "http://localhost:" + srv.address.port)
    }

    private val resourceEndpoint = "/api/v2/templateversions/([^/]+)/resources".toRegex()

    private fun addServerContext(srv: HttpServer, workspaces: List<TestWorkspace> = emptyList()) {
        srv.createContext("/")  { exchange ->
            var code = HttpURLConnection.HTTP_NOT_FOUND
            var response = "not found"
            try {
                val matches = resourceEndpoint.find(exchange.requestURI.path)
                if (matches != null) {
                    val templateVersionId = UUID.fromString(matches.destructured.toList()[0])
                    val ws = workspaces.first { it.workspace.latestBuild.templateVersionID == templateVersionId }
                    code = HttpURLConnection.HTTP_OK
                    response = GsonBuilder().registerTypeAdapter(Instant::class.java, InstantConverter())
                        .create().toJson(ws.resources)
                } else if (exchange.requestURI.path == "/api/v2/workspaces") {
                    code = HttpsURLConnection.HTTP_OK
                    response = GsonBuilder().registerTypeAdapter(Instant::class.java, InstantConverter())
                        .create().toJson(WorkspacesResponse(workspaces.map{ it.workspace }, workspaces.size))
                } else if (exchange.requestURI.path == "/api/v2/users/me") {
                    code = HttpsURLConnection.HTTP_OK
                    val user = User(
                        UUID.randomUUID(),
                        "tester",
                        "tester@example.com",
                        Instant.now(),
                        Instant.now(),
                        UserStatus.ACTIVE,
                        listOf(),
                        listOf(),
                        "",
                    )
                    response = GsonBuilder().registerTypeAdapter(Instant::class.java, InstantConverter())
                        .create().toJson(user)
                }
            } catch (ex: Exception) {
                // This will be a developer error.
                code = HttpURLConnection.HTTP_INTERNAL_ERROR
                response = ex.message.toString()
                println(ex.message) // Print since it will not show up in the error.
            }

            val body = response.toByteArray()
            exchange.sendResponseHeaders(code, body.size.toLong())
            exchange.responseBody.write(body)
            exchange.close()
        }
    }

    private fun mockTLSServer(certName: String, workspaces: List<TestWorkspace> = emptyList()): Pair<HttpServer, String> {
        val srv = HttpsServer.create(InetSocketAddress(0), 0)
        val sslContext = sslContextFromPEMs(
            Path.of("src/test/fixtures/tls", "$certName.crt").toString(),
            Path.of("src/test/fixtures/tls", "$certName.key").toString(),
            "")
        srv.httpsConfigurator = HttpsConfigurator(sslContext)
        addServerContext(srv, workspaces)
        srv.start()
        return Pair(srv, "https://localhost:" + srv.address.port)
    }
    private fun mockProxy(): HttpServer {
        val srv = HttpServer.create(InetSocketAddress(0), 0)
        srv.createContext("/")  { exchange ->
            var code: Int
            var response: String

            if (exchange.requestHeaders.getFirst("Proxy-Authorization") != "Basic Zm9vOmJhcg==") {
                code = HttpURLConnection.HTTP_PROXY_AUTH
                response = "authentication required"
            } else {
                try {
                    val conn = URL(exchange.requestURI.toString()).openConnection()
                    exchange.requestHeaders.forEach {
                        conn.setRequestProperty(it.key, it.value.joinToString(","))
                    }
                    response = InputStreamReader(conn.inputStream).use { it.readText() }
                    code = (conn as HttpURLConnection).responseCode
                } catch (error: Exception) {
                    code = HttpURLConnection.HTTP_INTERNAL_ERROR
                    response = error.message.toString()
                    println(error) // Print since it will not show up in the error.
                }
            }

            val body = response.toByteArray()
            exchange.sendResponseHeaders(code, body.size.toLong())
            exchange.responseBody.write(body)
            exchange.close()
        }
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
        tests.forEach {
            val (srv, url) = mockServer(it.map{ ws -> TestWorkspace(ws) })
            val client = CoderRestClient(URL(url), "token")
            assertEquals(it.map{ ws -> ws.name }, client.workspaces().map{ ws -> ws.name })
            srv.stop(0)
        }
    }

    @Test
    fun testGetsResources() {
        val tests = listOf(
            // Nothing, so no resources.
            emptyList(),
            // One workspace with an agent, but no resources.
            listOf(TestWorkspace(DataGen.workspace("ws1", mapOf("agent1" to "3f51da1d-306f-4a40-ac12-62bda5bc5f9a")))),
            // One workspace with an agent and resources that do not match the agent.
            listOf(TestWorkspace(
                workspace = DataGen.workspace("ws1", mapOf("agent1" to "3f51da1d-306f-4a40-ac12-62bda5bc5f9a")),
                resources = listOf(DataGen.resource("agent2", "968eea5e-8787-439d-88cd-5bc440216a34"),
                    DataGen.resource("agent3", "72fbc97b-952c-40c8-b1e5-7535f4407728")))),
            // Multiple workspaces but only one has resources.
            listOf(TestWorkspace(
                workspace = DataGen.workspace("ws1", mapOf("agent1" to "3f51da1d-306f-4a40-ac12-62bda5bc5f9a")),
                resources = emptyList()),
                TestWorkspace(
                    workspace = DataGen.workspace("ws2"),
                    resources = listOf(DataGen.resource("agent2", "968eea5e-8787-439d-88cd-5bc440216a34"),
                        DataGen.resource("agent3", "72fbc97b-952c-40c8-b1e5-7535f4407728"))),
                TestWorkspace(
                    workspace = DataGen.workspace("ws3"),
                    resources = emptyList())),
        )

        tests.forEach {
            val (srv, url) = mockServer(it)
            val client = CoderRestClient(URL(url), "token")

            it.forEach { ws->
                assertEquals(ws.resources, client.resources(ws.workspace))
            }

            srv.stop(0)
        }
    }

    @Test
    fun testValidSelfSignedCert() {
        val settings = CoderSettings(CoderSettingsState(
            tlsCAPath = Path.of("src/test/fixtures/tls", "self-signed.crt").toString(),
            tlsAlternateHostname = "localhost"))
        val (srv, url) = mockTLSServer("self-signed")
        val client = CoderRestClient(URL(url), "token", settings)

        assertEquals("tester", client.me().username)

        srv.stop(0)
    }

    @Test
    fun testWrongHostname() {
        val settings = CoderSettings(CoderSettingsState(
            tlsCAPath = Path.of("src/test/fixtures/tls", "self-signed.crt").toString(),
            tlsAlternateHostname = "fake.example.com"))
        val (srv, url) = mockTLSServer("self-signed")
        val client = CoderRestClient(URL(url), "token", settings)

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
        val client = CoderRestClient(URL(url), "token", settings)

        assertFailsWith(
            exceptionClass = SSLHandshakeException::class,
            block = { client.me() })

        srv.stop(0)
    }

    @Test
    fun testValidChain() {
        val settings = CoderSettings(CoderSettingsState(
            tlsCAPath = Path.of("src/test/fixtures/tls", "chain-root.crt").toString()))
        val (srv, url) = mockTLSServer("chain")
        val client = CoderRestClient(URL(url), "token", settings)

        assertEquals("tester", client.me().username)

        srv.stop(0)
    }

    @Test
    fun usesProxy() {
        val settings = CoderSettings(CoderSettingsState())
        val workspaces = listOf(TestWorkspace(DataGen.workspace("ws1")))
        val (srv1, url1) = mockServer(workspaces)
        val srv2 = mockProxy()
        val client = CoderRestClient(URL(url1), "token", settings, ProxyValues(
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

        assertEquals(workspaces.map{ ws -> ws.workspace.name }, client.workspaces().map{ ws -> ws.name })

        srv1.stop(0)
        srv2.stop(0)
    }
}

