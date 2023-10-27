package com.coder.gateway

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class CoderGatewayConnectionProviderTest extends Specification {
    @Shared
    def agents = [
        agent_name_3: "b0e4c54d-9ba9-4413-8512-11ca1e826a24",
        agent_name_2: "fb3daea4-da6b-424d-84c7-36b90574cfef",
        agent_name: "9a920eee-47fb-4571-9501-e4b3120c12f2",
    ]
    def oneAgent = [
        agent_name_3: "b0e4c54d-9ba9-4413-8512-11ca1e826a24"
    ]

    def "gets matching agent"() {
        expect:
        def ws = DataGen.workspace("ws", agents)
        CoderGatewayConnectionProvider.getMatchingAgent(parameters, ws).agentID == UUID.fromString(expected)

        where:
        parameters                                         | expected
        [agent:    "agent_name"]                           | "9a920eee-47fb-4571-9501-e4b3120c12f2"
        [agent_id: "9a920eee-47fb-4571-9501-e4b3120c12f2"] | "9a920eee-47fb-4571-9501-e4b3120c12f2"
        [agent:    "agent_name_2"]                         | "fb3daea4-da6b-424d-84c7-36b90574cfef"
        [agent_id: "fb3daea4-da6b-424d-84c7-36b90574cfef"] | "fb3daea4-da6b-424d-84c7-36b90574cfef"
        [agent:    "agent_name_3"]                         | "b0e4c54d-9ba9-4413-8512-11ca1e826a24"
        [agent_id: "b0e4c54d-9ba9-4413-8512-11ca1e826a24"] | "b0e4c54d-9ba9-4413-8512-11ca1e826a24"

        // Prefer agent_id.
        [agent: "agent_name", agent_id: "b0e4c54d-9ba9-4413-8512-11ca1e826a24"] | "b0e4c54d-9ba9-4413-8512-11ca1e826a24"
    }

    def "fails to get matching agent"() {
        when:
        def ws = DataGen.workspace("ws", agents)
        CoderGatewayConnectionProvider.getMatchingAgent(parameters, ws)

        then:
        def err = thrown(expected)
        err.message.contains(message)

        where:
        parameters                                         | expected                 | message
        [:]                                                | MissingArgumentException | "Unable to determine"
        [agent: ""]                                        | MissingArgumentException | "Unable to determine"
        [agent_id: ""]                                     | MissingArgumentException | "Unable to determine"
        [agent: null]                                      | MissingArgumentException | "Unable to determine"
        [agent_id: null]                                   | MissingArgumentException | "Unable to determine"
        [agent: "ws"]                                      | IllegalArgumentException | "agent named"
        [agent: "ws.agent_name"]                           | IllegalArgumentException | "agent named"
        [agent: "agent_name_4"]                            | IllegalArgumentException | "agent named"
        [agent_id: "not-a-uuid"]                           | IllegalArgumentException | "agent with ID"
        [agent_id: "ceaa7bcf-1612-45d7-b484-2e0da9349168"] | IllegalArgumentException | "agent with ID"

        // Will ignore agent if agent_id is set even if agent matches.
        [agent: "agent_name", agent_id: "ceaa7bcf-1612-45d7-b484-2e0da9349168"] | IllegalArgumentException | "agent with ID"
    }

    def "gets the first agent when workspace has only one"() {
        expect:
        def ws = DataGen.workspace("ws", oneAgent)
        CoderGatewayConnectionProvider.getMatchingAgent(parameters, ws).agentID == UUID.fromString("b0e4c54d-9ba9-4413-8512-11ca1e826a24")

        where:
        parameters << [
            [:],
            [agent: ""],
            [agent_id: ""],
            [agent: null],
            [agent_id: null],
        ]
    }

    def "fails to get agent when workspace has only one"() {
        when:
        def ws = DataGen.workspace("ws", oneAgent)
        CoderGatewayConnectionProvider.getMatchingAgent(parameters, ws)

        then:
        def err = thrown(expected)
        err.message.contains(message)

        where:
        parameters                                         | expected                 | message
        [agent:    "ws"]                                   | IllegalArgumentException | "agent named"
        [agent:    "ws.agent_name_3"]                      | IllegalArgumentException | "agent named"
        [agent:    "agent_name_4"]                         | IllegalArgumentException | "agent named"
        [agent_id: "ceaa7bcf-1612-45d7-b484-2e0da9349168"] | IllegalArgumentException | "agent with ID"
    }

    def "fails to get agent from workspace without agents"() {
        when:
        def ws = DataGen.workspace("ws")
        CoderGatewayConnectionProvider.getMatchingAgent(parameters, ws)

        then:
        def err = thrown(expected)
        err.message.contains(message)

        where:
        parameters                                         | expected                 | message
        [:]                                                | IllegalArgumentException | "has no agents"
        [agent: ""]                                        | IllegalArgumentException | "has no agents"
        [agent_id: ""]                                     | IllegalArgumentException | "has no agents"
        [agent: null]                                      | IllegalArgumentException | "has no agents"
        [agent_id: null]                                   | IllegalArgumentException | "has no agents"
        [agent:    "agent_name"]                           | IllegalArgumentException | "has no agents"
        [agent_id: "9a920eee-47fb-4571-9501-e4b3120c12f2"] | IllegalArgumentException | "has no agents"
    }
}
