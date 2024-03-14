package com.coder.gateway.sdk.v2.models

import com.squareup.moshi.Json
import java.time.Instant
import java.util.UUID

data class User(
    @Json(name = "id") val id: UUID,
    @Json(name = "username") val username: String,
    @Json(name = "email") val email: String,
    @Json(name = "created_at") val createdAt: Instant,
    @Json(name = "last_seen_at") val lastSeenAt: Instant,

    @Json(name = "status") val status: UserStatus,
    @Json(name = "organization_ids") val organizationIDs: List<UUID>,
    @Json(name = "roles") val roles: List<Role>?,
    @Json(name = "avatar_url") val avatarURL: String,
)

enum class UserStatus {
    @Json(name = "active") ACTIVE,
    @Json(name = "suspended") SUSPENDED
}
