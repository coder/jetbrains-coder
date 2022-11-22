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
    @SerializedName("workspace_name") val workspaceName: String,
    @SerializedName("workspace_owner_id") val workspaceOwnerID: UUID,
    @SerializedName("workspace_owner_name") val workspaceOwnerName: String,
    @SerializedName("template_version_id") val templateVersionID: UUID,
    @SerializedName("build_number") val buildNumber: Int,
    @SerializedName("transition") val transition: WorkspaceTransition,
    @SerializedName("initiator_id") val initiatorID: UUID,
    @SerializedName("initiator_name") val initiatorUsername: String,
    @SerializedName("job") val job: ProvisionerJob,
    @SerializedName("reason") val reason: BuildReason,
    @SerializedName("resources") val resources: List<WorkspaceResource>,
    @SerializedName("deadline") val deadline: Instant?,
    @SerializedName("status") val status: WorkspaceStatus,
    @SerializedName("daily_cost") val dailyCost: Int,
)

enum class WorkspaceStatus {
    @SerializedName("pending")
    PENDING,

    @SerializedName("starting")
    STARTING,

    @SerializedName("running")
    RUNNING,

    @SerializedName("stopping")
    STOPPING,

    @SerializedName("stopped")
    STOPPED,

    @SerializedName("failed")
    FAILED,

    @SerializedName("canceling")
    CANCELING,

    @SerializedName("canceled")
    CANCELED,

    @SerializedName("deleting")
    DELETING,

    @SerializedName("deleted")
    DELETED
}