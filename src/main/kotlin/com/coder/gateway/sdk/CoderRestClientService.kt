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
import org.zeroturnaround.exec.ProcessExecutor
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
    fun initClientSession(url: URL, token: String, headerCommand: String?): User {
        client = CoderRestClient(url, token, headerCommand)
        me = client.me()
        buildVersion = client.buildInfo().version
        isReady = true
        return me
    }
}

class CoderRestClient(var url: URL, var token: String, var headerCommand: String?) {
    private var httpClient: OkHttpClient
    private var retroRestClient: CoderV2RestFacade

    init {
        val gson: Gson = GsonBuilder().registerTypeAdapter(Instant::class.java, InstantConverter()).setPrettyPrinting().create()
        val pluginVersion = PluginManagerCore.getPlugin(PluginId.getId("com.coder.gateway"))!! // this is the id from the plugin.xml

        httpClient = OkHttpClient.Builder()
            .addInterceptor { it.proceed(it.request().newBuilder().addHeader("Coder-Session-Token", token).build()) }
            .addInterceptor { it.proceed(it.request().newBuilder().addHeader("User-Agent", "Coder Gateway/${pluginVersion.version} (${SystemInfo.getOsNameAndVersion()}; ${SystemInfo.OS_ARCH})").build()) }
            .addInterceptor {
                var request = it.request()
                val headers = getHeaders(url, headerCommand)
                if (headers.size > 0) {
                    val builder = request.newBuilder()
                    headers.forEach { h -> builder.addHeader(h.key, h.value) }
                    request = builder.build()
                }
                it.proceed(request)
            }
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
            throw AuthenticationResponseException("Unable to authenticate to $url: code ${userResponse.code()}, ${userResponse.message().ifBlank { "has your token expired?" }}")
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
            throw WorkspaceResponseException("Unable to retrieve workspaces from $url: code ${workspacesResponse.code()}, reason: ${workspacesResponse.message().ifBlank { "no reason provided" }}")
        }

        return workspacesResponse.body()!!.workspaces
    }

    fun buildInfo(): BuildInfo {
        val buildInfoResponse = retroRestClient.buildInfo().execute()
        if (!buildInfoResponse.isSuccessful) {
            throw java.lang.IllegalStateException("Unable to retrieve build information for $url, code: ${buildInfoResponse.code()}, reason: ${buildInfoResponse.message().ifBlank { "no reason provided" }}")
        }
        return buildInfoResponse.body()!!
    }

    private fun template(templateID: UUID): Template {
        val templateResponse = retroRestClient.template(templateID).execute()
        if (!templateResponse.isSuccessful) {
            throw TemplateResponseException("Unable to retrieve template with ID $templateID from $url, code: ${templateResponse.code()}, reason: ${templateResponse.message().ifBlank { "no reason provided" }}")
        }
        return templateResponse.body()!!
    }

    fun startWorkspace(workspaceID: UUID, workspaceName: String): WorkspaceBuild {
        val buildRequest = CreateWorkspaceBuildRequest(null, WorkspaceTransition.START, null, null, null, null)
        val buildResponse = retroRestClient.createWorkspaceBuild(workspaceID, buildRequest).execute()
        if (buildResponse.code() != HTTP_CREATED) {
            throw WorkspaceResponseException("Unable to build workspace $workspaceName on $url, code: ${buildResponse.code()}, reason: ${buildResponse.message().ifBlank { "no reason provided" }}")
        }

        return buildResponse.body()!!
    }

    fun stopWorkspace(workspaceID: UUID, workspaceName: String): WorkspaceBuild {
        val buildRequest = CreateWorkspaceBuildRequest(null, WorkspaceTransition.STOP, null, null, null, null)
        val buildResponse = retroRestClient.createWorkspaceBuild(workspaceID, buildRequest).execute()
        if (buildResponse.code() != HTTP_CREATED) {
            throw WorkspaceResponseException("Unable to stop workspace $workspaceName on $url, code: ${buildResponse.code()}, reason: ${buildResponse.message().ifBlank { "no reason provided" }}")
        }

        return buildResponse.body()!!
    }

    fun updateWorkspace(workspaceID: UUID, workspaceName: String, lastWorkspaceTransition: WorkspaceTransition, templateID: UUID): WorkspaceBuild {
        val template = template(templateID)

        val buildRequest = CreateWorkspaceBuildRequest(template.activeVersionID, lastWorkspaceTransition, null, null, null, null)
        val buildResponse = retroRestClient.createWorkspaceBuild(workspaceID, buildRequest).execute()
        if (buildResponse.code() != HTTP_CREATED) {
            throw WorkspaceResponseException("Unable to update workspace $workspaceName on $url, code: ${buildResponse.code()}, reason: ${buildResponse.message().ifBlank { "no reason provided" }}")
        }

        return buildResponse.body()!!
    }

    companion object {
        private val newlineRegex = "\r?\n".toRegex()
        private val endingNewlineRegex = "\r?\n$".toRegex()

        // TODO: This really only needs to be a private function, but
        // unfortunately it is not possible to test the client because it fails
        // on the plugin manager core call and I do not know how to fix it.  So,
        // for now make this static and test it directly instead.
        @JvmStatic
        fun getHeaders(url: URL, headerCommand: String?): Map<String, String> {
            if (headerCommand.isNullOrBlank()) {
                return emptyMap()
            }
            val (shell, caller) = when (getOS()) {
                OS.WINDOWS -> Pair("cmd.exe", "/c")
                else -> Pair("sh", "-c")
            }
            return ProcessExecutor()
                .command(shell, caller, headerCommand)
                .environment("CODER_URL", url.toString())
                .exitValues(0)
                .readOutput(true)
                .execute()
                .outputUTF8()
                .replaceFirst(endingNewlineRegex, "")
                .split(newlineRegex)
                .associate {
                    // Header names cannot be blank or contain whitespace and
                    // the Coder CLI requires that there be an equals sign (the
                    // value can be blank though).  The second case is taken
                    // care of by the destructure here, as it will throw if
                    // there are not enough parts.
                    val (name, value) = it.split("=", limit=2)
                    if (name.contains(" ") || name == "") {
                        throw Exception("\"$name\" is not a valid header name")
                    }
                    name to value
                }
        }
    }
}
