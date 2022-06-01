package com.coder.gateway.sdk.v1

import com.google.gson.annotations.SerializedName
import java.time.Instant


data class User(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String,
    @SerializedName("username") val username: String,
    @SerializedName("name") val name: String,
    @SerializedName("roles") val roles: Set<String>,
    @SerializedName("temporary_password") val temporaryPassword: Boolean,
    @SerializedName("login_type") val loginType: Boolean,
    @SerializedName("key_regenerated_at") val keyRegeneratedAt: Boolean,
    @SerializedName("created_at") val createdAt: Instant,
    @SerializedName("updated_at") val updatedAt: Instant,
)
