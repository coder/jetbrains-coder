package com.coder.gateway.sdk.v1

import com.google.gson.annotations.SerializedName
import java.time.Duration

data class RebuildMessage(
    @SerializedName("text") val text: String,
    @SerializedName("required") val required: Boolean,
    @SerializedName("auto_off_threshold") val auto_off_threshold: Duration
)
