package com.coder.gateway.sdk.v2.models

import com.google.gson.annotations.SerializedName
import java.time.Instant
import java.util.UUID

data class User(
    @SerializedName("id") val id: UUID,
    @SerializedName("email") val email: String,
    @SerializedName("username") val username: String,
    @SerializedName("created_at") val createdAt: Instant,

    @SerializedName("status") val status: String?,
    @SerializedName("organization_ids") val organizationIDs: List<UUID>?,
    @SerializedName("roles") val roles: List<Role>?,
)
