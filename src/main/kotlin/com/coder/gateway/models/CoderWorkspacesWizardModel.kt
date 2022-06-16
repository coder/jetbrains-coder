package com.coder.gateway.models

import com.coder.gateway.sdk.v2.models.Workspace

data class CoderWorkspacesWizardModel(
    var coderURL: String = "https://localhost",
    var token: String = "",
    var buildVersion: String = "",
    var workspaces: List<Workspace> = mutableListOf(),
    var selectedWorkspace: Workspace? = null
)