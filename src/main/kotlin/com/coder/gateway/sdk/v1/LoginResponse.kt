package com.coder.gateway.sdk.v1

import com.google.gson.annotations.SerializedName

data class LoginResponse(@SerializedName("session_token") val sessionToken: String)

