package com.coder.gateway.models

import com.coder.gateway.sdk.v1.Workspace

data class CoderWorkspacesWizardModel(var loginModel: LoginModel, var workspaces: List<Workspace>)