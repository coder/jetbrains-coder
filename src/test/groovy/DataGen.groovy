import com.coder.gateway.models.WorkspaceAgentModel
import com.coder.gateway.models.WorkspaceAndAgentStatus
import com.coder.gateway.models.WorkspaceVersionStatus
import com.coder.gateway.sdk.v2.models.WorkspaceStatus
import com.coder.gateway.sdk.v2.models.WorkspaceTransition

class DataGen {
    static WorkspaceAgentModel workspace(String name, String workspaceName = name) {
        return new WorkspaceAgentModel(
                UUID.randomUUID(),
                workspaceName,
                name,
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
