package com.coder.gateway.models

import com.coder.gateway.cli.CoderCLIManager
import com.coder.gateway.sdk.CoderRestClient
import com.coder.gateway.sdk.v2.models.Workspace

enum class TokenSource {
    CONFIG,    // Pulled from the Coder CLI config.
    USER,      // Input by the user.
    QUERY,     // From the Gateway link as a query parameter.
    LAST_USED, // Last used token, either from storage or current run.
}

/**
 * Used to pass data between steps.
 */
data class CoderWorkspacesWizardModel(
    var coderURL: String = "https://coder.example.com",
    var token: Pair<String, TokenSource>? = null,
    var selectedListItem: WorkspaceAgentListModel? = null,
    var useExistingToken: Boolean = false,
    var configDirectory: String = "",
    var client: CoderRestClient? = null,
    var cliManager: CoderCLIManager? = null,
    var workspaces: List<Workspace>? = null
)
