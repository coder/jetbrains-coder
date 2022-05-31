package com.coder.gateway.sdk

import dev.onvoid.webrtc.RTCPeerConnection
import dev.onvoid.webrtc.RTCSessionDescription
import dev.onvoid.webrtc.SetSessionDescriptionObserver
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun RTCPeerConnection.setCoderLocalDescription(sessionDescription: RTCSessionDescription) = suspendCoroutine<Unit> {
    this.setLocalDescription(sessionDescription, object : SetSessionDescriptionObserver {
        override fun onSuccess() {
            it.resume(Unit)
        }

        override fun onFailure(reason: String) {
            it.resumeWithException(IOException(reason))
        }
    })
}