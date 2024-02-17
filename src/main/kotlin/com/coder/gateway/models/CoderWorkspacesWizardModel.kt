package com.coder.gateway.models

enum class TokenSource {
    CONFIG,    // Pulled from the Coder CLI config.
    USER,      // Input by the user.
    QUERY,     // From the Gateway link as a query parameter.
    LAST_USED, // Last used token, either from storage or current run.
}

data class CoderWorkspacesWizardModel(
    var coderURL: String = "https://coder.example.com",
    var token: Pair<String, TokenSource>? = null,
    var selectedListItem: WorkspaceAgentListModel? = null,
    var useExistingToken: Boolean = false,
    var configDirectory: String = "",
)
