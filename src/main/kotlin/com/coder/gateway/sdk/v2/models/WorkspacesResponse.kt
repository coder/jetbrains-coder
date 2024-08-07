package com.coder.gateway.sdk.v2.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WorkspacesResponse(
    @Json(name = "workspaces") val workspaces: List<Workspace>,
)
