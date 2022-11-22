package com.coder.gateway.sdk.v2.models

import com.google.gson.annotations.SerializedName
import java.time.Instant
import java.util.UUID

data class WorkspaceAgent(
    @SerializedName("id") val id: UUID,
    @SerializedName("created_at") val createdAt: Instant,
    @SerializedName("updated_at") val updatedAt: Instant,
    @SerializedName("first_connected_at") val firstConnectedAt: Instant?,
    @SerializedName("last_connected_at") val lastConnectedAt: Instant?,
    @SerializedName("disconnected_at") val disconnectedAt: Instant?,
    @SerializedName("status") val status: WorkspaceAgentStatus,
    @SerializedName("name") val name: String,
    @SerializedName("resource_id") val resourceID: UUID,
    @SerializedName("instance_id") val instanceID: String?,
    @SerializedName("architecture") val architecture: String,
    @SerializedName("environment_variables") val envVariables: Map<String, String>,
    @SerializedName("operating_system") val operatingSystem: String,
    @SerializedName("startup_script") val startupScript: String?,
    @SerializedName("directory") val directory: String?,
    @SerializedName("version") val version: String,
    @SerializedName("apps") val apps: List<WorkspaceApp>,
    @SerializedName("latency") val derpLatency: Map<String, DERPRegion>?,
    @SerializedName("connection_timeout_seconds") val connectionTimeoutSeconds: Int,
    @SerializedName("troubleshooting_url") val troubleshootingURL: String
)

enum class WorkspaceAgentStatus {
    @SerializedName("connecting")
    CONNECTING,

    @SerializedName("connected")
    CONNECTED,

    @SerializedName("disconnected")
    DISCONNECTED,

    @SerializedName("timeout")
    TIMEOUT
}

data class DERPRegion(
    @SerializedName("preferred") val preferred: Boolean,
    @SerializedName("latency_ms") val latencyMillis: Double
)