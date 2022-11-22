package com.coder.gateway.sdk.v2.models

import com.google.gson.annotations.SerializedName

enum class WorkspaceTransition {
    @SerializedName("start")
    START,

    @SerializedName("stop")
    STOP,

    @SerializedName("delete")
    DELETE
}