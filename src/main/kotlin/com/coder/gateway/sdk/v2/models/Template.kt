package com.coder.gateway.sdk.v2.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.UUID

@JsonClass(generateAdapter = true)
data class Template(
    @Json(name = "id") val id: UUID,
    @Json(name = "active_version_id") val activeVersionID: UUID,
)
