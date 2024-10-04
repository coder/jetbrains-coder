package com.coder.gateway.models

import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceAgent
import javax.swing.Icon

// This represents a single row in the flattened agent list.  It is either an
// agent with its associated workspace or a workspace with no agents, in which
// case it acts as a placeholder for performing actions on the workspace but
// cannot be connected to.
data class WorkspaceAgentListModel(
    val workspace: Workspace,
    // If this is missing, assume the workspace is off or has no agents.
    val agent: WorkspaceAgent? = null,
    // The icon of the template from which this workspace was created.
    var icon: Icon? = null,
    // The combined status of the workspace and agent to display on the row.
    val status: WorkspaceAndAgentStatus = WorkspaceAndAgentStatus.from(workspace, agent),
    // The combined `workspace.agent` name to display on the row.  Users can have workspaces with the same name, so it
    // must not be used as a unique identifier.
    val name: String = if (agent != null) "${workspace.name}.${agent.name}" else workspace.name,
)
