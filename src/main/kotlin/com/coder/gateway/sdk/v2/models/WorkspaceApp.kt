package com.coder.gateway.sdk.v2.models

import com.squareup.moshi.Json
import java.util.UUID

data class WorkspaceApp(
    @Json(name = "id") val id: UUID,
    // unique identifier within the agent
    @Json(name = "slug") val slug: String,
    // friendly name for the app
    @Json(name = "display_name") val displayName: String,
    @Json(name = "command") val command: String?,
    // relative path or external URL
    @Json(name = "icon") val icon: String?,
    @Json(name = "subdomain") val subdomain: Boolean,
    @Json(name = "sharing_level") val sharingLevel: WorkspaceAppSharingLevel,
    @Json(name = "healthcheck") val healthCheck: HealthCheck,
    @Json(name = "health") val health: WorkspaceAppHealth,
)

enum class WorkspaceAppSharingLevel {
    @Json(name = "owner") OWNER,
    @Json(name = "authenticated") AUTHENTICATED,
    @Json(name = "public") PUBLIC
}

data class HealthCheck(
    @Json(name = "url") val url: String,
    // Interval specifies the seconds between each health check.
    @Json(name = "interval") val interval: Int,
    // Threshold specifies the number of consecutive failed health checks before returning "unhealthy".
    @Json(name = "Threshold") val threshold: Int
)

enum class WorkspaceAppHealth {
    @Json(name = "disabled") DISABLED,
    @Json(name = "initializing") INITIALIZING,
    @Json(name = "healthy") HEALTHY,
    @Json(name = "unhealthy") UNHEALTHY
}
