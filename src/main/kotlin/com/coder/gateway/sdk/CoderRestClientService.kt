package com.coder.gateway.sdk

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
import com.coder.gateway.sdk.v2.models.WorkspaceTransition
import com.coder.gateway.sdk.v2.models.toAgentModels
import com.coder.gateway.services.CoderSettingsState
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.SystemInfo
import okhttp3.OkHttpClient
import okhttp3.internal.tls.OkHostnameVerifier
import okhttp3.logging.HttpLoggingInterceptor
import org.zeroturnaround.exec.ProcessExecutor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection.HTTP_CREATED
import java.net.InetAddress
import java.net.Socket
import java.net.URL
import java.nio.file.Path
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Locale
import java.util.UUID
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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
    fun initClientSession(url: URL, token: String, settings: CoderSettingsState): User {
        client = CoderRestClient(url, token, null, settings)
        me = client.me()
        buildVersion = client.buildInfo().version
        isReady = true
        return me
    }
}

class CoderRestClient(
    var url: URL, var token: String,
    private var pluginVersion: String?,
    private var settings: CoderSettingsState,
) {
    private var httpClient: OkHttpClient
    private var retroRestClient: CoderV2RestFacade

    init {
        val gson: Gson = GsonBuilder().registerTypeAdapter(Instant::class.java, InstantConverter()).setPrettyPrinting().create()
        if (pluginVersion.isNullOrBlank()) {
            pluginVersion = PluginManagerCore.getPlugin(PluginId.getId("com.coder.gateway"))!!.version // this is the id from the plugin.xml
        }

        val socketFactory = coderSocketFactory(settings)
        val trustManagers = coderTrustManagers(settings.tlsCAPath)
        httpClient = OkHttpClient.Builder()
            .sslSocketFactory(socketFactory, trustManagers[0] as X509TrustManager)
            .hostnameVerifier(CoderHostnameVerifier(settings.tlsAlternateHostname))
            .addInterceptor { it.proceed(it.request().newBuilder().addHeader("Coder-Session-Token", token).build()) }
            .addInterceptor { it.proceed(it.request().newBuilder().addHeader("User-Agent", "Coder Gateway/${pluginVersion} (${SystemInfo.getOsNameAndVersion()}; ${SystemInfo.OS_ARCH})").build()) }
            .addInterceptor {
                var request = it.request()
                val headers = getHeaders(url, settings.headerCommand)
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

    /**
     * Retrieves agents for the specified workspaces.  Since the workspaces
     * response does not include agents when the workspace is off, this fires
     * off separate queries to get the agents for each workspace, just like
     * `coder config-ssh` does (otherwise we risk removing hosts from the SSH
     * config when they are off).
     */
    fun agents(workspaces: List<Workspace>): List<WorkspaceAgentModel> {
        return workspaces.flatMap {
            val resourcesResponse = retroRestClient.templateVersionResources(it.latestBuild.templateVersionID).execute()
            if (!resourcesResponse.isSuccessful) {
                throw WorkspaceResponseException("Unable to retrieve template resources for ${it.name} from $url: code ${resourcesResponse.code()}, reason: ${resourcesResponse.message().ifBlank { "no reason provided" }}")
            }
            it.toAgentModels(resourcesResponse.body()!!)
        }
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

fun coderSocketFactory(settings: CoderSettingsState) : SSLSocketFactory {
    if (settings.tlsCertPath.isBlank() || settings.tlsKeyPath.isBlank()) {
        return SSLSocketFactory.getDefault() as SSLSocketFactory
    }

    val certificateFactory = CertificateFactory.getInstance("X.509")
    val certInputStream = FileInputStream(expandPath(settings.tlsCertPath))
    val certChain = certificateFactory.generateCertificates(certInputStream)
    certInputStream.close()

    // ideally we would use something like PemReader from BouncyCastle, but
    // BC is used by the IDE.  This makes using BC very impractical since
    // type casting will mismatch due to the different class loaders.
    val privateKeyPem = File(expandPath(settings.tlsKeyPath)).readText()
    val start: Int = privateKeyPem.indexOf("-----BEGIN PRIVATE KEY-----")
    val end: Int = privateKeyPem.indexOf("-----END PRIVATE KEY-----", start)
    val pemBytes: ByteArray = Base64.getDecoder().decode(
        privateKeyPem.substring(start + "-----BEGIN PRIVATE KEY-----".length, end)
            .replace("\\s+".toRegex(), "")
    )

    var privateKey : PrivateKey
    try {
        val kf = KeyFactory.getInstance("RSA")
        val keySpec = PKCS8EncodedKeySpec(pemBytes)
        privateKey = kf.generatePrivate(keySpec)
    } catch (e: InvalidKeySpecException) {
        val kf = KeyFactory.getInstance("EC")
        val keySpec = PKCS8EncodedKeySpec(pemBytes)
        privateKey = kf.generatePrivate(keySpec)
    }

    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
    keyStore.load(null)
    certChain.withIndex().forEach {
        keyStore.setCertificateEntry("cert${it.index}", it.value as X509Certificate)
    }
    keyStore.setKeyEntry("key", privateKey, null, certChain.toTypedArray())

    val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    keyManagerFactory.init(keyStore, null)

    val sslContext = SSLContext.getInstance("TLS")

    val trustManagers = coderTrustManagers(settings.tlsCAPath)
    sslContext.init(keyManagerFactory.keyManagers, trustManagers, null)

    if (settings.tlsAlternateHostname.isBlank()) {
        return sslContext.socketFactory
    }

    return AlternateNameSSLSocketFactory(sslContext.socketFactory, settings.tlsAlternateHostname)
}

fun coderTrustManagers(tlsCAPath: String) : Array<TrustManager> {
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    if (tlsCAPath.isBlank()) {
        // return default trust managers
        trustManagerFactory.init(null as KeyStore?)
        return trustManagerFactory.trustManagers
    }


    val certificateFactory = CertificateFactory.getInstance("X.509")
    val caInputStream = FileInputStream(expandPath(tlsCAPath))
    val certChain = certificateFactory.generateCertificates(caInputStream)

    val truststore = KeyStore.getInstance(KeyStore.getDefaultType())
    truststore.load(null)
    certChain.withIndex().forEach {
        truststore.setCertificateEntry("cert${it.index}", it.value as X509Certificate)
    }
    trustManagerFactory.init(truststore)
    return trustManagerFactory.trustManagers.map { MergedSystemTrustManger(it as X509TrustManager) }.toTypedArray()
}

fun expandPath(path: String): String {
    if (path.startsWith("~/")) {
        return Path.of(System.getProperty("user.home"), path.substring(1)).toString()
    }
    if (path.startsWith("\$HOME/")) {
        return Path.of(System.getProperty("user.home"), path.substring(5)).toString()
    }
    if (path.startsWith("\${user.home}/")) {
        return Path.of(System.getProperty("user.home"), path.substring(12)).toString()
    }
    return path
}

class AlternateNameSSLSocketFactory(private val delegate: SSLSocketFactory, private val alternateName: String) : SSLSocketFactory() {
    override fun getDefaultCipherSuites(): Array<String> {
        return delegate.defaultCipherSuites
    }

    override fun getSupportedCipherSuites(): Array<String> {
        return delegate.supportedCipherSuites
    }

    override fun createSocket(): Socket {
        val socket = delegate.createSocket() as SSLSocket
        customizeSocket(socket)
        return socket
    }

    override fun createSocket(host: String?, port: Int): Socket {
        val socket = delegate.createSocket(host,  port) as SSLSocket
        customizeSocket(socket)
        return socket
    }

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket {
        val socket = delegate.createSocket(host, port, localHost, localPort) as SSLSocket
        customizeSocket(socket)
        return socket
    }

    override fun createSocket(host: InetAddress?, port: Int): Socket {
        val socket = delegate.createSocket(host, port) as SSLSocket
        customizeSocket(socket)
        return socket
    }

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket {
        val socket = delegate.createSocket(address, port, localAddress, localPort) as SSLSocket
        customizeSocket(socket)
        return socket
    }

    override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket {
        val socket = delegate.createSocket(s, host, port, autoClose) as SSLSocket
        customizeSocket(socket)
        return socket
    }

    private fun customizeSocket(socket: SSLSocket) {
        val params = socket.sslParameters
        params.serverNames = listOf(SNIHostName(alternateName))
        socket.sslParameters = params
    }
}

class CoderHostnameVerifier(private val alternateName: String) : HostnameVerifier {
    override fun verify(host: String, session: SSLSession): Boolean {
        if (alternateName.isEmpty()) {
            println("using default hostname verifier, alternateName is  empty")
            return OkHostnameVerifier.verify(host, session)
        }
        println("Looking for alternate hostname: $alternateName")
        val certs = session.peerCertificates ?: return false
        for (cert in certs) {
            if (cert !is X509Certificate) {
                continue
            }
            val entries = cert.subjectAlternativeNames ?: continue
            for (entry in entries) {
                val kind = entry[0] as Int
                if (kind != 2) { // DNS Name
                    continue
                }
                val hostname = entry[1] as String
                println("Found cert hostname: $hostname")
                if (hostname.lowercase(Locale.getDefault()) == alternateName) {
                    return true
                }
            }
        }
        println("No matching hostname found")
        return false
    }
}

class MergedSystemTrustManger(private val otherTrustManager: X509TrustManager) : X509TrustManager {
    private val systemTrustManager : X509TrustManager
    init {
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(null as KeyStore?)
        systemTrustManager = trustManagerFactory.trustManagers.first { it is X509TrustManager } as X509TrustManager
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String?) {
        try {
            otherTrustManager.checkClientTrusted(chain, authType)
        } catch (e: CertificateException) {
            systemTrustManager.checkClientTrusted(chain, authType)
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String?) {
        try {
            otherTrustManager.checkServerTrusted(chain, authType)
        } catch (e: CertificateException) {
            systemTrustManager.checkServerTrusted(chain, authType)
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return otherTrustManager.acceptedIssuers + systemTrustManager.acceptedIssuers
    }
}