package com.coder.gateway.models

import com.coder.gateway.sdk.v2.models.Workspace

enum class WorkspaceVersionStatus(val label: String) {
    UPDATED("Up to date"), OUTDATED("Outdated");

    companion object {
        fun from(workspace: Workspace) = when (workspace.outdated) {
            true -> OUTDATED
            false -> UPDATED
        }
    }
}