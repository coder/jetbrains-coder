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

    // Test data setup
    private companion object {
        val AGENT_1 = AgentTestData(name = "agent_name", id = "9a920eee-47fb-4571-9501-e4b3120c12f2")
        val AGENT_2 = AgentTestData(name = "agent_name_2", id = "fb3daea4-da6b-424d-84c7-36b90574cfef")
        val AGENT_3 = AgentTestData(name = "agent_name_3", id = "b0e4c54d-9ba9-4413-8512-11ca1e826a24")

        val ALL_AGENTS = mapOf(
            AGENT_3.name to AGENT_3.id,
            AGENT_2.name to AGENT_2.id,
            AGENT_1.name to AGENT_1.id
        )

        val SINGLE_AGENT = mapOf(AGENT_3.name to AGENT_3.id)
    }

    @Test
    fun `getMatchingAgent finds agent by name`() {
        val ws = DataGen.workspace("ws", agents = ALL_AGENTS)

        val testCases = listOf(
            AgentMatchTestCase(
                "matches agent_name",
                mapOf("agent" to AGENT_1.name),
                AGENT_1.id
            ),
            AgentMatchTestCase(
                "matches agent_name_2",
                mapOf("agent" to AGENT_2.name),
                AGENT_2.id
            ),
            AgentMatchTestCase(
                "matches agent_name_3",
                mapOf("agent" to AGENT_3.name),
                AGENT_3.id
            )
        )

        testCases.forEach { testCase ->
            assertEquals(
                UUID.fromString(testCase.expectedAgentId),
                getMatchingAgent(testCase.params, ws).id,
                "Failed: ${testCase.description}"
            )
        }
    }

    @Test
    fun `getMatchingAgent finds agent by ID`() {
        val ws = DataGen.workspace("ws", agents = ALL_AGENTS)

        val testCases = listOf(
            AgentMatchTestCase(
                "matches by agent_1 ID",
                mapOf("agent_id" to AGENT_1.id),
                AGENT_1.id
            ),
            AgentMatchTestCase(
                "matches by agent_2 ID",
                mapOf("agent_id" to AGENT_2.id),
                AGENT_2.id
            ),
            AgentMatchTestCase(
                "matches by agent_3 ID",
                mapOf("agent_id" to AGENT_3.id),
                AGENT_3.id
            )
        )

        testCases.forEach { testCase ->
            assertEquals(
                UUID.fromString(testCase.expectedAgentId),
                getMatchingAgent(testCase.params, ws).id,
                "Failed: ${testCase.description}"
            )
        }
    }

    @Test
    fun `getMatchingAgent prefers agent name over agent_id`() {
        val ws = DataGen.workspace("ws", agents = ALL_AGENTS)

        val params = mapOf(
            "agent" to AGENT_3.name,
            "agent_id" to AGENT_2.id
        )

        assertEquals(AGENT_3.uuid, getMatchingAgent(params, ws).id)
    }

    @Test
    fun `getMatchingAgent fails with missing parameters`() {
        val ws = DataGen.workspace("ws", agents = ALL_AGENTS)

        val testCases = listOf(
            AgentFailureTestCase(
                "empty parameters (i.e. no agent name or id provided)",
                emptyMap(),
                MissingArgumentException::class,
                "Unable to determine which agent to connect to; one of \"agent\" or \"agent_id\" must be set because the workspace \"ws\" has more than one agent"
            ),
            AgentFailureTestCase(
                "empty agent name",
                mapOf("agent" to ""),
                MissingArgumentException::class,
                "Unable to determine which agent to connect to; one of \"agent\" or \"agent_id\" must be set because the workspace \"ws\" has more than one agent"
            ),
            AgentFailureTestCase(
                "empty agent id",
                mapOf("agent_id" to ""),
                MissingArgumentException::class,
                "Unable to determine which agent to connect to; one of \"agent\" or \"agent_id\" must be set because the workspace \"ws\" has more than one agent"
            ),
            AgentFailureTestCase(
                "null agent name",
                mapOf("agent" to null),
                MissingArgumentException::class,
                "Unable to determine which agent to connect to; one of \"agent\" or \"agent_id\" must be set because the workspace \"ws\" has more than one agent"
            ),
            AgentFailureTestCase(
                "null agent id",
                mapOf("agent_id" to null),
                MissingArgumentException::class,
                "Unable to determine which agent to connect to; one of \"agent\" or \"agent_id\" must be set because the workspace \"ws\" has more than one agent"
            )
        )

        testCases.forEach { testCase ->
            val ex = assertFailsWith(
                exceptionClass = testCase.expectedException,
                message = "Failed: ${testCase.description}"
            ) {
                getMatchingAgent(testCase.params, ws).id
            }
            assertContains(ex.message.toString(), testCase.expectedMessageFragment)
        }
    }

    @Test
    fun `getMatchingAgent fails with invalid agent references`() {
        val ws = DataGen.workspace("ws", agents = ALL_AGENTS)

        val testCases = listOf(
            AgentFailureTestCase(
                "workspace name instead of agent",
                mapOf("agent" to "ws"),
                IllegalArgumentException::class,
                "The workspace \"ws\" does not have an agent named \"ws\""
            ),
            AgentFailureTestCase(
                "qualified agent name",
                mapOf("agent" to "ws.agent_name"),
                IllegalArgumentException::class,
                "The workspace \"ws\" does not have an agent named \"ws.agent_name\""
            ),
            AgentFailureTestCase(
                "non-existent agent name",
                mapOf("agent" to "agent_name_4"),
                IllegalArgumentException::class,
                "The workspace \"ws\" does not have an agent named \"agent_name_4\""
            ),
            AgentFailureTestCase(
                "invalid UUID format",
                mapOf("agent_id" to "not-a-uuid"),
                IllegalArgumentException::class,
                "The workspace \"ws\" does not have an agent with ID \"not-a-uuid\""
            ),
            AgentFailureTestCase(
                "non-existent agent ID",
                mapOf("agent_id" to "ceaa7bcf-1612-45d7-b484-2e0da9349168"),
                IllegalArgumentException::class,
                "The workspace \"ws\" does not have an agent with ID \"ceaa7bcf-1612-45d7-b484-2e0da9349168\""
            ),
            AgentFailureTestCase(
                "ignores valid agent_id when agent name is invalid",
                mapOf(
                    "agent" to "unknown_agent_name",
                    "agent_id" to "b0e4c54d-9ba9-4413-8512-11ca1e826a24"
                ),
                IllegalArgumentException::class,
                "The workspace \"ws\" does not have an agent named \"unknown_agent_name\""
            )
        )

        testCases.forEach { testCase ->
            val ex = assertFailsWith(
                exceptionClass = testCase.expectedException,
                message = "Failed: ${testCase.description}"
            ) {
                getMatchingAgent(testCase.params, ws).id
            }
            assertContains(ex.message.toString(), testCase.expectedMessageFragment)
        }
    }

    @Test
    fun `getMatchingAgent returns only agent when workspace has one agent`() {
        val ws = DataGen.workspace("ws", agents = SINGLE_AGENT)

        val testCases = listOf(
            "empty parameters (i.e. no agent name or id provided)" to emptyMap(),
            "empty agent name" to mapOf("agent" to ""),
            "empty agent id" to mapOf("agent_id" to ""),
            "null agent name" to mapOf("agent" to null),
            "null agent id" to mapOf("agent_id" to null)
        )

        testCases.forEach { (description, params) ->
            assertEquals(
                AGENT_3.uuid,
                getMatchingAgent(params, ws).id,
                "Failed: $description"
            )
        }
    }

    @Test
    fun `getMatchingAgent fails with invalid references in single-agent workspace`() {
        val ws = DataGen.workspace("ws", agents = SINGLE_AGENT)

        val testCases = listOf(
            AgentFailureTestCase(
                "invalid agent name provided, i.e. the workspace name",
                mapOf("agent" to "ws"),
                IllegalArgumentException::class,
                "The workspace \"ws\" does not have an agent named \"ws\""
            ),
            AgentFailureTestCase(
                "qualified agent name",
                mapOf("agent" to "ws.agent_name_3"),
                IllegalArgumentException::class,
                "The workspace \"ws\" does not have an agent named \"ws.agent_name_3\""
            ),
            AgentFailureTestCase(
                "non-existent agent",
                mapOf("agent" to "agent_name_4"),
                IllegalArgumentException::class,
                "The workspace \"ws\" does not have an agent named \"agent_name_4\""
            ),
            AgentFailureTestCase(
                "non-existent agent ID",
                mapOf("agent_id" to "ceaa7bcf-1612-45d7-b484-2e0da9349168"),
                IllegalArgumentException::class,
                "The workspace \"ws\" does not have an agent with ID \"ceaa7bcf-1612-45d7-b484-2e0da9349168\""
            )
        )

        testCases.forEach { testCase ->
            val ex = assertFailsWith(
                exceptionClass = testCase.expectedException,
                message = "Failed: ${testCase.description}"
            ) {
                getMatchingAgent(testCase.params, ws).id
            }
            assertContains(ex.message.toString(), testCase.expectedMessageFragment)
        }
    }

    @Test
    fun `getMatchingAgent fails when workspace has no agents`() {
        val ws = DataGen.workspace("ws")

        val testCases = listOf(
            AgentFailureTestCase(
                "empty map",
                emptyMap(),
                IllegalArgumentException::class,
                "The workspace \"ws\" has no agents"
            ),
            AgentFailureTestCase(
                "empty agent string",
                mapOf("agent" to ""),
                IllegalArgumentException::class,
                "The workspace \"ws\" has no agents"
            ),
            AgentFailureTestCase(
                "empty agent_id string",
                mapOf("agent_id" to ""),
                IllegalArgumentException::class,
                "The workspace \"ws\" has no agents"
            ),
            AgentFailureTestCase(
                "null agent",
                mapOf("agent" to null),
                IllegalArgumentException::class,
                "The workspace \"ws\" has no agents"
            ),
            AgentFailureTestCase(
                "null agent_id",
                mapOf("agent_id" to null),
                IllegalArgumentException::class,
                "The workspace \"ws\" has no agents"
            ),
            AgentFailureTestCase(
                "valid agent name",
                mapOf("agent" to "agent_name"),
                IllegalArgumentException::class,
                "The workspace \"ws\" has no agents"
            ),
            AgentFailureTestCase(
                "valid agent ID",
                mapOf("agent_id" to "9a920eee-47fb-4571-9501-e4b3120c12f2"),
                IllegalArgumentException::class,
                "The workspace \"ws\" has no agents"
            )
        )

        testCases.forEach { testCase ->
            val ex = assertFailsWith(
                exceptionClass = testCase.expectedException,
                message = "Failed: ${testCase.description}"
            ) {
                getMatchingAgent(testCase.params, ws).id
            }
            assertContains(ex.message.toString(), testCase.expectedMessageFragment)
        }
    }

    @Test
    fun `resolveRedirects follows redirect chain`() {
        val finalServer = mockOkServer()
        val permanentRedirect = mockRedirectServer(finalServer.url, isPermanent = true)
        val temporaryRedirect = mockRedirectServer(permanentRedirect.url, isPermanent = false)

        try {
            assertEquals(
                finalServer.url.toURL(),
                resolveRedirects(temporaryRedirect.url.toURL())
            )
        } finally {
            finalServer.stop()
            permanentRedirect.stop()
            temporaryRedirect.stop()
        }
    }

    @Test
    fun `resolveRedirects fails on infinite redirect loop`() {
        val server = mockRedirectServer(".", isPermanent = false)

        try {
            assertFailsWith<Exception> {
                resolveRedirects(server.url.toURL())
            }
        } finally {
            server.stop()
        }
    }

    internal data class AgentTestData(val name: String, val id: String) {
        val uuid: UUID get() = UUID.fromString(id)
    }

    internal data class AgentMatchTestCase(
        val description: String,
        val params: Map<String, String?>,
        val expectedAgentId: String
    )

    internal data class AgentFailureTestCase(
        val description: String,
        val params: Map<String, String?>,
        val expectedException: kotlin.reflect.KClass<out Exception>,
        val expectedMessageFragment: String
    )

    // Helper classes for cleaner test server management
    private data class TestServer(val httpServer: HttpServer, val url: String) {
        fun stop() = httpServer.stop(0)
    }

    private fun mockServer(handler: HttpHandler): TestServer {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/", handler)
        server.start()
        return TestServer(server, "http://localhost:${server.address.port}")
    }

    private fun mockOkServer(): TestServer = mockServer { exchange ->
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1)
        exchange.close()
    }

    private fun mockRedirectServer(location: String, isPermanent: Boolean): TestServer =
        mockServer { exchange ->
            exchange.responseHeaders.set("Location", location)
            exchange.sendResponseHeaders(
                if (isPermanent) HttpURLConnection.HTTP_MOVED_PERM else HttpURLConnection.HTTP_MOVED_TEMP,
                -1
            )
            exchange.close()
        }
}