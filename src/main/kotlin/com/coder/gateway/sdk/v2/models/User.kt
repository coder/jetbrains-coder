package com.coder.gateway.sdk.v2.models

import com.squareup.moshi.Json

data class User(
    @Json(name = "username") val username: String,
)
