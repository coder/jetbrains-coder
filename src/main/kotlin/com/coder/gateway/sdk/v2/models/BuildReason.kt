package com.coder.gateway.sdk.v2.models

import com.squareup.moshi.Json

enum class BuildReason {
    // "initiator" is used when a workspace build is triggered by a user.
    // Combined with the initiator id/username, it indicates which user initiated the build.
    @Json(name = "initiator") INITIATOR,
    // "autostart" is used when a build to start a workspace is triggered by Autostart.
    // The initiator id/username in this case is the workspace owner and can be ignored.
    @Json(name = "autostart") AUTOSTART,
    // "autostop" is used when a build to stop a workspace is triggered by Autostop.
    // The initiator id/username in this case is the workspace owner and can be ignored.
    @Json(name = "autostop") AUTOSTOP
}
