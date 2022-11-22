package com.coder.gateway.sdk.v2.models

import com.google.gson.annotations.SerializedName
import java.time.Instant
import java.util.UUID

/**
 * Represents a deployment of a template. It references a specific version and can be updated.
 */
data class Workspace(
    @SerializedName("id") val id: UUID,
    @SerializedName("created_at") val createdAt: Instant,
    @SerializedName("updated_at") val updatedAt: Instant,
    @SerializedName("owner_id") val ownerID: UUID,
    @SerializedName("owner_name") val ownerName: String,
    @SerializedName("template_id") val templateID: UUID,
    @SerializedName("template_name") val templateName: String,
    @SerializedName("template_display_name") val templateDisplayName: String,
    @SerializedName("template_icon") val templateIcon: String,
    @SerializedName("template_allow_user_cancel_workspace_jobs") val templateAllowUserCancelWorkspaceJobs: Boolean,
    @SerializedName("latest_build") val latestBuild: WorkspaceBuild,
    @SerializedName("outdated") val outdated: Boolean,
    @SerializedName("name") val name: String,
    @SerializedName("autostart_schedule") val autostartSchedule: String?,
    @SerializedName("ttl_ms") val ttlMillis: Long?,
    @SerializedName("last_used_at") val lastUsedAt: Instant,
)