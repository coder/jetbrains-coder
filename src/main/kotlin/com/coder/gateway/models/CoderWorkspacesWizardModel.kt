package com.coder.gateway.models

data class CoderWorkspacesWizardModel(
    var coderURL: String = "https://localhost",
    var token: String = "",
    var buildVersion: String = "",
    var workspaceAgents: List<WorkspaceAgentModel> = mutableListOf(),
    var selectedWorkspace: WorkspaceAgentModel? = null
)