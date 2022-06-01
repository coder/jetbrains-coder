package com.coder.gateway.sdk.v2.models

import com.google.gson.annotations.SerializedName

/**
 * contains a session token for the newly authenticated user.
 */
data class LoginWithPasswordResponse(@SerializedName("session_token") val sessionToken: String)