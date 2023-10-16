import com.coder.gateway.models.WorkspaceAgentModel
import com.coder.gateway.models.WorkspaceAndAgentStatus
import com.coder.gateway.models.WorkspaceVersionStatus
import com.coder.gateway.sdk.v2.models.WorkspaceStatus
import com.coder.gateway.sdk.v2.models.WorkspaceTransition

class DataGen {
    // Create a random workspace agent model.  If the workspace name is omitted
    // then return a model without any agent bits, similar to what
    // toAgentModels() does if the workspace does not specify any agents.
    // TODO: Maybe better to randomly generate the workspace and then call
    //       toAgentModels() on it.  Also the way an "agent" model can have no
    //       agent in it seems weird; can we refactor to remove
    //       WorkspaceAgentModel and use the original structs from the API?
    static WorkspaceAgentModel workspaceAgentModel(String name, String workspaceName = "", UUID agentId = UUID.randomUUID()) {
        return new WorkspaceAgentModel(
                workspaceName == "" ? null : agentId,
                UUID.randomUUID(),
                workspaceName == "" ? name : workspaceName,
                workspaceName == "" ? name : (workspaceName + "." + name),
                UUID.randomUUID(),
                "template-name",
                "template-icon-path",
                null,
                WorkspaceVersionStatus.UPDATED,
                WorkspaceStatus.RUNNING,
                WorkspaceAndAgentStatus.READY,
                WorkspaceTransition.START,
                null,
                null,
                null
        )
    }
}
