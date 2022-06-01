package com.coder.gateway.sdk.v2.models

import com.google.gson.annotations.SerializedName
import java.time.Instant
import java.util.UUID

data class ProvisionerJob(
    @SerializedName("id") val id: UUID,
    @SerializedName("created_at") val createdAt: Instant,
    @SerializedName("started_at") val startedAt: Instant,
    @SerializedName("completed_at") val completedAt: Instant,
    @SerializedName("error") val error: String,
    @SerializedName("status") val status: String,
    @SerializedName("worker_id") val workerID: UUID,
)
