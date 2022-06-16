package com.coder.gateway.sdk

import com.coder.gateway.sdk.convertors.InstantConverter
import com.coder.gateway.sdk.ex.AuthenticationException
import com.coder.gateway.sdk.v2.CoderV2RestFacade
import com.coder.gateway.sdk.v2.models.AgentGitSSHKeys
import com.coder.gateway.sdk.v2.models.BuildInfo
import com.coder.gateway.sdk.v2.models.User
import com.coder.gateway.sdk.v2.models.Workspace
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.components.Service
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.CookieManager
import java.net.URL
import java.time.Instant

@Service(Service.Level.APP)
class CoderRestClientService {
    private lateinit var retroRestClient: CoderV2RestFacade
    lateinit var coderURL: URL
    lateinit var sessionToken: String
    lateinit var me: User
    lateinit var buildVersion: String

    /**
     * This must be called before anything else. It will authenticate with coder and retrieve a session token
     * @throws [AuthenticationException] if authentication failed
     */
    fun initClientSession(url: URL, token: String): User? {
        val cookieUrl = url.toHttpUrlOrNull()!!
        val cookieJar = JavaNetCookieJar(CookieManager()).apply {
            saveFromResponse(
                cookieUrl,
                listOf(Cookie.parse(cookieUrl, "session_token=$token")!!)
            )
        }
        val gson: Gson = GsonBuilder()
            .registerTypeAdapter(Instant::class.java, InstantConverter())
            .setPrettyPrinting()
            .create()

        val interceptor = HttpLoggingInterceptor()
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
        retroRestClient = Retrofit.Builder()
            .baseUrl(url.toString())
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(interceptor)
                    .cookieJar(cookieJar)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(CoderV2RestFacade::class.java)

        val userResponse = retroRestClient.me().execute()
        if (!userResponse.isSuccessful) {
            throw IllegalStateException("Could not retrieve information about logged use:${userResponse.code()}, reason: ${userResponse.message()}")
        }

        coderURL = url
        sessionToken = token
        me = userResponse.body()!!
        buildVersion = buildInfo().version

        return me
    }

    fun workspaces(): List<Workspace> {
        val workspacesResponse = retroRestClient.workspaces().execute()
        if (!workspacesResponse.isSuccessful) {
            throw IllegalStateException("Could not retrieve Coder Workspaces:${workspacesResponse.code()}, reason: ${workspacesResponse.message()}")
        }

        return workspacesResponse.body()!!
    }

    fun userSSHKeys(): AgentGitSSHKeys {
        val sshKeysResponse = retroRestClient.sshKeys().execute()
        if (!sshKeysResponse.isSuccessful) {
            throw IllegalStateException("Could not retrieve Coder Workspaces:${sshKeysResponse.code()}, reason: ${sshKeysResponse.message()}")
        }

        return sshKeysResponse.body()!!
    }

    fun buildInfo(): BuildInfo {
        val buildInfoResponse = retroRestClient.buildInfo().execute()
        if (!buildInfoResponse.isSuccessful) {
            throw java.lang.IllegalStateException("Could not retrieve build information for Coder instance $coderURL, reason:${buildInfoResponse.message()}")
        }
        return buildInfoResponse.body()!!
    }
}