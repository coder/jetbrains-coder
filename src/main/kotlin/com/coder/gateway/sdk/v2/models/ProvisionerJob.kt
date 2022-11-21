package com.coder.gateway.sdk.v2.models

import com.google.gson.annotations.SerializedName
import java.time.Instant
import java.util.UUID

data class ProvisionerJob(
    @SerializedName("id") val id: UUID,
    @SerializedName("created_at") val createdAt: Instant,
    @SerializedName("started_at") val startedAt: Instant,
    @SerializedName("completed_at") val completedAt: Instant,
    @SerializedName("canceled_at") val canceledAt: Instant,
    @SerializedName("error") val error: String,
    @SerializedName("status") val status: ProvisionerJobStatus,
    @SerializedName("worker_id") val workerID: UUID,
    @SerializedName("file_id") val fileID: UUID,
    @SerializedName("tags") val tags: Map<String, String>,
)

enum class ProvisionerJobStatus {
    @SerializedName("canceled")
    CANCELED,

    @SerializedName("canceling")
    CANCELING,

    @SerializedName("failed")
    FAILED,

    @SerializedName("pending")
    PENDING,

    @SerializedName("running")
    RUNNING,

    @SerializedName("succeeded")
    SUCCEEDED
}