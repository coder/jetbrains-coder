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
    @SerializedName("provisioner") val provisioner: String,
    @SerializedName("active_version_id") val activeVersionID: UUID,
    @SerializedName("workspace_owner_count") val workspaceOwnerCount: Int,
    @SerializedName("description") val description: String,
    @SerializedName("max_ttl_ms") val maxTTLMillis: Long,
    @SerializedName("min_autostart_interval_ms") val minAutostartIntervalMillis: Long,
    @SerializedName("created_by_id") val createdByID: UUID,
    @SerializedName("created_by_name") val createdByName: String,
)
