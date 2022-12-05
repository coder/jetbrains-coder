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
    val templateIcon: Icon,
    val status: WorkspaceVersionStatus,
    val agentStatus: WorkspaceAgentStatus,
    val lastBuildTransition: WorkspaceTransition,
    val agentOS: OS?,
    val agentArch: Arch?,
    val homeDirectory: String?
)
