package com.coder.gateway.sdk

import com.google.gson.annotations.SerializedName

data class LoginResponse(@SerializedName("session_token") val sessionToken: String)

