package com.coder.gateway.sdk.v2.models

import com.coder.gateway.util.Arch
import com.coder.gateway.util.OS
import com.squareup.moshi.Json
import java.time.Instant
import java.util.*

data class WorkspaceAgent(
    @Json(name = "id") val id: UUID,
    @Json(name = "created_at") val createdAt: Instant,
    @Json(name = "updated_at") val updatedAt: Instant,
    @Json(name = "first_connected_at") val firstConnectedAt: Instant?,
    @Json(name = "last_connected_at") val lastConnectedAt: Instant?,
    @Json(name = "disconnected_at") val disconnectedAt: Instant?,
    @Json(name = "status") val status: WorkspaceAgentStatus,
    @Json(name = "name") val name: String,
    @Json(name = "resource_id") val resourceID: UUID,
    @Json(name = "instance_id") val instanceID: String?,
    @Json(name = "architecture") val architecture: Arch?,
    @Json(name = "environment_variables") val envVariables: Map<String, String>,
    @Json(name = "operating_system") val operatingSystem: OS?,
    @Json(name = "startup_script") val startupScript: String?,
    @Json(name = "directory") val directory: String?,
    @Json(name = "expanded_directory") val expandedDirectory: String?,
    @Json(name = "version") val version: String,
    @Json(name = "apps") val apps: List<WorkspaceApp>,
    @Json(name = "latency") val derpLatency: Map<String, DERPRegion>?,
    @Json(name = "connection_timeout_seconds") val connectionTimeoutSeconds: Int,
    @Json(name = "troubleshooting_url") val troubleshootingURL: String,
    @Json(name = "lifecycle_state") val lifecycleState: WorkspaceAgentLifecycleState,
    @Json(name = "login_before_ready") val loginBeforeReady: Boolean?,
)

enum class WorkspaceAgentStatus {
    @Json(name = "connecting") CONNECTING,
    @Json(name = "connected") CONNECTED,
    @Json(name = "disconnected") DISCONNECTED,
    @Json(name = "timeout") TIMEOUT
}

enum class WorkspaceAgentLifecycleState {
    @Json(name = "created") CREATED,
    @Json(name = "starting") STARTING,
    @Json(name = "start_timeout") START_TIMEOUT,
    @Json(name = "start_error") START_ERROR,
    @Json(name = "ready") READY,
    @Json(name = "shutting_down") SHUTTING_DOWN,
    @Json(name = "shutdown_timeout") SHUTDOWN_TIMEOUT,
    @Json(name = "shutdown_error") SHUTDOWN_ERROR,
    @Json(name = "off") OFF,
}

data class DERPRegion(
    @Json(name = "preferred") val preferred: Boolean,
    @Json(name = "latency_ms") val latencyMillis: Double,
)
