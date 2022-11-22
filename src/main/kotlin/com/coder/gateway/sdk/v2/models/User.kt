package com.coder.gateway.sdk.v2.models

import com.google.gson.annotations.SerializedName
import java.time.Instant
import java.util.UUID

data class User(
    @SerializedName("id") val id: UUID,
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("created_at") val createdAt: Instant,
    @SerializedName("last_seen_at") val lastSeenAt: Instant,

    @SerializedName("status") val status: UserStatus,
    @SerializedName("organization_ids") val organizationIDs: List<UUID>,
    @SerializedName("roles") val roles: List<Role>?,
    @SerializedName("avatar_url") val avatarURL: String,
)

enum class UserStatus {
    @SerializedName("active")
    ACTIVE,

    @SerializedName("suspended")
    SUSPENDED
}