package com.coder.gateway.sdk.v2.models

import com.google.gson.annotations.SerializedName
import java.time.Instant
import java.util.UUID

data class WorkspaceResource(
    @SerializedName("id") val id: UUID,
    @SerializedName("created_at") val createdAt: Instant,
    @SerializedName("job_id") val jobID: UUID,
    @SerializedName("workspace_transition") val workspaceTransition: WorkspaceTransition,
    @SerializedName("type") val type: String,
    @SerializedName("name") val name: String,
    @SerializedName("hide") val hide: Boolean,
    @SerializedName("icon") val icon: String,
    @SerializedName("agents") val agents: List<WorkspaceAgent>?,
    @SerializedName("metadata") val metadata: List<WorkspaceResourceMetadata>?,
    @SerializedName("daily_cost") val dailyCost: Int
)