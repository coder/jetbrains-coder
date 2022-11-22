package com.coder.gateway.sdk.v2.models

import com.google.gson.annotations.SerializedName
import java.util.UUID

data class CreateParameterRequest(
    @SerializedName("copy_from_parameter") val cloneID: UUID?,
    @SerializedName("name") val name: String,
    @SerializedName("source_value") val sourceValue: String,
    @SerializedName("source_scheme") val sourceScheme: ParameterSourceScheme,
    @SerializedName("destination_scheme") val destinationScheme: ParameterDestinationScheme
)

enum class ParameterSourceScheme {
    @SerializedName("none")
    NONE,

    @SerializedName("data")
    DATA
}

enum class ParameterDestinationScheme {
    @SerializedName("none")
    NONE,

    @SerializedName("environment_variable")
    ENVIRONMENT_VARIABLE,

    @SerializedName("provisioner_variable")
    PROVISIONER_VARIABLE
}