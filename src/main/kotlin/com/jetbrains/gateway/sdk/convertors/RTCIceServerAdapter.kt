package com.jetbrains.gateway.sdk.convertors

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import dev.onvoid.webrtc.RTCIceServer

private const val URLS = "urls"

private const val USERNAME = "username"

private const val PASSWORD = "password"

/**
 * GSON adapter for converting [RTCIceServer] objects.
 */
class RTCIceServerAdapter : TypeAdapter<RTCIceServer>() {
    override fun write(writer: JsonWriter, iceServer: RTCIceServer) {
        writer.beginObject()
        writer.name(URLS).beginArray()
        iceServer.urls.forEach {
            writer.value(it)
        }
        writer.endArray()
        writer.name(USERNAME).value(iceServer.username)
        writer.name(PASSWORD).value(iceServer.password)
        writer.endObject()
    }

    override fun read(reader: JsonReader): RTCIceServer {
        val iceServer = RTCIceServer()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                URLS -> {
                    val urls = mutableListOf<String>()
                    reader.beginArray()
                    while (reader.hasNext()) {
                        urls.add(reader.nextString())
                    }
                    reader.endArray()
                    iceServer.urls = urls
                }
                USERNAME -> iceServer.username = reader.nextString()
                "credential" -> iceServer.password = reader.nextString()
            }
        }
        reader.endObject()
        return iceServer
    }

}