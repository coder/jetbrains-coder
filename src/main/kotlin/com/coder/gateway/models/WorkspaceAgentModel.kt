package com.coder.gateway.models

import com.coder.gateway.sdk.Arch
import com.coder.gateway.sdk.OS

data class WorkspaceAgentModel(
    val name: String,
    val templateName: String,
    val status: WorkspaceVersionStatus,
    val agentStatus: WorkspaceAgentStatus,
    val agentOS: OS?,
    val agentArch: Arch?,
    val homeDirectory: String?
)
