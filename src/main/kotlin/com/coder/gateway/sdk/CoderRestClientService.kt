package com.coder.gateway.sdk

import com.coder.gateway.sdk.convertors.InstantConverter
import com.coder.gateway.sdk.ex.AuthenticationResponseException
import com.coder.gateway.sdk.ex.TemplateResponseException
import com.coder.gateway.sdk.ex.WorkspaceResourcesResponseException
import com.coder.gateway.sdk.ex.WorkspaceResponseException
import com.coder.gateway.sdk.v2.CoderV2RestFacade
import com.coder.gateway.sdk.v2.models.BuildInfo
import com.coder.gateway.sdk.v2.models.CreateWorkspaceBuildRequest
import com.coder.gateway.sdk.v2.models.Template
import com.coder.gateway.sdk.v2.models.User
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceAgent
import com.coder.gateway.sdk.v2.models.WorkspaceBuild
import com.coder.gateway.sdk.v2.models.WorkspaceTransition
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
import java.net.HttpURLConnection.HTTP_CREATED
import java.net.URL
import java.time.Instant
import java.util.UUID

@Service(Service.Level.APP)
class CoderRestClientService {
    private lateinit var retroRestClient: CoderV2RestFacade
    private lateinit var sessionToken: String
    lateinit var coderURL: URL
    lateinit var me: User
    lateinit var buildVersion: String

    /**
     * This must be called before anything else. It will authenticate with coder and retrieve a session token
     * @throws [AuthenticationResponseException] if authentication failed.
     */
    fun initClientSession(url: URL, token: String): User {
        val cookieUrl = url.toHttpUrlOrNull()!!
        val cookieJar = JavaNetCookieJar(CookieManager()).apply {
            saveFromResponse(
                cookieUrl,
                listOf(Cookie.parse(cookieUrl, "coder_session_token=$token")!!)
            )
        }
        val gson: Gson = GsonBuilder()
            .registerTypeAdapter(Instant::class.java, InstantConverter())
            .setPrettyPrinting()
            .create()

        val interceptor = HttpLoggingInterceptor()
        interceptor.setLevel(HttpLoggingInterceptor.Level.BASIC)
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
            throw AuthenticationResponseException("Could not retrieve information about logged user:${userResponse.code()}, reason: ${userResponse.message()}")
        }

        coderURL = url
        sessionToken = token
        me = userResponse.body()!!
        buildVersion = buildInfo().version

        return me
    }

    /**
     * Retrieves the available workspaces created by the user.
     * @throws WorkspaceResponseException if workspaces could not be retrieved.
     */
    fun workspaces(): List<Workspace> {
        val workspacesResponse = retroRestClient.workspaces("owner:me").execute()
        if (!workspacesResponse.isSuccessful) {
            throw WorkspaceResponseException("Could not retrieve Coder Workspaces:${workspacesResponse.code()}, reason: ${workspacesResponse.message()}")
        }

        return workspacesResponse.body()!!.workspaces
    }

    private fun buildInfo(): BuildInfo {
        val buildInfoResponse = retroRestClient.buildInfo().execute()
        if (!buildInfoResponse.isSuccessful) {
            throw java.lang.IllegalStateException("Could not retrieve build information for Coder instance $coderURL, reason:${buildInfoResponse.message()}")
        }
        return buildInfoResponse.body()!!
    }

    /**
     * Retrieves the workspace agents a template declares.
     * A workspace is a collection of objects like, VMs, containers, cloud DBs, etc...Agents run on compute hosts like VMs or containers.
     *
     * @throws WorkspaceResourcesResponseException if workspace resources could not be retrieved.
     */
    fun workspaceAgentsByTemplate(workspace: Workspace): List<WorkspaceAgent> {
        val workspaceResourcesResponse = retroRestClient.templateVersionResources(workspace.latestBuild.templateVersionID).execute()
        if (!workspaceResourcesResponse.isSuccessful) {
            throw WorkspaceResourcesResponseException("Could not retrieve agents for ${workspace.name} workspace :${workspaceResourcesResponse.code()}, reason: ${workspaceResourcesResponse.message()}")
        }

        return workspaceResourcesResponse.body()!!.flatMap { it.agents ?: emptyList() }
    }

    private fun template(templateID: UUID): Template {
        val templateResponse = retroRestClient.template(templateID).execute()
        if (!templateResponse.isSuccessful) {
            throw TemplateResponseException("Failed to retrieve template with id: $templateID, reason: ${templateResponse.message()}")
        }
        return templateResponse.body()!!
    }

    fun startWorkspace(workspaceID: UUID, workspaceName: String): WorkspaceBuild {
        val buildRequest = CreateWorkspaceBuildRequest(null, WorkspaceTransition.START, null, null, null, null)
        val buildResponse = retroRestClient.createWorkspaceBuild(workspaceID, buildRequest).execute()
        if (buildResponse.code() != HTTP_CREATED) {
            throw WorkspaceResponseException("Failed to build workspace ${workspaceName}: ${buildResponse.code()}, reason: ${buildResponse.message()}")
        }

        return buildResponse.body()!!
    }

    fun stopWorkspace(workspaceID: UUID, workspaceName: String): WorkspaceBuild {
        val buildRequest = CreateWorkspaceBuildRequest(null, WorkspaceTransition.STOP, null, null, null, null)
        val buildResponse = retroRestClient.createWorkspaceBuild(workspaceID, buildRequest).execute()
        if (buildResponse.code() != HTTP_CREATED) {
            throw WorkspaceResponseException("Failed to stop workspace ${workspaceName}: ${buildResponse.code()}, reason: ${buildResponse.message()}")
        }

        return buildResponse.body()!!
    }

    fun updateWorkspace(workspaceID: UUID, workspaceName: String, lastWorkspaceTransition: WorkspaceTransition, templateID: UUID): WorkspaceBuild {
        val template = template(templateID)

        val buildRequest = CreateWorkspaceBuildRequest(template.activeVersionID, lastWorkspaceTransition, null, null, null, null)
        val buildResponse = retroRestClient.createWorkspaceBuild(workspaceID, buildRequest).execute()
        if (buildResponse.code() != HTTP_CREATED) {
            throw WorkspaceResponseException("Failed to update workspace ${workspaceName}: ${buildResponse.code()}, reason: ${buildResponse.message()}")
        }

        return buildResponse.body()!!
    }
}