package com.coder.gateway.sdk.v2.models

import com.squareup.moshi.Json
import java.time.Instant
import java.util.UUID

data class WorkspaceResource(
    @Json(name = "id") val id: UUID,
    @Json(name = "created_at") val createdAt: Instant,
    @Json(name = "job_id") val jobID: UUID,
    @Json(name = "workspace_transition") val workspaceTransition: WorkspaceTransition,
    @Json(name = "type") val type: String,
    @Json(name = "name") val name: String,
    @Json(name = "hide") val hide: Boolean,
    @Json(name = "icon") val icon: String,
    @Json(name = "agents") val agents: List<WorkspaceAgent>?,
    @Json(name = "metadata") val metadata: List<WorkspaceResourceMetadata>?,
    @Json(name = "daily_cost") val dailyCost: Int
)
