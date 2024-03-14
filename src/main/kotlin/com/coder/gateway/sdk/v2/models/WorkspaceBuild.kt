package com.coder.gateway.sdk.v2.models

import com.squareup.moshi.Json
import java.time.Instant
import java.util.UUID

/**
 * WorkspaceBuild is an at-point representation of a workspace state.
 * BuildNumbers start at 1 and increase by 1 for each subsequent build.
 */
data class WorkspaceBuild(
    @Json(name = "id") val id: UUID,
    @Json(name = "created_at") val createdAt: Instant,
    @Json(name = "updated_at") val updatedAt: Instant,
    @Json(name = "workspace_id") val workspaceID: UUID,
    @Json(name = "workspace_name") val workspaceName: String,
    @Json(name = "workspace_owner_id") val workspaceOwnerID: UUID,
    @Json(name = "workspace_owner_name") val workspaceOwnerName: String,
    @Json(name = "template_version_id") val templateVersionID: UUID,
    @Json(name = "build_number") val buildNumber: Int,
    @Json(name = "transition") val transition: WorkspaceTransition,
    @Json(name = "initiator_id") val initiatorID: UUID,
    @Json(name = "initiator_name") val initiatorUsername: String,
    @Json(name = "job") val job: ProvisionerJob,
    @Json(name = "reason") val reason: BuildReason,
    @Json(name = "resources") val resources: List<WorkspaceResource>,
    @Json(name = "deadline") val deadline: Instant?,
    @Json(name = "status") val status: WorkspaceStatus,
    @Json(name = "daily_cost") val dailyCost: Int,
)

enum class WorkspaceStatus {
    @Json(name = "pending") PENDING,
    @Json(name = "starting") STARTING,
    @Json(name = "running") RUNNING,
    @Json(name = "stopping") STOPPING,
    @Json(name = "stopped") STOPPED,
    @Json(name = "failed") FAILED,
    @Json(name = "canceling") CANCELING,
    @Json(name = "canceled") CANCELED,
    @Json(name = "deleting") DELETING,
    @Json(name = "deleted") DELETED
}
