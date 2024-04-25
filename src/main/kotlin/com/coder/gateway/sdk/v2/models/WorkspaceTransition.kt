package com.coder.gateway.sdk.v2.models

import com.squareup.moshi.Json

enum class WorkspaceTransition {
    @Json(name = "start") START,
    @Json(name = "stop") STOP,
    @Json(name = "delete") DELETE,
}
