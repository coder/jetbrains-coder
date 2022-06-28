package com.coder.gateway.sdk.v2.models

import com.google.gson.annotations.SerializedName
import java.util.UUID

data class WorkspaceApp(
    @SerializedName("id") val id: UUID,
    @SerializedName("name") val name: String,
    @SerializedName("command") val command: String?,
    @SerializedName("icon") val icon: String?,
)
