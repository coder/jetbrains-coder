package com.coder.gateway.sdk.v2.models

import com.coder.gateway.util.Arch
import com.coder.gateway.util.OS
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.UUID

@JsonClass(generateAdapter = true)
data class WorkspaceAgent(
    @Json(name = "id") val id: UUID,
    @Json(name = "status") val status: WorkspaceAgentStatus,
    @Json(name = "name") val name: String,
    @Json(name = "architecture") val architecture: Arch?,
    @Json(name = "operating_system") val operatingSystem: OS?,
    @Json(name = "directory") val directory: String?,
    @Json(name = "expanded_directory") val expandedDirectory: String?,
    @Json(name = "lifecycle_state") val lifecycleState: WorkspaceAgentLifecycleState,
    @Json(name = "login_before_ready") val loginBeforeReady: Boolean?,
)

enum class WorkspaceAgentStatus {
    @Json(name = "connecting") CONNECTING,
    @Json(name = "connected") CONNECTED,
    @Json(name = "disconnected") DISCONNECTED,
    @Json(name = "timeout") TIMEOUT,
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
