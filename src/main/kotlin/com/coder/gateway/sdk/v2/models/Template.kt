package com.coder.gateway.sdk.v2.models

import com.google.gson.annotations.SerializedName
import java.time.Instant
import java.util.UUID

data class Template(
    @SerializedName("id") val id: UUID,
    @SerializedName("created_at") val createdAt: Instant,
    @SerializedName("updated_at") val updatedAt: Instant,
    @SerializedName("organization_id") val organizationIterator: UUID,
    @SerializedName("name") val name: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("provisioner") val provisioner: ProvisionerType,
    @SerializedName("active_version_id") val activeVersionID: UUID,
    @SerializedName("workspace_owner_count") val workspaceOwnerCount: Int,
    @SerializedName("active_user_count") val activeUserCount: Int,
    @SerializedName("build_time_stats") val buildTimeStats: Map<WorkspaceTransition, TransitionStats>,
    @SerializedName("description") val description: String,
    @SerializedName("icon") val icon: String,
    @SerializedName("default_ttl_ms") val defaultTTLMillis: Long,
    @SerializedName("created_by_id") val createdByID: UUID,
    @SerializedName("created_by_name") val createdByName: String,
    @SerializedName("allow_user_cancel_workspace_jobs") val allowUserCancelWorkspaceJobs: Boolean,
)

enum class ProvisionerType {
    @SerializedName("echo")
    ECHO,

    @SerializedName("terraform")
    TERRAFORM
}

data class TransitionStats(val p50: Long, val p95: Long)