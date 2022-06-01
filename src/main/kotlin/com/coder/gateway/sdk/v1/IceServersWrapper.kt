package com.coder.gateway.sdk.v1

import com.google.gson.annotations.SerializedName
import dev.onvoid.webrtc.RTCIceServer

data class IceServersWrapper(@SerializedName("data") val iceServers: List<RTCIceServer>)
