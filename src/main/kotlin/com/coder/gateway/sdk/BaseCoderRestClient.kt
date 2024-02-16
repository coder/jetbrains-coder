package com.coder.gateway.sdk

import com.coder.gateway.icons.CoderIcons
import com.coder.gateway.icons.toRetinaAwareIcon
import com.coder.gateway.models.WorkspaceAgentModel
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
import com.coder.gateway.sdk.v2.models.WorkspaceResource
import com.coder.gateway.sdk.v2.models.WorkspaceTransition
import com.coder.gateway.sdk.v2.models.toAgentModels
import com.coder.gateway.services.CoderSettingsState
import com.coder.gateway.settings.CoderSettings
import com.coder.gateway.util.CoderHostnameVerifier
import com.coder.gateway.util.coderSocketFactory
import com.coder.gateway.util.coderTrustManagers
import com.coder.gateway.util.getHeaders
import com.coder.gateway.util.toURL
import com.coder.gateway.util.withPath
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.ImageLoader
import com.intellij.util.ui.ImageUtil
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.imgscalr.Scalr
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.*
import javax.net.ssl.X509TrustManager
import javax.swing.Icon

/**
 * In non-test code use CoderRestClient instead.
 */
open class BaseCoderRestClient(
    var url: URL, var token: String,
    private val settings: CoderSettings = CoderSettings(CoderSettingsState()),
    private val proxyValues: ProxyValues? = null,
    private val pluginVersion: String = "development",
) {
    private val httpClient: OkHttpClient
    private val retroRestClient: CoderV2RestFacade

    lateinit var me: User
    lateinit var buildVersion: String

    init {
        val gson: Gson = GsonBuilder().registerTypeAdapter(Instant::class.java, InstantConverter()).setPrettyPrinting().create()

        val socketFactory = coderSocketFactory(settings.tls)
        val trustManagers = coderTrustManagers(settings.tls.caPath)
        var builder = OkHttpClient.Builder()

        if (proxyValues != null) {
            builder = builder
                .proxySelector(proxyValues.selector)
                .proxyAuthenticator { _, response ->
                    if (proxyValues.useAuth && proxyValues.username != null && proxyValues.password != null) {
                        val credentials = Credentials.basic(proxyValues.username, proxyValues.password)
                        response.request.newBuilder()
                            .header("Proxy-Authorization", credentials)
                            .build()
                    } else null
                }
        }

        httpClient = builder
            .sslSocketFactory(socketFactory, trustManagers[0] as X509TrustManager)
            .hostnameVerifier(CoderHostnameVerifier(settings.tls.altHostname))
            .addInterceptor { it.proceed(it.request().newBuilder().addHeader("Coder-Session-Token", token).build()) }
            .addInterceptor { it.proceed(it.request().newBuilder().addHeader("User-Agent", "Coder Gateway/${pluginVersion} (${SystemInfo.getOsNameAndVersion()}; ${SystemInfo.OS_ARCH})").build()) }
            .addInterceptor {
                var request = it.request()
                val headers = getHeaders(url, settings.headerCommand)
                if (headers.isNotEmpty()) {
                    val reqBuilder = request.newBuilder()
                    headers.forEach { h -> reqBuilder.addHeader(h.key, h.value) }
                    request = reqBuilder.build()
                }
                it.proceed(request)
            }
            // This should always be last if we want to see previous interceptors logged.
            .addInterceptor(HttpLoggingInterceptor().apply { setLevel(HttpLoggingInterceptor.Level.BASIC) })
            .build()

        retroRestClient = Retrofit.Builder().baseUrl(url.toString()).client(httpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build().create(CoderV2RestFacade::class.java)
    }

    private fun <T> error(action: String, res: retrofit2.Response<T>): String {
        val details = res.errorBody()?.charStream()?.use{ it.readText() } ?: "no details provided"
        return "Unable to $action: url=$url, code=${res.code()}, details=$details"
    }

    /**
     * Authenticate and load information about the current user and the build
     * version.
     *
     * @throws [AuthenticationResponseException] if authentication failed.
     */
    fun authenticate(): User {
        me = me()
        buildVersion = buildInfo().version
        return me
    }

    /**
     * Retrieve the current user.
     * @throws [AuthenticationResponseException] if authentication failed.
     */
    fun me(): User {
        val userResponse = retroRestClient.me().execute()
        if (!userResponse.isSuccessful) {
            throw AuthenticationResponseException(error("authenticate", userResponse))
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
            throw WorkspaceResponseException(error("retrieve workspaces", workspacesResponse))
        }

        return workspacesResponse.body()!!.workspaces
    }

    /**
     * Retrieves agents for the specified workspaces, including those that are
     * off.
     */
    fun agents(workspaces: List<Workspace>): List<WorkspaceAgentModel> {
        return workspaces.flatMap {
            val resources = resources(it)
            it.toAgentModels(resources)
        }
    }

    /**
     * Retrieves resources for the specified workspace.  The workspaces response
     * does not include agents when the workspace is off so this can be used to
     * get them instead, just like `coder config-ssh` does (otherwise we risk
     * removing hosts from the SSH config when they are off).
     */
    fun resources(workspace: Workspace): List<WorkspaceResource> {
        val resourcesResponse = retroRestClient.templateVersionResources(workspace.latestBuild.templateVersionID).execute()
        if (!resourcesResponse.isSuccessful) {
            throw WorkspaceResponseException(error("retrieve resources for ${workspace.name}", resourcesResponse))
        }
        return resourcesResponse.body()!!
    }

    fun buildInfo(): BuildInfo {
        val buildInfoResponse = retroRestClient.buildInfo().execute()
        if (!buildInfoResponse.isSuccessful) {
            throw java.lang.IllegalStateException(error("retrieve build information", buildInfoResponse))
        }
        return buildInfoResponse.body()!!
    }

    private fun template(templateID: UUID): Template {
        val templateResponse = retroRestClient.template(templateID).execute()
        if (!templateResponse.isSuccessful) {
            throw TemplateResponseException(error("retrieve template with ID $templateID", templateResponse))
        }
        return templateResponse.body()!!
    }

    fun startWorkspace(workspaceID: UUID, workspaceName: String): WorkspaceBuild {
        val buildRequest = CreateWorkspaceBuildRequest(null, WorkspaceTransition.START, null, null, null, null)
        val buildResponse = retroRestClient.createWorkspaceBuild(workspaceID, buildRequest).execute()
        if (buildResponse.code() != HttpURLConnection.HTTP_CREATED) {
            throw WorkspaceResponseException(error("start workspace $workspaceName", buildResponse))
        }

        return buildResponse.body()!!
    }

    fun stopWorkspace(workspaceID: UUID, workspaceName: String): WorkspaceBuild {
        val buildRequest = CreateWorkspaceBuildRequest(null, WorkspaceTransition.STOP, null, null, null, null)
        val buildResponse = retroRestClient.createWorkspaceBuild(workspaceID, buildRequest).execute()
        if (buildResponse.code() != HttpURLConnection.HTTP_CREATED) {
            throw WorkspaceResponseException(error("stop workspace $workspaceName", buildResponse))
        }

        return buildResponse.body()!!
    }

    fun updateWorkspace(workspaceID: UUID, workspaceName: String, lastWorkspaceTransition: WorkspaceTransition, templateID: UUID): WorkspaceBuild {
        // Best practice is to STOP a workspace before doing an update if it is
        // started.
        // 1. If the update changes parameters, the old template might be needed
        //    to correctly STOP with the existing parameter values.
        // 2. The agent gets a new ID and token on each START build.  Many
        //    template authors are not diligent about making sure the agent gets
        //    restarted with this information when we do two START builds in a
        //    row.
        if (lastWorkspaceTransition == WorkspaceTransition.START) {
            stopWorkspace(workspaceID, workspaceName)
        }

        val template = template(templateID)

        val buildRequest =
            CreateWorkspaceBuildRequest(template.activeVersionID, WorkspaceTransition.START, null, null, null, null)
        val buildResponse = retroRestClient.createWorkspaceBuild(workspaceID, buildRequest).execute()
        if (buildResponse.code() != HttpURLConnection.HTTP_CREATED) {
            throw WorkspaceResponseException(error("update workspace $workspaceName", buildResponse))
        }

        return buildResponse.body()!!
    }


    private val iconCache = mutableMapOf<Pair<String, String>, Icon>()

    fun loadIcon(path: String, workspaceName: String): Icon {
        var iconURL: URL? = null
        if (path.startsWith("http")) {
            iconURL = path.toURL()
        } else if (!path.contains(":") && !path.contains("//")) {
            iconURL = url.withPath(path)
        }

        if (iconURL != null) {
            val cachedIcon = iconCache[Pair(workspaceName, path)]
            if (cachedIcon != null) {
                return cachedIcon
            }
            val img = ImageLoader.loadFromUrl(iconURL)
            if (img != null) {
                val icon = toRetinaAwareIcon(Scalr.resize(ImageUtil.toBufferedImage(img), Scalr.Method.ULTRA_QUALITY, 32))
                iconCache[Pair(workspaceName, path)] = icon
                return icon
            }
        }

        return CoderIcons.fromChar(workspaceName.lowercase().first())
    }
}
