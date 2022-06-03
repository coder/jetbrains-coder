package com.coder.gateway.sdk.v2.models

import com.google.gson.annotations.SerializedName

data class Role(@SerializedName("name") val name: String, @SerializedName("display_name") val displayName: String)
