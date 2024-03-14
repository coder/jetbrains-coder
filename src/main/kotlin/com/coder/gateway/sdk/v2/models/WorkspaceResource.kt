package com.coder.gateway.sdk.v2.models

import com.squareup.moshi.Json

data class WorkspaceResource(
    @Json(name = "agents") val agents: List<WorkspaceAgent>?,
)
