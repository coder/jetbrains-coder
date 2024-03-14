package com.coder.gateway.sdk.v2.models

import com.coder.gateway.models.WorkspaceAgentListModel
import com.squareup.moshi.Json
import java.util.*

/**
 * Represents a deployment of a template. It references a specific version and can be updated.
 */
data class Workspace(
    @Json(name = "id") val id: UUID,
    @Json(name = "template_id") val templateID: UUID,
    @Json(name = "template_name") val templateName: String,
    @Json(name = "template_display_name") val templateDisplayName: String,
    @Json(name = "template_icon") val templateIcon: String,
    @Json(name = "latest_build") val latestBuild: WorkspaceBuild,
    @Json(name = "outdated") val outdated: Boolean,
    @Json(name = "name") val name: String,
)

/**
 * Return a list of agents combined with this workspace to display in the list.
 * If the workspace has no agents, return just itself with a null agent.
 */
fun Workspace.toAgentList(resources: List<WorkspaceResource> = this.latestBuild.resources): List<WorkspaceAgentListModel> {
    return resources.filter { it.agents != null }.flatMap { it.agents!! }.map { agent ->
        WorkspaceAgentListModel(this, agent)
    }.ifEmpty {
        listOf(WorkspaceAgentListModel(this))
    }
}
