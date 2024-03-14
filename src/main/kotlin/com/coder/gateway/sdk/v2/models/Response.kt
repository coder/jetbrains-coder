package com.coder.gateway.sdk.v2.models

import com.squareup.moshi.Json

data class Validation (
    @Json(name = "field") val field: String,
    @Json(name = "detail") val detail: String,
)

data class Response (
    @Json(name = "message") val message: String,
    @Json(name = "detail") val detail: String,
    @Json(name = "validations") val validations: List<Validation> = emptyList(),
)
