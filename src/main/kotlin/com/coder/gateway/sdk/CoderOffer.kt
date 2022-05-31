package com.coder.gateway.sdk

import dev.onvoid.webrtc.CreateSessionDescriptionObserver
import dev.onvoid.webrtc.RTCOfferOptions
import dev.onvoid.webrtc.RTCPeerConnection
import dev.onvoid.webrtc.RTCSessionDescription
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CoderOffer(val sessionDescription: RTCSessionDescription)

suspend fun RTCPeerConnection.createCoderOffer(offerOptions: RTCOfferOptions) = suspendCoroutine<CoderOffer> {
    var coderOffer: CoderOffer? = null
    this.createOffer(offerOptions, object : CreateSessionDescriptionObserver {
        override fun onSuccess(description: RTCSessionDescription) {
            coderOffer = CoderOffer(description)
            it.resume(coderOffer!!)
        }

        override fun onFailure(error: String) {
            it.resumeWithException(IOException(error))
        }
    })
}