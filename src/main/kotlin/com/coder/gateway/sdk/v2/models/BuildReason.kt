package com.coder.gateway.sdk.v2.models

import com.google.gson.annotations.SerializedName

enum class BuildReason {
    // "initiator" is used when a workspace build is triggered by a user.
    // Combined with the initiator id/username, it indicates which user initiated the build.
    @SerializedName("initiator")
    INITIATOR,

    // "autostart" is used when a build to start a workspace is triggered by Autostart.
    // The initiator id/username in this case is the workspace owner and can be ignored.
    @SerializedName("autostart")
    AUTOSTART,

    // "autostop" is used when a build to stop a workspace is triggered by Autostop.
    // The initiator id/username in this case is the workspace owner and can be ignored.
    @SerializedName("autostop")
    AUTOSTOP
}