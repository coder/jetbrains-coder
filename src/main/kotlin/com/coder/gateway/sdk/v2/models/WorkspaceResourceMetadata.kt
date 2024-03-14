package com.coder.gateway.sdk.v2.models

import com.squareup.moshi.Json

data class WorkspaceResourceMetadata(
    @Json(name = "key") val key: String,
    @Json(name = "value") val value: String,
    @Json(name = "sensitive") val sensitive: Boolean
)
