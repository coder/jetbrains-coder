package com.coder.gateway.models

import com.coder.gateway.sdk.Arch
import com.coder.gateway.sdk.OS
import com.coder.gateway.sdk.v2.models.ProvisionerJobStatus
import com.coder.gateway.sdk.v2.models.WorkspaceBuildTransition

data class WorkspaceAgentModel(
    val name: String,
    val templateName: String,

    val jobStatus: ProvisionerJobStatus,
    val buildTransition: WorkspaceBuildTransition,

    val agentOS: OS?,
    val agentArch: Arch?,
    val homeDirectory: String?
)
