package com.coder.gateway.sdk.v2.models

import com.squareup.moshi.Json
import java.time.Instant
import java.util.UUID

data class Template(
    @Json(name = "id") val id: UUID,
    @Json(name = "created_at") val createdAt: Instant,
    @Json(name = "updated_at") val updatedAt: Instant,
    @Json(name = "organization_id") val organizationIterator: UUID,
    @Json(name = "name") val name: String,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "provisioner") val provisioner: ProvisionerType,
    @Json(name = "active_version_id") val activeVersionID: UUID,
    @Json(name = "workspace_owner_count") val workspaceOwnerCount: Int,
    @Json(name = "active_user_count") val activeUserCount: Int,
    @Json(name = "build_time_stats") val buildTimeStats: Map<WorkspaceTransition, TransitionStats>,
    @Json(name = "description") val description: String,
    @Json(name = "icon") val icon: String,
    @Json(name = "default_ttl_ms") val defaultTTLMillis: Long,
    @Json(name = "created_by_id") val createdByID: UUID,
    @Json(name = "created_by_name") val createdByName: String,
    @Json(name = "allow_user_cancel_workspace_jobs") val allowUserCancelWorkspaceJobs: Boolean,
)

enum class ProvisionerType {
    @Json(name = "echo") ECHO,
    @Json(name = "terraform") TERRAFORM
}

data class TransitionStats(val p50: Long, val p95: Long)
