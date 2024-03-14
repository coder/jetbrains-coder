package com.coder.gateway.sdk.v2.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Validation (
    @Json(name = "field") val field: String,
    @Json(name = "detail") val detail: String,
)

@JsonClass(generateAdapter = true)
data class Response (
    @Json(name = "message") val message: String,
    @Json(name = "detail") val detail: String,
    @Json(name = "validations") val validations: List<Validation> = emptyList(),
)
