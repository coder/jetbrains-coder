package com.coder.gateway.models

import com.google.gson.annotations.SerializedName
import dev.onvoid.webrtc.RTCIceServer
import dev.onvoid.webrtc.RTCSessionDescription

data class BrokerMessage(@SerializedName("offer") val offer: RTCSessionDescription, @SerializedName("servers") val servers: List<RTCIceServer>, @SerializedName("turn_proxy_url") val turnProxyUrl: String)
