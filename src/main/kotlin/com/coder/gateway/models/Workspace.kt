package com.coder.gateway.models

import com.google.gson.annotations.SerializedName
import java.time.Instant

data class Workspace(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("image_id") val imageId: String,
    @SerializedName("image_tag") val imageTag: String,
    @SerializedName("organization_id") val organizationId: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("last_built_at") val lastBuiltAt: Instant,
    @SerializedName("cpu_cores") val cpuCores: Float,
    @SerializedName("memory_gb") val memoryGB: Float,
    @SerializedName("disk_gb") val disk_gb: Int,
    @SerializedName("gpus") val gpus: Int,
    @SerializedName("updating") val updating: Boolean,
    @SerializedName("latest_stat") val latestStat: WorkspaceStat,
    @SerializedName("rebuild_messages") val rebuildMessages: List<RebuildMessage>,
    @SerializedName("created_at") val createdAt: Instant,
    @SerializedName("updated_at") val updatedAt: Instant,
    @SerializedName("last_opened_at") val lastOpenedAt: Instant,
    @SerializedName("last_connection_at") val lastConnectionAt: Instant,
    @SerializedName("auto_off_threshold") val autoOffThreshold: Long,
    @SerializedName("use_container_vm") val useContainerVM: Boolean,
    @SerializedName("resource_pool_id") val resourcePoolId: String,

    )
