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
                DataGen.workspace("ws1", "ws1"),

                // On workspaces.
                DataGen.workspace("agent1", "ws2"),
                DataGen.workspace("agent2", "ws2"),
                DataGen.workspace("agent3", "ws3"),

                // Another off workspace.
                DataGen.workspace("ws4", "ws4"),

                // In practice we do not list both agents and workspaces
                // together but here test that anyway with an agent first and
                // then with a workspace first.
                DataGen.workspace("agent2", "ws5"),
                DataGen.workspace("ws5", "ws5"),
                DataGen.workspace("ws6", "ws6"),
                DataGen.workspace("agent3", "ws6"),
        )

        expect:
        table.getNewSelection(selected) == expected

        where:
        selected                           | expected
        null                               | -1 // No selection.
        DataGen.workspace("gone", "gone")  | -1 // No workspace that matches.
        DataGen.workspace("ws1", "ws1")    | 0  // Workspace exact match.
        DataGen.workspace("gone", "ws1")   | 0  // Agent gone, select workspace.
        DataGen.workspace("ws2", "ws2")    | 1  // Workspace gone, select first agent.
        DataGen.workspace("agent1", "ws2") | 1  // Agent exact match.
        DataGen.workspace("agent2", "ws2") | 2  // Agent exact match.
        DataGen.workspace("ws3", "ws3")    | 3  // Workspace gone, select first agent.
        DataGen.workspace("agent3", "ws3") | 3  // Agent exact match.
        DataGen.workspace("gone", "ws4")   | 4  // Agent gone, select workspace.
        DataGen.workspace("ws4", "ws4")    | 4  // Workspace exact match.
        DataGen.workspace("agent2", "ws5") | 5  // Agent exact match.
        DataGen.workspace("gone", "ws5")   | 5  // Agent gone, another agent comes first.
        DataGen.workspace("ws5", "ws5")    | 6  // Workspace exact match.
        DataGen.workspace("ws6", "ws6")    | 7  // Workspace exact match.
        DataGen.workspace("gone", "ws6")   | 7  // Agent gone, workspace comes first.
        DataGen.workspace("agent3", "ws6") | 8  // Agent exact match.
    }

    def "gets cli manager"() {

    }
}
