package com.coder.gateway.views.steps

import kotlin.test.Test
import kotlin.test.assertEquals

import com.coder.gateway.sdk.DataGen

internal class CoderWorkspacesStepViewTest {
    @Test
    fun getsNewSelection() {
        val table = WorkspacesTable()
        table.listTableModel.items = listOf(
            // An off workspace.
            DataGen.workspaceAgentModel("ws1"),

            // On workspaces.
            DataGen.workspaceAgentModel("agent1", "ws2"),
            DataGen.workspaceAgentModel("agent2", "ws2"),
            DataGen.workspaceAgentModel("agent3", "ws3"),

            // Another off workspace.
            DataGen.workspaceAgentModel("ws4"),

            // In practice we do not list both agents and workspaces
            // together but here test that anyway with an agent first and
            // then with a workspace first.
            DataGen.workspaceAgentModel("agent2", "ws5"),
            DataGen.workspaceAgentModel("ws5"),
            DataGen.workspaceAgentModel("ws6"),
            DataGen.workspaceAgentModel("agent3", "ws6"),
        )

        val tests = listOf(
            Pair(null,                                        -1), // No selection.
            Pair(DataGen.workspaceAgentModel("gone", "gone"), -1), // No workspace that matches.
            Pair(DataGen.workspaceAgentModel("ws1"),           0), // Workspace exact match.
            Pair(DataGen.workspaceAgentModel("gone", "ws1"),   0), // Agent gone, select workspace.
            Pair(DataGen.workspaceAgentModel("ws2"),           1), // Workspace gone, select first agent.
            Pair(DataGen.workspaceAgentModel("agent1", "ws2"), 1), // Agent exact match.
            Pair(DataGen.workspaceAgentModel("agent2", "ws2"), 2), // Agent exact match.
            Pair(DataGen.workspaceAgentModel("ws3"),           3), // Workspace gone, select first agent.
            Pair(DataGen.workspaceAgentModel("agent3", "ws3"), 3), // Agent exact match.
            Pair(DataGen.workspaceAgentModel("gone", "ws4"),   4), // Agent gone, select workspace.
            Pair(DataGen.workspaceAgentModel("ws4"),           4), // Workspace exact match.
            Pair(DataGen.workspaceAgentModel("agent2", "ws5"), 5), // Agent exact match.
            Pair(DataGen.workspaceAgentModel("gone", "ws5"),   5), // Agent gone, another agent comes first.
            Pair(DataGen.workspaceAgentModel("ws5"),           6), // Workspace exact match.
            Pair(DataGen.workspaceAgentModel("ws6"),           7), // Workspace exact match.
            Pair(DataGen.workspaceAgentModel("gone", "ws6"),   7), // Agent gone, workspace comes first.
            Pair(DataGen.workspaceAgentModel("agent3", "ws6"), 8), // Agent exact match.
        )

        tests.forEach {
            assertEquals(it.second, table.getNewSelection(it.first))
        }
    }
}
