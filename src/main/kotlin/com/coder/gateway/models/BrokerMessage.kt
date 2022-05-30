package com.coder.gateway.models

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
    @SerializedName("offer") val offer: RTCSessionDescription,
    @SerializedName("servers") val servers: List<RTCIceServer>,
    @SerializedName("turn_proxy_url") val turnProxyUrl: String,
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
        if (!ports.contentEquals(other.ports)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = offer.hashCode()
        result = 31 * result + servers.hashCode()
        result = 31 * result + turnProxyUrl.hashCode()
        result = 31 * result + ports.contentHashCode()
        return result
    }
}
