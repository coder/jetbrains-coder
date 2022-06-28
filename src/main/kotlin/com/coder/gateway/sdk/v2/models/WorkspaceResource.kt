package com.coder.gateway.sdk.v2.models

import com.google.gson.annotations.SerializedName
import java.time.Instant
import java.util.UUID

data class WorkspaceResource(
    @SerializedName("id") val id: UUID,
    @SerializedName("created_at") val createdAt: Instant,
    @SerializedName("job_id") val jobID: UUID,
    @SerializedName("workspace_transition") val workspaceTransition: String,
    @SerializedName("type") val type: String,
    @SerializedName("name") val name: String,
    @SerializedName("agents") val agents: List<WorkspaceAgent>?
)
