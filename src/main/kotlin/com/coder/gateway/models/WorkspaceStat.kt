package com.coder.gateway.models

import com.google.gson.annotations.SerializedName
import java.time.Instant

data class WorkspaceStat(
    @SerializedName("time") val time: Instant,
    @SerializedName("last_online") val last_online: Instant,
    @SerializedName("container_status") val container_status: String,
    @SerializedName("stat_error") val stat_error: String,
    @SerializedName("cpu_usage") val cpu_usage: Float,
    @SerializedName("memory_total") val memory_total: Long,
    @SerializedName("memory_usage") val memory_usage: Float,
    @SerializedName("disk_total") val disk_total: Long,
    @SerializedName("disk_used") val disk_used: Long,
)
