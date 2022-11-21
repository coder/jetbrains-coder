package com.coder.gateway.sdk.v2.models

import com.google.gson.annotations.SerializedName

data class WorkspaceResourceMetadata(
    @SerializedName("key") val key: String,
    @SerializedName("value") val value: String,
    @SerializedName("sensitive") val sensitive: Boolean
)
