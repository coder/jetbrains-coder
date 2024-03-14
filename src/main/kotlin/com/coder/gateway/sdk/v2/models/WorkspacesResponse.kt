package com.coder.gateway.sdk.v2.models

import com.squareup.moshi.Json

data class WorkspacesResponse(
    @Json(name = "workspaces") val workspaces: List<Workspace>,
)
