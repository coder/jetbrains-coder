package com.coder.gateway

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

import com.coder.gateway.sdk.DataGen
import java.util.UUID

internal class CoderGatewayConnectionProviderTest {
    private val agents = mapOf(
        "agent_name_3" to "b0e4c54d-9ba9-4413-8512-11ca1e826a24",
        "agent_name_2" to "fb3daea4-da6b-424d-84c7-36b90574cfef",
        "agent_name" to "9a920eee-47fb-4571-9501-e4b3120c12f2",
    )
    private val oneAgent = mapOf(
        "agent_name_3" to "b0e4c54d-9ba9-4413-8512-11ca1e826a24"
    )

    @Test
    fun getMatchingAgent() {
        val ws = DataGen.workspace("ws", agents = agents)

        val tests = listOf(
            Pair(mapOf("agent" to "agent_name"), "9a920eee-47fb-4571-9501-e4b3120c12f2"),
            Pair(mapOf("agent_id" to "9a920eee-47fb-4571-9501-e4b3120c12f2"), "9a920eee-47fb-4571-9501-e4b3120c12f2"),
            Pair(mapOf("agent" to "agent_name_2"), "fb3daea4-da6b-424d-84c7-36b90574cfef"),
            Pair(mapOf("agent_id" to "fb3daea4-da6b-424d-84c7-36b90574cfef"), "fb3daea4-da6b-424d-84c7-36b90574cfef"),
            Pair(mapOf("agent" to "agent_name_3"), "b0e4c54d-9ba9-4413-8512-11ca1e826a24"),
            Pair(mapOf("agent_id" to "b0e4c54d-9ba9-4413-8512-11ca1e826a24"), "b0e4c54d-9ba9-4413-8512-11ca1e826a24"),
            // Prefer agent_id.
            Pair(mapOf("agent" to "agent_name",
                "agent_id" to "b0e4c54d-9ba9-4413-8512-11ca1e826a24"), "b0e4c54d-9ba9-4413-8512-11ca1e826a24"),
        )

        tests.forEach {
            assertEquals(UUID.fromString(it.second), CoderGatewayConnectionProvider.getMatchingAgent(it.first, ws).agentID)
        }
    }

    @Test
    fun failsToGetMatchingAgent() {
        val ws = DataGen.workspace("ws", agents = agents)
        val tests = listOf(
            Triple(emptyMap(), MissingArgumentException::class, "Unable to determine"),
            Triple(mapOf("agent" to ""), MissingArgumentException::class, "Unable to determine"),
            Triple(mapOf("agent_id" to ""), MissingArgumentException::class, "Unable to determine"),
            Triple(mapOf("agent" to null), MissingArgumentException::class, "Unable to determine"),
            Triple(mapOf("agent_id" to null), MissingArgumentException::class, "Unable to determine"),
            Triple(mapOf("agent" to "ws"), IllegalArgumentException::class, "agent named"),
            Triple(mapOf("agent" to "ws.agent_name"), IllegalArgumentException::class, "agent named"),
            Triple(mapOf("agent" to "agent_name_4"), IllegalArgumentException::class, "agent named"),
            Triple(mapOf("agent_id" to "not-a-uuid"), IllegalArgumentException::class, "agent with ID"),
            Triple(mapOf("agent_id" to "ceaa7bcf-1612-45d7-b484-2e0da9349168"), IllegalArgumentException::class, "agent with ID"),
            // Will ignore agent if agent_id is set even if agent matches.
            Triple(mapOf("agent" to "agent_name",
                "agent_id" to "ceaa7bcf-1612-45d7-b484-2e0da9349168"), IllegalArgumentException::class, "agent with ID"),
        )

        tests.forEach {
            val ex = assertFailsWith(
                exceptionClass = it.second,
                block = { CoderGatewayConnectionProvider.getMatchingAgent(it.first, ws) })
            assertContains(ex.message.toString(), it.third)
        }
    }

    @Test
    fun getsFirstAgentWhenOnlyOne() {
        val ws = DataGen.workspace("ws", agents = oneAgent)
        val tests = listOf(
            emptyMap(),
            mapOf("agent" to ""),
            mapOf("agent_id" to ""),
            mapOf("agent" to null),
            mapOf("agent_id" to null),
        )

        tests.forEach {
            assertEquals(UUID.fromString("b0e4c54d-9ba9-4413-8512-11ca1e826a24"), CoderGatewayConnectionProvider.getMatchingAgent(it, ws).agentID)
        }
    }

    @Test
    fun failsToGetAgentWhenOnlyOne() {
        val ws = DataGen.workspace("ws", agents = oneAgent)
        val tests = listOf(
            Triple(mapOf("agent" to "ws"), IllegalArgumentException::class, "agent named"),
            Triple(mapOf("agent" to "ws.agent_name_3"), IllegalArgumentException::class, "agent named"),
            Triple(mapOf("agent" to "agent_name_4"), IllegalArgumentException::class, "agent named"),
            Triple(mapOf("agent_id" to "ceaa7bcf-1612-45d7-b484-2e0da9349168"), IllegalArgumentException::class, "agent with ID"),
        )

        tests.forEach {
            val ex = assertFailsWith(
                exceptionClass = it.second,
                block = { CoderGatewayConnectionProvider.getMatchingAgent(it.first, ws) })
            assertContains(ex.message.toString(), it.third)
        }
    }

    @Test
    fun failsToGetAgentWithoutAgents(){
        val ws = DataGen.workspace("ws")
        val tests = listOf(
            Triple(emptyMap(), IllegalArgumentException::class, "has no agents"),
            Triple(mapOf("agent" to ""), IllegalArgumentException::class, "has no agents"),
            Triple(mapOf("agent_id" to ""), IllegalArgumentException::class, "has no agents"),
            Triple(mapOf("agent" to null), IllegalArgumentException::class, "has no agents"),
            Triple(mapOf("agent_id" to null), IllegalArgumentException::class, "has no agents"),
            Triple(mapOf("agent" to "agent_name"), IllegalArgumentException::class, "has no agents"),
            Triple(mapOf("agent_id" to "9a920eee-47fb-4571-9501-e4b3120c12f2"), IllegalArgumentException::class, "has no agents"),
        )

        tests.forEach {
            val ex = assertFailsWith(
                exceptionClass = it.second,
                block = { CoderGatewayConnectionProvider.getMatchingAgent(it.first, ws) })
            assertContains(ex.message.toString(), it.third)
        }
    }
}
