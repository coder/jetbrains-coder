package com.coder.gateway.sdk.v2.models

import com.squareup.moshi.Json
import java.time.Instant
import java.util.UUID

data class ProvisionerJob(
    @Json(name = "id") val id: UUID,
    @Json(name = "created_at") val createdAt: Instant,
    @Json(name = "started_at") val startedAt: Instant?,
    @Json(name = "completed_at") val completedAt: Instant?,
    @Json(name = "canceled_at") val canceledAt: Instant?,
    @Json(name = "error") val error: String?,
    @Json(name = "status") val status: ProvisionerJobStatus,
    @Json(name = "worker_id") val workerID: UUID?,
    @Json(name = "file_id") val fileID: UUID,
    @Json(name = "tags") val tags: Map<String, String>,
)

enum class ProvisionerJobStatus {
    @Json(name = "canceled") CANCELED,
    @Json(name = "canceling") CANCELING,
    @Json(name = "failed") FAILED,
    @Json(name = "pending") PENDING,
    @Json(name = "running") RUNNING,
    @Json(name = "succeeded") SUCCEEDED
}
