package com.coder.gateway.sdk

import com.coder.gateway.models.BrokerMessage
import com.coder.gateway.models.Workspace
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.jetbrains.gateway.sdk.convertors.InstantConverter
import com.jetbrains.gateway.sdk.convertors.RTCIceServerAdapter
import com.jetbrains.gateway.sdk.convertors.RTCSessionDescriptionAdapter
import dev.onvoid.webrtc.CreateSessionDescriptionObserver
import dev.onvoid.webrtc.PeerConnectionFactory
import dev.onvoid.webrtc.PeerConnectionObserver
import dev.onvoid.webrtc.RTCConfiguration
import dev.onvoid.webrtc.RTCDataChannel
import dev.onvoid.webrtc.RTCDataChannelInit
import dev.onvoid.webrtc.RTCIceCandidate
import dev.onvoid.webrtc.RTCIceServer
import dev.onvoid.webrtc.RTCIceTransportPolicy
import dev.onvoid.webrtc.RTCOfferOptions
import dev.onvoid.webrtc.RTCPeerConnection
import dev.onvoid.webrtc.RTCPeerConnectionState
import dev.onvoid.webrtc.RTCSessionDescription
import dev.onvoid.webrtc.SetSessionDescriptionObserver
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import java.net.URL
import java.time.Instant
import java.util.logging.Logger

private const val REMOTE_PORT = 12213

class Tunneler(val brokerAddr: URL, val token: String, val workspace: Workspace, val iceServers: List<RTCIceServer>, val sshPort: Int = 22, val remotePort: Int = REMOTE_PORT) {
    val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Instant::class.java, InstantConverter())
        .registerTypeAdapter(RTCIceServer::class.java, RTCIceServerAdapter())
        .registerTypeAdapter(RTCSessionDescription::class.java, RTCSessionDescriptionAdapter())
        .setPrettyPrinting()
        .create()

    val client = OkHttpClient.Builder().addInterceptor(HttpLoggingInterceptor().apply { setLevel(HttpLoggingInterceptor.Level.BODY) }).build()

    fun start() {
        logger.info("Connecting to workspace ${workspace.name}")
        dialWebsocket(connectionEndpoint(brokerAddr, workspace.id, token), DialOptions(token, brokerAddr, brokerAddr, iceServers))
    }

    /**
     * Dials [brokerAddr] with a websocket and negotiates a connection.
     */
    private fun dialWebsocket(brokerAddress: String, netOpts: DialOptions): Dialer {
        logger.info("Connecting to broker: $brokerAddress")
        val request: Request = Request.Builder()
            .url(brokerAddress)
            .build()


        val connection = client.newWebSocket(request, object : WebSocketListener() {
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                logger.info(">>> onClose -> code: ${code} reason: ${reason}")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosing(webSocket, code, reason)
                logger.info(">>> onClosing -> code: ${code} reason: ${reason}")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                logger.info(">>> onFailure -> code: ${t} reason: ${response}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                logger.info(">>> onMessage -> text: ${text}")
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                super.onMessage(webSocket, bytes)
                logger.info(">>> onMessage -> bytes: ${bytes}")
            }

            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                logger.info(">>> onOpen -> response: ${response}")
            }
        })

        logger.info("Connected to broker")
        return dial(connection, netOpts)
    }

    private fun connectionEndpoint(url: URL, workspaceId: String, token: String): String {
        val wsScheme = if (url.protocol == "https") "wss" else "ws"
        return "${wsScheme}://${url.host}/api/private/envagent/${workspaceId}/connect?session_token=${token}"
    }

    /**
     * Dial negotiates a connection to a listener.
     */
    private fun dial(connection: WebSocket, options: DialOptions): Dialer {
        val turnProxy = TURNProxyDialer(options.turnLocalProxyURL, options.turnProxyAuthToken)
        logger.info("creating peer connection { \"options: \"${options}, turn_proxy: ${turnProxy}\"")
        val rtc = newPeerConnection(iceServers, turnProxy, connection)
        logger.info("created peer connection")

        logger.info("creating control channel { \"proto\" : \"control\"}")
        rtc.createDataChannel("control", RTCDataChannelInit().apply {
            protocol = "control"
            ordered = true
        })
        rtc.createOffer(RTCOfferOptions(), object : CreateSessionDescriptionObserver {
            override fun onSuccess(sessionDescription: RTCSessionDescription?) {
                logger.info("created offer {\"offer\": ${sessionDescription}}")
                rtc.setLocalDescription(sessionDescription, object : SetSessionDescriptionObserver {
                    override fun onSuccess() {
                        logger.info("set local offer $sessionDescription with success")
                        val offerMsg = BrokerMessage(sessionDescription!!, options.iceServers, options.turnRemoteProxyURL.toString())
                        logger.info("sending offer message {\"msg\": ${gson.toJson(offerMsg)}}")
                        connection.send(gson.toJson(offerMsg).encodeUtf8())
                    }

                    override fun onFailure(p0: String?) {
                        logger.warning("failed to set local $p0 with success")
                        TODO("Not yet implemented")
                    }
                })
            }

            override fun onFailure(p0: String?) {
                logger.warning("onFailure to set local $p0 with success")
                TODO("Not yet implemented")
            }
        })

        return Dialer(connection, rtc.createDataChannel("data_channel_tmp", RTCDataChannelInit()), rtc)
    }

    private fun newPeerConnection(servers: List<RTCIceServer>, dialer: TURNProxyDialer, connection: WebSocket): RTCPeerConnection {
        val configuration = RTCConfiguration().apply {
            iceServers = servers
            if (servers.size == 1) {
                val url = iceServers[0].urls[0]
                if (url.startsWith("turn") || url.startsWith("turns")) {
                    this.iceTransportPolicy = RTCIceTransportPolicy.RELAY
                }
            }
        }
        return PeerConnectionFactory().createPeerConnection(configuration, PeerConnectionImpl(connection))
    }

    class PeerConnectionImpl(val connection: WebSocket) : PeerConnectionObserver {
        override fun onIceCandidate(candidate: RTCIceCandidate?) {
            logger.info(">>> onICeCandidate ${candidate}")
        }

        override fun onConnectionChange(state: RTCPeerConnectionState?) {
            super.onConnectionChange(state)
            logger.info("connection state changed { \"state\": ${state}}")
        }
    }

    companion object {
        val logger = Logger.getLogger(Tunneler::class.java.simpleName)
        // TODO run a coroutine that update the last connection status every minute sdk.UpdateLastConnectionAt(ctx, c.workspace.ID)
    }
}

data class DialOptions(val turnProxyAuthToken: String, val turnRemoteProxyURL: URL, val turnLocalProxyURL: URL, val iceServers: List<RTCIceServer>)

/**
 * Proxies all TURN ICEServer traffic through this dialer
 */
data class TURNProxyDialer(val baseURL: URL, val token: String)

/**
 * Dialer enables arbitrary dialing to any network and address
 * inside a workspace. The opposing end of the WebSocket messages
 * should be proxied with a Listener.
 */
class Dialer(val connection: WebSocket, val ctrl: RTCDataChannel, val rtc: RTCPeerConnection)

data class ICECandidateInit(
    @SerializedName("candidate") val candidate: String,
    @SerializedName("sdpMid") val sdpMid: String,
    @SerializedName("sdpMLineIndex") val sdpMLineIndex: UShort,
    @SerializedName("usernameFragment") val usernameFragment: String?
)