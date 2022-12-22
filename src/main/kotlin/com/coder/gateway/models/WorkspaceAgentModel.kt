package com.coder.gateway.models

import com.coder.gateway.sdk.Arch
import com.coder.gateway.sdk.OS
import com.coder.gateway.sdk.v2.models.WorkspaceTransition
import java.util.UUID
import javax.swing.Icon

data class WorkspaceAgentModel(
    val workspaceID: UUID,
    val workspaceName: String,
    val name: String,
    val templateID: UUID,
    val templateName: String,
    val templateIconPath: String,
    var templateIcon: Icon?,
    val status: WorkspaceVersionStatus,
    val agentStatus: WorkspaceAgentStatus,
    val lastBuildTransition: WorkspaceTransition,
    val agentOS: OS?,
    val agentArch: Arch?,
    val homeDirectory: String?
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

        return true
    }

    override fun hashCode(): Int {
        var result = workspaceID.hashCode()
        result = 31 * result + workspaceName.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + templateID.hashCode()
        result = 31 * result + templateName.hashCode()
        return result
    }
}
