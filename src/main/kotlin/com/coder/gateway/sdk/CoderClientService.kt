package com.coder.gateway.sdk

import com.coder.gateway.sdk.ex.AuthenticationException
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Service(Service.Level.APP)
class CoderClientService : Disposable {
    private lateinit var retroRestClient: CoderRestService

    lateinit var sessionToken: String

    /**
     * This must be called before anything else. It will authenticate with coder and retrieve a session token
     * @throws [AuthenticationException] if authentication failed
     */
    fun initClientSession(host: String, port: Int, email: String, password: String) {
        val hostPath = host.trimEnd('/')
        val sessionTokenResponse = Retrofit.Builder()
            .baseUrl("http://$hostPath:$port")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoderAuthenticatonRestService::class.java).authenticate(LoginRequest(email, password)).execute()

        if (!sessionTokenResponse.isSuccessful) {
            throw AuthenticationException("Authentication failed with code:${sessionTokenResponse.code()}, reason: ${sessionTokenResponse.errorBody().toString()}")
        }
        sessionToken = sessionTokenResponse.body()!!.sessionToken
        retroRestClient = Retrofit.Builder()
            .baseUrl("https://$hostPath:$port")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoderRestService::class.java)
    }

    override fun dispose() {

    }
}