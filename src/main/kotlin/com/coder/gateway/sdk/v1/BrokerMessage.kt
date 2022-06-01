package com.coder.gateway.sdk.v1

import com.google.gson.annotations.SerializedName
import dev.onvoid.webrtc.RTCIceServer
import dev.onvoid.webrtc.RTCSessionDescription

/**
 * BrokerMessage is used for brokering a dialer and listener.
 *
 * Dialers initiate an exchange by providing an Offer, along with a list of ICE servers for the listener to peer with.
 * The listener should respond with an offer, then both sides can begin exchanging candidates.
 *
 */
data class BrokerMessage(
    // Dialer -> Listener
    @SerializedName("offer") val offer: RTCSessionDescription? = null,
    @SerializedName("servers") val servers: List<RTCIceServer>? = null,
    @SerializedName("turn_proxy_url") val turnProxyUrl: String? = "",
    @SerializedName("ports") val ports: Array<DialPolicy>? = null,
    // Listener -> Dialer
    @SerializedName("error") val error: String? = "",
    @SerializedName("answer") val answer: RTCSessionDescription? = null,
    // Bidirectional
    @SerializedName("candidate") val candidate: String? = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BrokerMessage

        if (offer != other.offer) return false
        if (servers != other.servers) return false
        if (turnProxyUrl != other.turnProxyUrl) return false
        if (ports != null) {
            if (other.ports == null) return false
            if (!ports.contentEquals(other.ports)) return false
        } else if (other.ports != null) return false
        if (error != other.error) return false
        if (answer != other.answer) return false
        if (candidate != other.candidate) return false

        return true
    }

    override fun hashCode(): Int {
        var result = offer?.hashCode() ?: 0
        result = 31 * result + (servers?.hashCode() ?: 0)
        result = 31 * result + (turnProxyUrl?.hashCode() ?: 0)
        result = 31 * result + (ports?.contentHashCode() ?: 0)
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + (answer?.hashCode() ?: 0)
        result = 31 * result + (candidate?.hashCode() ?: 0)
        return result
    }
}
