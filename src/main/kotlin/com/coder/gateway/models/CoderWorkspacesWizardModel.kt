package com.coder.gateway.models

import com.coder.gateway.sdk.v1.Workspace

data class CoderWorkspacesWizardModel(var coderURL: String = "https://localhost", var token: String = "", var workspaces: List<Workspace> = mutableListOf())