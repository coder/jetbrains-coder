package com.coder.gateway.sdk.v2.models

import com.squareup.moshi.Json
import java.util.UUID

data class CreateParameterRequest(
    @Json(name = "copy_from_parameter") val cloneID: UUID?,
    @Json(name = "name") val name: String,
    @Json(name = "source_value") val sourceValue: String,
    @Json(name = "source_scheme") val sourceScheme: ParameterSourceScheme,
    @Json(name = "destination_scheme") val destinationScheme: ParameterDestinationScheme
)

enum class ParameterSourceScheme {
    @Json(name = "none") NONE,
    @Json(name = "data") DATA
}

enum class ParameterDestinationScheme {
    @Json(name = "none") NONE,
    @Json(name = "environment_variable") ENVIRONMENT_VARIABLE,
    @Json(name = "provisioner_variable") PROVISIONER_VARIABLE
}
