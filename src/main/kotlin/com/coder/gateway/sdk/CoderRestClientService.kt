package com.coder.gateway.sdk

import com.coder.gateway.sdk.convertors.InstantConverter
import com.coder.gateway.sdk.ex.AuthenticationResponseException
import com.coder.gateway.sdk.ex.TemplateResponseException
import com.coder.gateway.sdk.ex.WorkspaceResponseException
import com.coder.gateway.sdk.v2.CoderV2RestFacade
import com.coder.gateway.sdk.v2.models.BuildInfo
import com.coder.gateway.sdk.v2.models.CreateWorkspaceBuildRequest
import com.coder.gateway.sdk.v2.models.Template
import com.coder.gateway.sdk.v2.models.User
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceBuild
import com.coder.gateway.sdk.v2.models.WorkspaceTransition
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.SystemInfo
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.HttpURLConnection.HTTP_CREATED
import java.net.URL
import java.time.Instant
import java.util.UUID

@Service(Service.Level.APP)
class CoderRestClientService {
    var isReady: Boolean = false
        private set
    lateinit var me: User
    lateinit var buildVersion: String
    lateinit var client: CoderRestClient

    /**
     * This must be called before anything else. It will authenticate and load
     * information about the current user and the build version.
     *
     * @throws [AuthenticationResponseException] if authentication failed.
     */
    fun initClientSession(url: URL, token: String): User {
        client = CoderRestClient(url, token)
        me = client.me()
        buildVersion = client.buildInfo().version
        isReady = true
        return me
    }
}

class CoderRestClient(var url: URL, private var token: String) {
    private var httpClient: OkHttpClient
    private var retroRestClient: CoderV2RestFacade

    init {
        val gson: Gson = GsonBuilder().registerTypeAdapter(Instant::class.java, InstantConverter()).setPrettyPrinting().create()
        val pluginVersion = PluginManagerCore.getPlugin(PluginId.getId("com.coder.gateway"))!! // this is the id from the plugin.xml

        httpClient = OkHttpClient.Builder()
            .addInterceptor { it.proceed(it.request().newBuilder().addHeader("Coder-Session-Token", token).build()) }
            .addInterceptor { it.proceed(it.request().newBuilder().addHeader("User-Agent", "Coder Gateway/${pluginVersion.version} (${SystemInfo.getOsNameAndVersion()}; ${SystemInfo.OS_ARCH})").build()) }
            // this should always be last if we want to see previous interceptors logged
            .addInterceptor(HttpLoggingInterceptor().apply { setLevel(HttpLoggingInterceptor.Level.BASIC) })
            .build()

        retroRestClient = Retrofit.Builder().baseUrl(url.toString()).client(httpClient).addConverterFactory(GsonConverterFactory.create(gson)).build().create(CoderV2RestFacade::class.java)
    }

    /**
     * Retrieve the current user.
     * @throws [AuthenticationResponseException] if authentication failed.
     */
    fun me(): User {
        val userResponse = retroRestClient.me().execute()
        if (!userResponse.isSuccessful) {
            throw AuthenticationResponseException("Could not retrieve information about logged user:${userResponse.code()}, reason: ${userResponse.message().ifBlank { "no reason provided" }}")
        }

        return userResponse.body()!!
    }

    /**
     * Retrieves the available workspaces created by the user.
     * @throws WorkspaceResponseException if workspaces could not be retrieved.
     */
    fun workspaces(): List<Workspace> {
        val workspacesResponse = retroRestClient.workspaces("owner:me").execute()
        if (!workspacesResponse.isSuccessful) {
            throw WorkspaceResponseException("Could not retrieve Coder Workspaces:${workspacesResponse.code()}, reason: ${workspacesResponse.message().ifBlank { "no reason provided" }}")
        }

        return workspacesResponse.body()!!.workspaces
    }

    fun buildInfo(): BuildInfo {
        val buildInfoResponse = retroRestClient.buildInfo().execute()
        if (!buildInfoResponse.isSuccessful) {
            throw java.lang.IllegalStateException("Could not retrieve build information for Coder instance $url, reason:${buildInfoResponse.message().ifBlank { "no reason provided" }}")
        }
        return buildInfoResponse.body()!!
    }

    fun template(templateID: UUID): Template {
        val templateResponse = retroRestClient.template(templateID).execute()
        if (!templateResponse.isSuccessful) {
            throw TemplateResponseException("Failed to retrieve template with id: $templateID, reason: ${templateResponse.message().ifBlank { "no reason provided" }}")
        }
        return templateResponse.body()!!
    }

    fun startWorkspace(workspaceID: UUID, workspaceName: String): WorkspaceBuild {
        val buildRequest = CreateWorkspaceBuildRequest(null, WorkspaceTransition.START, null, null, null, null)
        val buildResponse = retroRestClient.createWorkspaceBuild(workspaceID, buildRequest).execute()
        if (buildResponse.code() != HTTP_CREATED) {
            throw WorkspaceResponseException("Failed to build workspace ${workspaceName}: ${buildResponse.code()}, reason: ${buildResponse.message().ifBlank { "no reason provided" }}")
        }

        return buildResponse.body()!!
    }

    fun stopWorkspace(workspaceID: UUID, workspaceName: String): WorkspaceBuild {
        val buildRequest = CreateWorkspaceBuildRequest(null, WorkspaceTransition.STOP, null, null, null, null)
        val buildResponse = retroRestClient.createWorkspaceBuild(workspaceID, buildRequest).execute()
        if (buildResponse.code() != HTTP_CREATED) {
            throw WorkspaceResponseException("Failed to stop workspace ${workspaceName}: ${buildResponse.code()}, reason: ${buildResponse.message().ifBlank { "no reason provided" }}")
        }

        return buildResponse.body()!!
    }

    fun updateWorkspace(workspaceID: UUID, workspaceName: String, lastWorkspaceTransition: WorkspaceTransition, templateID: UUID): WorkspaceBuild {
        val template = template(templateID)

        val buildRequest = CreateWorkspaceBuildRequest(template.activeVersionID, lastWorkspaceTransition, null, null, null, null)
        val buildResponse = retroRestClient.createWorkspaceBuild(workspaceID, buildRequest).execute()
        if (buildResponse.code() != HTTP_CREATED) {
            throw WorkspaceResponseException("Failed to update workspace ${workspaceName}: ${buildResponse.code()}, reason: ${buildResponse.message().ifBlank { "no reason provided" }}")
        }

        return buildResponse.body()!!
    }
}
