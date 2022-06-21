package com.coder.gateway.sdk.v2.models

import com.google.gson.annotations.SerializedName
import java.time.Instant
import java.util.UUID

/**
 * WorkspaceBuild is an at-point representation of a workspace state.
 * BuildNumbers start at 1 and increase by 1 for each subsequent build.
 */
data class WorkspaceBuild(
    @SerializedName("id") val id: UUID,
    @SerializedName("created_at") val createdAt: Instant,
    @SerializedName("updated_at") val updatedAt: Instant,
    @SerializedName("workspace_id") val workspaceID: UUID,
    @SerializedName("template_version_id") val templateVersionID: UUID,
    @SerializedName("build_number") val buildNumber: Int,
    @SerializedName("name") val name: String,
    @SerializedName("transition") val workspaceTransition: WorkspaceBuildTransition,
    @SerializedName("owner_id") val ownerID: UUID,
    @SerializedName("initiator_id") val initiatorID: UUID,
    @SerializedName("job") val job: ProvisionerJob,
    @SerializedName("deadline") val deadline: Instant,
)

enum class WorkspaceBuildTransition {
    @SerializedName("start")
    START,

    @SerializedName("stop")
    STOP,

    @SerializedName("delete")
    DELETE
}