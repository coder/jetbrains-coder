package com.coder.gateway.sdk.v2.models

import com.google.gson.annotations.SerializedName
import java.util.UUID

data class WorkspaceApp(
    @SerializedName("id") val id: UUID,
    // unique identifier within the agent
    @SerializedName("slug") val slug: String,
    // friendly name for the app
    @SerializedName("display_name") val displayName: String,
    @SerializedName("command") val command: String?,
    // relative path or external URL
    @SerializedName("icon") val icon: String?,
    @SerializedName("subdomain") val subdomain: Boolean,
    @SerializedName("sharing_level") val sharingLevel: WorkspaceAppSharingLevel,
    @SerializedName("healthcheck") val healthCheck: HealthCheck,
    @SerializedName("health") val health: WorkspaceAppHealth,
)

enum class WorkspaceAppSharingLevel {
    @SerializedName("owner")
    OWNER,

    @SerializedName("authenticated")
    AUTHENTICATED,

    @SerializedName("public")
    PUBLIC
}

data class HealthCheck(
    @SerializedName("url") val url: String,
    // Interval specifies the seconds between each health check.
    @SerializedName("interval") val interval: Int,
    // Threshold specifies the number of consecutive failed health checks before returning "unhealthy".
    @SerializedName("Threshold") val threshold: Int
)

enum class WorkspaceAppHealth {
    @SerializedName("disabled")
    DISABLED,

    @SerializedName("initializing")
    INITIALIZING,

    @SerializedName("healthy")
    HEALTHY,

    @SerializedName("unhealthy")
    UNHEALTHY
}