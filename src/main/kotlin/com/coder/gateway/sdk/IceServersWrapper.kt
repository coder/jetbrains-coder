package com.coder.gateway.sdk

import com.google.gson.annotations.SerializedName
import dev.onvoid.webrtc.RTCIceServer

data class IceServersWrapper(@SerializedName("data") val iceServers: List<RTCIceServer>)
