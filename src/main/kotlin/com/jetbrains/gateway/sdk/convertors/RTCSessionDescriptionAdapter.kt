package com.jetbrains.gateway.sdk.convertors

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import dev.onvoid.webrtc.RTCSdpType
import dev.onvoid.webrtc.RTCSessionDescription

class RTCSessionDescriptionAdapter : TypeAdapter<RTCSessionDescription>() {
    override fun write(writer: JsonWriter?, p1: RTCSessionDescription?) {
        TODO("not implemented yet")
    }

    override fun read(reader: JsonReader): RTCSessionDescription {
        var sdpType = 0;
        var sdp: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "type" -> sdpType = reader.nextInt()
                "sdp" -> sdp = reader.nextString()
            }
        }
        reader.endObject()
        return RTCSessionDescription(from(sdpType), sdp)
    }

    private fun from(value: Int): RTCSdpType {
        return when (value) {
            0 -> RTCSdpType.OFFER
            1 -> RTCSdpType.PR_ANSWER
            2 -> RTCSdpType.ANSWER
            3 -> RTCSdpType.ROLLBACK

            else -> RTCSdpType.OFFER
        }
    }

}