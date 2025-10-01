package com.coder.gateway.util

import com.coder.gateway.sdk.DataGen
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class LinkHandlerTest {
    /**
     * Create, start, and return a server that uses the provided handler.
     */
    private fun mockServer(handler: HttpHandler): Pair<HttpServer, String> {
        val srv = HttpServer.create(InetSocketAddress(0), 0)
        srv.createContext("/", handler)
        srv.start()
        return Pair(srv, "http://localhost:" + srv.address.port)
    }

    /**
     * Create, start, and return a server that mocks redirects.
     */
    private fun mockRedirectServer(
        location: String,
        temp: Boolean,
    ): Pair<HttpServer, String> = mockServer { exchange ->
        exchange.responseHeaders.set("Location", location)
        exchange.sendResponseHeaders(
            if (temp) HttpURLConnection.HTTP_MOVED_TEMP else HttpURLConnection.HTTP_MOVED_PERM,
            -1,
        )
        exchange.close()
    }

    private val agents =
        mapOf(
            "agent_name_3" to "b0e4c54d-9ba9-4413-8512-11ca1e826a24",
            "agent_name_2" to "fb3daea4-da6b-424d-84c7-36b90574cfef",
            "agent_name" to "9a920eee-47fb-4571-9501-e4b3120c12f2",
        )
    private val oneAgent =
        mapOf(
            "agent_name_3" to "b0e4c54d-9ba9-4413-8512-11ca1e826a24",
        )

    @Test
    fun getMatchingAgent() {
        val ws = DataGen.workspace("ws", agents = agents)

        val tests =
            listOf(
                Pair(mapOf("agent" to "agent_name"), "9a920eee-47fb-4571-9501-e4b3120c12f2"),
                Pair(
                    mapOf("agent_id" to "9a920eee-47fb-4571-9501-e4b3120c12f2"),
                    "9a920eee-47fb-4571-9501-e4b3120c12f2"
                ),
                Pair(mapOf("agent" to "agent_name_2"), "fb3daea4-da6b-424d-84c7-36b90574cfef"),
                Pair(
                    mapOf("agent_id" to "fb3daea4-da6b-424d-84c7-36b90574cfef"),
                    "fb3daea4-da6b-424d-84c7-36b90574cfef"
                ),
                Pair(mapOf("agent" to "agent_name_3"), "b0e4c54d-9ba9-4413-8512-11ca1e826a24"),
                Pair(
                    mapOf("agent_id" to "b0e4c54d-9ba9-4413-8512-11ca1e826a24"),
                    "b0e4c54d-9ba9-4413-8512-11ca1e826a24"
                ),
                // Prefer agent name.
                Pair(
                    mapOf(
                        "agent" to "agent_name_3",
                        "agent_id" to "b0e4c54d-9ba9-4413-8512-11ca1e826a24",
                    ),
                    "b0e4c54d-9ba9-4413-8512-11ca1e826a24",
                ),
            )

        tests.forEach {
            assertEquals(UUID.fromString(it.second), getMatchingAgent(it.first, ws).id)
        }
    }

    @Test
    fun failsToGetMatchingAgent() {
        val ws = DataGen.workspace("ws", agents = agents)
        val tests =
            listOf(
                Triple(emptyMap(), MissingArgumentException::class, "Unable to determine"),
                Triple(mapOf("agent" to ""), MissingArgumentException::class, "Unable to determine"),
                Triple(mapOf("agent_id" to ""), MissingArgumentException::class, "Unable to determine"),
                Triple(mapOf("agent" to null), MissingArgumentException::class, "Unable to determine"),
                Triple(mapOf("agent_id" to null), MissingArgumentException::class, "Unable to determine"),
                Triple(mapOf("agent" to "ws"), IllegalArgumentException::class, "agent named"),
                Triple(mapOf("agent" to "ws.agent_name"), IllegalArgumentException::class, "agent named"),
                Triple(mapOf("agent" to "agent_name_4"), IllegalArgumentException::class, "agent named"),
                Triple(mapOf("agent_id" to "not-a-uuid"), IllegalArgumentException::class, "agent with ID"),
                Triple(
                    mapOf("agent_id" to "ceaa7bcf-1612-45d7-b484-2e0da9349168"),
                    IllegalArgumentException::class,
                    "agent with ID"
                ),
                // Will ignore agent_id if agent is set even if agent_id matches.
                Triple(
                    mapOf(
                        "agent" to "unknown_agent_name",
                        "agent_id" to "ceaa7bcf-1612-45d7-b484-2e0da9349168",
                    ),
                    IllegalArgumentException::class,
                    "The workspace \"ws\" does not have an agent named \"unknown_agent_name\"",
                ),
            )

        tests.forEach {
            val ex =
                assertFailsWith(
                    exceptionClass = it.second,
                    block = { getMatchingAgent(it.first, ws).id },
                )
            assertContains(ex.message.toString(), it.third)
        }
    }

    @Test
    fun getsFirstAgentWhenOnlyOne() {
        val ws = DataGen.workspace("ws", agents = oneAgent)
        val tests =
            listOf(
                emptyMap(),
                mapOf("agent" to ""),
                mapOf("agent_id" to ""),
                mapOf("agent" to null),
                mapOf("agent_id" to null),
            )

        tests.forEach {
            assertEquals(
                UUID.fromString("b0e4c54d-9ba9-4413-8512-11ca1e826a24"),
                getMatchingAgent(
                    it,
                    ws,
                ).id,
            )
        }
    }

    @Test
    fun failsToGetAgentWhenOnlyOne() {
        val ws = DataGen.workspace("ws", agents = oneAgent)
        val tests =
            listOf(
                Triple(mapOf("agent" to "ws"), IllegalArgumentException::class, "agent named"),
                Triple(mapOf("agent" to "ws.agent_name_3"), IllegalArgumentException::class, "agent named"),
                Triple(mapOf("agent" to "agent_name_4"), IllegalArgumentException::class, "agent named"),
                Triple(
                    mapOf("agent_id" to "ceaa7bcf-1612-45d7-b484-2e0da9349168"),
                    IllegalArgumentException::class,
                    "agent with ID"
                ),
            )

        tests.forEach {
            val ex =
                assertFailsWith(
                    exceptionClass = it.second,
                    block = { getMatchingAgent(it.first, ws).id },
                )
            assertContains(ex.message.toString(), it.third)
        }
    }

    @Test
    fun failsToGetAgentWithoutAgents() {
        val ws = DataGen.workspace("ws")
        val tests =
            listOf(
                Triple(emptyMap(), IllegalArgumentException::class, "has no agents"),
                Triple(mapOf("agent" to ""), IllegalArgumentException::class, "has no agents"),
                Triple(mapOf("agent_id" to ""), IllegalArgumentException::class, "has no agents"),
                Triple(mapOf("agent" to null), IllegalArgumentException::class, "has no agents"),
                Triple(mapOf("agent_id" to null), IllegalArgumentException::class, "has no agents"),
                Triple(mapOf("agent" to "agent_name"), IllegalArgumentException::class, "has no agents"),
                Triple(
                    mapOf("agent_id" to "9a920eee-47fb-4571-9501-e4b3120c12f2"),
                    IllegalArgumentException::class,
                    "has no agents"
                ),
            )

        tests.forEach {
            val ex =
                assertFailsWith(
                    exceptionClass = it.second,
                    block = { getMatchingAgent(it.first, ws).id },
                )
            assertContains(ex.message.toString(), it.third)
        }
    }

    @Test
    fun followsRedirects() {
        val (srv1, url1) =
            mockServer { exchange ->
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1)
                exchange.close()
            }
        val (srv2, url2) = mockRedirectServer(url1, false)
        val (srv3, url3) = mockRedirectServer(url2, true)

        assertEquals(url1.toURL(), resolveRedirects(java.net.URL(url3)))

        srv1.stop(0)
        srv2.stop(0)
        srv3.stop(0)
    }

    @Test
    fun followsMaximumRedirects() {
        val (srv, url) = mockRedirectServer(".", true)

        assertFailsWith(
            exceptionClass = Exception::class,
            block = { resolveRedirects(java.net.URL(url)) },
        )

        srv.stop(0)
    }
}
