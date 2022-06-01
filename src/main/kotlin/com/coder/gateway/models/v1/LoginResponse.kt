package com.coder.gateway.models.v1

import com.google.gson.annotations.SerializedName

data class LoginResponse(@SerializedName("session_token") val sessionToken: String)

