package com.coder.gateway.sdk.v2.models

import com.google.gson.annotations.SerializedName

data class WorkspacesResponse(
    @SerializedName("workspaces") val workspaces: List<Workspace>,
    @SerializedName("count") val count: Int
)
