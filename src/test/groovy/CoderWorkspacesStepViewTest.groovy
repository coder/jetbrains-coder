import com.coder.gateway.views.steps.WorkspacesTable
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class CoderWorkspacesStepViewTest extends Specification {
    def "gets new selection"() {
        given:
        def table = new WorkspacesTable()
        table.listTableModel.items = List.of(
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

        expect:
        table.getNewSelection(selected) == expected

        where:
        selected                                     | expected
        null                                         | -1 // No selection.
        DataGen.workspaceAgentModel("gone", "gone")  | -1 // No workspace that matches.
        DataGen.workspaceAgentModel("ws1")           | 0  // Workspace exact match.
        DataGen.workspaceAgentModel("gone", "ws1")   | 0  // Agent gone, select workspace.
        DataGen.workspaceAgentModel("ws2")           | 1  // Workspace gone, select first agent.
        DataGen.workspaceAgentModel("agent1", "ws2") | 1  // Agent exact match.
        DataGen.workspaceAgentModel("agent2", "ws2") | 2  // Agent exact match.
        DataGen.workspaceAgentModel("ws3")           | 3  // Workspace gone, select first agent.
        DataGen.workspaceAgentModel("agent3", "ws3") | 3  // Agent exact match.
        DataGen.workspaceAgentModel("gone", "ws4")   | 4  // Agent gone, select workspace.
        DataGen.workspaceAgentModel("ws4")           | 4  // Workspace exact match.
        DataGen.workspaceAgentModel("agent2", "ws5") | 5  // Agent exact match.
        DataGen.workspaceAgentModel("gone", "ws5")   | 5  // Agent gone, another agent comes first.
        DataGen.workspaceAgentModel("ws5")           | 6  // Workspace exact match.
        DataGen.workspaceAgentModel("ws6")           | 7  // Workspace exact match.
        DataGen.workspaceAgentModel("gone", "ws6")   | 7  // Agent gone, workspace comes first.
        DataGen.workspaceAgentModel("agent3", "ws6") | 8  // Agent exact match.
    }
}
