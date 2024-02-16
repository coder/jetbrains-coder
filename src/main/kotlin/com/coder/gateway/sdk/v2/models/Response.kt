package com.coder.gateway.sdk.v2.models

import com.google.gson.annotations.SerializedName

data class Validation (
    @SerializedName("field") val field: String,
    @SerializedName("detail") val detail: String,
)

data class Response (
    @SerializedName("message") val message: String,
    @SerializedName("detail") val detail: String,
    @SerializedName("validations") val validations: List<Validation> = emptyList(),
)
