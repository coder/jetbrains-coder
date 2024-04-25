package com.coder.gateway.views.steps

import com.coder.gateway.sdk.DataGen
import kotlin.test.Test
import kotlin.test.assertEquals

internal class CoderWorkspacesStepViewTest {
    @Test
    fun getsNewSelection() {
        val table = WorkspacesTable()
        table.listTableModel.items =
            listOf(
                // An off workspace.
                DataGen.agentList("ws1"),
                // On workspaces.
                DataGen.agentList("ws2", "agent1"),
                DataGen.agentList("ws2", "agent2"),
                DataGen.agentList("ws3", "agent3"),
                // Another off workspace.
                DataGen.agentList("ws4"),
                // In practice we do not list both agents and workspaces
                // together but here test that anyway with an agent first and
                // then with a workspace first.
                DataGen.agentList("ws5", "agent2"),
                DataGen.agentList("ws5"),
                DataGen.agentList("ws6"),
                DataGen.agentList("ws6", "agent3"),
            ).flatten()

        val tests =
            listOf(
                Pair(null, -1), // No selection.
                Pair(DataGen.agentList("gone", "gone"), -1), // No workspace that matches.
                Pair(DataGen.agentList("ws1"), 0),           // Workspace exact match.
                Pair(DataGen.agentList("ws1", "gone"), 0),   // Agent gone, select workspace.
                Pair(DataGen.agentList("ws2"), 1),           // Workspace gone, select first agent.
                Pair(DataGen.agentList("ws2", "agent1"), 1), // Agent exact match.
                Pair(DataGen.agentList("ws2", "agent2"), 2), // Agent exact match.
                Pair(DataGen.agentList("ws3"), 3),           // Workspace gone, select first agent.
                Pair(DataGen.agentList("ws3", "agent3"), 3), // Agent exact match.
                Pair(DataGen.agentList("ws4", "gone"), 4),   // Agent gone, select workspace.
                Pair(DataGen.agentList("ws4"), 4),           // Workspace exact match.
                Pair(DataGen.agentList("ws5", "agent2"), 5), // Agent exact match.
                Pair(DataGen.agentList("ws5", "gone"), 5),   // Agent gone, another agent comes first.
                Pair(DataGen.agentList("ws5"), 6),           // Workspace exact match.
                Pair(DataGen.agentList("ws6"), 7),           // Workspace exact match.
                Pair(DataGen.agentList("ws6", "gone"), 7),   // Agent gone, workspace comes first.
                Pair(DataGen.agentList("ws6", "agent3"), 8), // Agent exact match.
            )

        tests.forEach {
            assertEquals(it.second, table.getNewSelection(it.first?.first()))
        }
    }
}
