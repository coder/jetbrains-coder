package com.coder.gateway.sdk

import com.coder.gateway.models.BrokerMessage
import com.coder.gateway.models.Workspace
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.jetbrains.gateway.sdk.convertors.InstantConverter
import com.jetbrains.gateway.sdk.convertors.RTCIceServerAdapter
import com.jetbrains.gateway.sdk.convertors.RTCSessionDescriptionAdapter
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
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
        runBlocking {
            logger.info("Connecting to workspace ${workspace.name}")
            dialWebsocket(connectionEndpoint(brokerAddr, workspace.id, token), DialOptions(token, brokerAddr, brokerAddr, iceServers))
        }
    }

    /**
     * Dials [brokerAddr] with a websocket and negotiates a connection.
     */
    private suspend fun dialWebsocket(brokerAddress: String, netOpts: DialOptions): Dialer? {
        logger.info("Connecting to broker: $brokerAddress")
        // this is the signaling channel
        val connection = withContext(Dispatchers.IO) {
            client.coderWebSocket(
                Request.Builder()
                    .url(brokerAddress)
                    .build()
            )
        }
        if (!connection.response.isSuccessful && connection.response.code != 101
        /** Switching protocols*/
        ) {
            logger.warning("Could not establish a web socket connection, code: ${connection.response.code}, reason: ${connection.response.message}")
            return null
        }
        logger.info("Connected to broker")
        return withContext(Dispatchers.IO) { dial(connection, netOpts) }
    }

    private fun connectionEndpoint(url: URL, workspaceId: String, token: String): String {
        val wsScheme = if (url.protocol == "https") "wss" else "ws"
        return "${wsScheme}://${url.host}:${url.port}/api/private/envagent/${workspaceId}/connect?session_token=${token}"
    }

    /**
     * Dial negotiates a connection to a listener.
     */
    private suspend fun dial(connection: CoderWebSocket, options: DialOptions): Dialer {
        val turnProxy = TURNProxyDialer(options.turnLocalProxyURL, options.turnProxyAuthToken)
        logger.info("creating peer connection { \"options: \"${options}, turn_proxy: ${turnProxy}\"")
        val rtc = newPeerConnection(iceServers, turnProxy, connection)
        logger.info("created peer connection")

        logger.info("creating control channel { \"proto\" : \"control\"}")
        val ctrlDataChannel = rtc.createDataChannel("control", RTCDataChannelInit().apply {
            protocol = "control"
            ordered = true
        })
        val offer = rtc.createCoderOffer(RTCOfferOptions())
        logger.info("created offer {\"offer\": ${offer.sessionDescription}}")

        rtc.setCoderLocalDescription(offer.sessionDescription)

        val offerMsg = BrokerMessage(offer.sessionDescription, options.iceServers, options.turnRemoteProxyURL.toString())
        logger.info("sending offer message {\"msg\": ${gson.toJson(offerMsg)}}")
        connection.send(gson.toJson(offerMsg).encodeUtf8())

        val dialer = Dialer(connection, ctrlDataChannel, rtc)
        dialer.negotiate()
        return dialer
    }

    private fun newPeerConnection(servers: List<RTCIceServer>, dialer: TURNProxyDialer, connection: CoderWebSocket): RTCPeerConnection {
        val configuration = RTCConfiguration().apply {
            iceServers = servers
            if (servers.size == 1) {
                val url = iceServers[0].urls[0]
                if (url.startsWith("turn") || url.startsWith("turns")) {
                    this.iceTransportPolicy = RTCIceTransportPolicy.RELAY
                }
            }
        }
        return PeerConnectionFactory().createPeerConnection(configuration, PeerConnectionImpl(connection, gson))
    }

    class PeerConnectionImpl(val connection: CoderWebSocket, private val gson: Gson) : PeerConnectionObserver {
        override fun onIceCandidate(candidate: RTCIceCandidate?) {
            logger.info(">>> onICeCandidate ${candidate}")
            connection.send(gson.toJson(BrokerMessage(candidate = candidate.toString())).encodeUtf8())
        }

        override fun onConnectionChange(state: RTCPeerConnectionState?) {
            logger.info("connection state changed { \"state\": ${state}}")
            super.onConnectionChange(state)
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
class Dialer(val connection: CoderWebSocket, val ctrl: RTCDataChannel, val rtc: RTCPeerConnection) {

    suspend fun negotiate() {
        val msg = connection.inChannel.receive()
        //  read the candidates and the answer and set it as remote description on peer connection
    }
}

data class ICECandidateInit(
    @SerializedName("candidate") val candidate: String,
    @SerializedName("sdpMid") val sdpMid: String,
    @SerializedName("sdpMLineIndex") val sdpMLineIndex: UShort,
    @SerializedName("usernameFragment") val usernameFragment: String?
)