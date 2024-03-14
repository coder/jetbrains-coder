package com.coder.gateway.sdk.v2.models

import com.squareup.moshi.Json
import java.util.UUID

/**
 * WorkspaceBuild is an at-point representation of a workspace state.
 * BuildNumbers start at 1 and increase by 1 for each subsequent build.
 */
data class WorkspaceBuild(
    @Json(name = "template_version_id") val templateVersionID: UUID,
    @Json(name = "resources") val resources: List<WorkspaceResource>,
    @Json(name = "status") val status: WorkspaceStatus,
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
