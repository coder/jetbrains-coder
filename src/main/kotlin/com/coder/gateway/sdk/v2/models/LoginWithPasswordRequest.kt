package com.coder.gateway.sdk.v2.models

import com.google.gson.annotations.SerializedName

/**
 * Enables callers to authenticate with email and password.
 */
data class LoginWithPasswordRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)


