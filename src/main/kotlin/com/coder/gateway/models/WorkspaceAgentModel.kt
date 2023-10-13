package com.coder.gateway.models

import com.coder.gateway.sdk.Arch
import com.coder.gateway.sdk.OS
import com.coder.gateway.sdk.v2.models.WorkspaceStatus
import com.coder.gateway.sdk.v2.models.WorkspaceTransition
import java.util.UUID
import javax.swing.Icon

// TODO: Refactor to have a list of workspaces that each have agents.  We
// present in the UI as a single flat list in the table (when there are no
// agents we display a row for the workspace) but still, a list of workspaces
// each with a list of agents might reflect reality more closely.  When we
// iterate over the list we can add the workspace row if it has no agents
// otherwise iterate over the agents and then flatten the result.
data class WorkspaceAgentModel(
    val agentID: UUID?,
    val workspaceID: UUID,
    val workspaceName: String,
    val name: String, // Name of the workspace OR the agent if this is for an agent.
    val templateID: UUID,
    val templateName: String,
    val templateIconPath: String,
    var templateIcon: Icon?,
    val status: WorkspaceVersionStatus,
    val workspaceStatus: WorkspaceStatus,
    val agentStatus: WorkspaceAndAgentStatus,
    val lastBuildTransition: WorkspaceTransition,
    val agentOS: OS?,
    val agentArch: Arch?,
    val homeDirectory: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WorkspaceAgentModel

        if (workspaceID != other.workspaceID) return false
        if (workspaceName != other.workspaceName) return false
        if (name != other.name) return false
        if (templateID != other.templateID) return false
        if (templateName != other.templateName) return false
        if (agentStatus != other.agentStatus) return false

        return true
    }

    override fun hashCode(): Int {
        var result = workspaceID.hashCode()
        result = 31 * result + workspaceName.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + templateID.hashCode()
        result = 31 * result + templateName.hashCode()
        result = 31 * result + agentStatus.hashCode()
        return result
    }
}
