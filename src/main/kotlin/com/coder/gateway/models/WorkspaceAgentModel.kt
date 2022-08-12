package com.coder.gateway.models

import com.coder.gateway.sdk.Arch
import com.coder.gateway.sdk.OS
import java.util.UUID

data class WorkspaceAgentModel(
    val workspaceID: UUID,
    val workspaceName: String,
    val name: String,
    val templateName: String,
    val status: WorkspaceVersionStatus,
    val agentStatus: WorkspaceAgentStatus,
    val agentOS: OS?,
    val agentArch: Arch?,
    val homeDirectory: String?
)
