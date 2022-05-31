package com.coder.gateway.sdk

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Wrapper over OkHttp WebSocket with callbacks converted to coroutines
 */
class CoderWebSocket(val webSocket: WebSocket, val response: Response) {
    internal val inChannel = Channel<ByteString>()

    suspend fun receive(): ByteString {
        return inChannel.receive()
    }

    fun send(byteString: ByteString): Boolean {
        return webSocket.send(byteString)
    }
}

suspend fun OkHttpClient.coderWebSocket(request: Request) = suspendCoroutine<CoderWebSocket> {
    var coderWebSocket: CoderWebSocket? = null
    newWebSocket(request, object : WebSocketListener() {
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            coderWebSocket!!.inChannel.close()
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            it.resumeWithException(t)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            runBlocking { coderWebSocket!!.inChannel.send(bytes) }
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            coderWebSocket = CoderWebSocket(webSocket, response)
            it.resume(coderWebSocket!!)
        }
    })
}