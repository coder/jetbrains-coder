package com.coder.gateway.sdk

import com.coder.gateway.models.SSHKeys
import com.coder.gateway.models.UriScheme
import com.coder.gateway.models.User
import com.coder.gateway.models.Workspace
import com.coder.gateway.sdk.ex.AuthenticationException
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.components.Service
import com.jetbrains.gateway.sdk.convertors.InstantConverter
import com.jetbrains.gateway.sdk.convertors.RTCIceServerAdapter
import dev.onvoid.webrtc.RTCIceServer
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Instant

@Service(Service.Level.APP)
class CoderClientService {
    private lateinit var retroRestClient: CoderRestService

    lateinit var sessionToken: String
    lateinit var me: User

    /**
     * This must be called before anything else. It will authenticate with coder and retrieve a session token
     * @throws [AuthenticationException] if authentication failed
     */
    fun initClientSession(uriScheme: UriScheme, host: String, port: Int, email: String, password: String) {
        val hostPath = host.trimEnd('/')

        val gson: Gson = GsonBuilder()
            .registerTypeAdapter(Instant::class.java, InstantConverter())
            .registerTypeAdapter(RTCIceServer::class.java, RTCIceServerAdapter())
            .setPrettyPrinting()
            .create()

        val interceptor = HttpLoggingInterceptor()
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)

        retroRestClient = Retrofit.Builder()
            .baseUrl("${uriScheme.scheme}://$hostPath:$port")
            .client(OkHttpClient.Builder().addInterceptor(interceptor).build())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(CoderRestService::class.java)

        val sessionTokenResponse = retroRestClient.authenticate(LoginRequest(email, password)).execute()

        if (!sessionTokenResponse.isSuccessful) {
            throw AuthenticationException("Authentication failed with code:${sessionTokenResponse.code()}, reason: ${sessionTokenResponse.message()}")
        }
        sessionToken = sessionTokenResponse.body()!!.sessionToken

        val userResponse = retroRestClient.me(sessionToken).execute()

        if (!userResponse.isSuccessful) {
            throw IllegalStateException("Could not retrieve information about logged use:${userResponse.code()}, reason: ${userResponse.message()}")
        }

        me = userResponse.body()!!
    }

    fun workspaces(): List<Workspace> {
        val workspacesResponse = retroRestClient.workspaces(sessionToken, me.id).execute()
        if (!workspacesResponse.isSuccessful) {
            throw IllegalStateException("Could not retrieve Coder Workspaces:${workspacesResponse.code()}, reason: ${workspacesResponse.message()}")
        }

        return workspacesResponse.body()!!
    }

    fun userSSHKeys(): SSHKeys {
        val sshKeysResponse = retroRestClient.sshKeys(sessionToken, me.id).execute()
        if (!sshKeysResponse.isSuccessful) {
            throw IllegalStateException("Could not retrieve Coder Workspaces:${sshKeysResponse.code()}, reason: ${sshKeysResponse.message()}")
        }

        return sshKeysResponse.body()!!
    }

    fun iceServers(): List<RTCIceServer> {
        val iceServersResponse = retroRestClient.iceServers(sessionToken).execute()
        if (!iceServersResponse.isSuccessful) {
            throw IllegalStateException("Could not retrieve retrieve ICE servers:${iceServersResponse.code()}, reason: ${iceServersResponse.message()}")
        }
        return iceServersResponse.body()!!.iceServers
    }
}