package com.coder.gateway.sdk.v2.models

import com.squareup.moshi.Json
import java.util.UUID

data class Template(
    @Json(name = "id") val id: UUID,
    @Json(name = "active_version_id") val activeVersionID: UUID,
)
