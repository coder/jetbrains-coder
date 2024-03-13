package com.coder.gateway

import com.coder.gateway.cli.CoderCLIManager
import com.coder.gateway.sdk.CoderRestClient
import com.coder.gateway.sdk.v2.models.WorkspaceStatus
import com.coder.gateway.services.CoderSecretsService
import com.coder.gateway.services.CoderSettingsService
import com.coder.gateway.settings.CoderSettings
import com.coder.gateway.util.withPath
import com.jetbrains.toolbox.gateway.PluginSecretStore
import com.jetbrains.toolbox.gateway.PluginSettingsStore
import com.jetbrains.toolbox.gateway.ProviderVisibilityState
import com.jetbrains.toolbox.gateway.RemoteEnvironmentConsumer
import com.jetbrains.toolbox.gateway.RemoteProvider
import com.jetbrains.toolbox.gateway.ui.AccountDropdownField
import com.jetbrains.toolbox.gateway.ui.CheckboxField
import com.jetbrains.toolbox.gateway.ui.LinkField
import com.jetbrains.toolbox.gateway.ui.RunnableActionDescription
import com.jetbrains.toolbox.gateway.ui.TextField
import com.jetbrains.toolbox.gateway.ui.TextType
import com.jetbrains.toolbox.gateway.ui.ToolboxUi
import com.jetbrains.toolbox.gateway.ui.UiField
import com.jetbrains.toolbox.gateway.ui.UiPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URL
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Function
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

class CoderRemoteProvider(
    private val httpClient: OkHttpClient,
    private val consumer: RemoteEnvironmentConsumer,
    private val coroutineScope: CoroutineScope,
    private val ui: ToolboxUi,
    settingsStore: PluginSettingsStore,
    secretsStore: PluginSecretStore,
) : RemoteProvider {
    private var job: Job? = null
    private val logger = LoggerFactory.getLogger(javaClass)
    private var lastEnvironments: List<CoderRemoteEnvironment> = emptyList()

    private val settingsService = CoderSettingsService(settingsStore)
    private val settings: CoderSettings = CoderSettings(settingsService)
    private val secrets: CoderSecretsService = CoderSecretsService(secretsStore)
    private val settingsPage: CoderSettingsPage = CoderSettingsPage(settingsService)

    // The REST client, if we are signed in.
    private var client: CoderRestClient? = null
    private val signInPage: SignInPage

    init {
        val lastUrl = secrets.url
        val lastToken = secrets.token

        // Once we sign in, create the client and start polling.
        signInPage = SignInPage(lastUrl)
        signInPage.onSignIn = { url ->
            // TODO: If lastToken is blank, try pulling from config.
            // TODO: Normalize URL (copy and expose from original plugin).
            val tokenPage = TokenPage(url, lastToken)
            tokenPage.onSignIn = { token ->
                authenticate(url, token)
                // TODO: Update the field's initial value as well, since I am
                //       pretty sure it gets wiped and would use the old value
                //       again.  Maybe we should just recreate the page each
                //       time it is needed instead of keeping it here like this.
                secrets.url = url.toString()
                secrets.token = token
                ui.hideUiPage(tokenPage)
                ui.hideUiPage(signInPage)
            }
            ui.showUiPage(tokenPage)
        }

        if (lastUrl.isNotBlank() && lastToken.isNotBlank()) {
            try {
                authenticate(URL(lastUrl), lastToken)
            } catch (ex: Exception) {
                signInPage.notify("Unable to sign in", ex)
            }
        }
    }

    private fun authenticate(url: URL, token: String) {
        // TODO: How do we pull proxy settings?  Or are they already applied
        //       since we inherit the client?
        // TODO: How do we pull the plugin version?
        val client = CoderRestClient(url, token, settings, null, "production", httpClient)
        client.authenticate()
        val cli = CoderCLIManager(client.url)
        cli.login(token)
        this.client = client
        lastEnvironments = emptyList()
        job?.cancel()
        job = poll(client)
    }

    private fun poll(client: CoderRestClient): Job {
        return coroutineScope.launch {
            while (true) {
                try {
                    logger.debug("Fetching workspace agents from {}", client.url)
                    val environments = client.workspaces().flatMap { ws ->
                        // Agents are not included in workspaces that are off
                        // so fetch them separately.
                        when (ws.latestBuild.status) {
                            WorkspaceStatus.RUNNING -> ws.latestBuild.resources
                            else -> emptyList()
                        }.ifEmpty {
                            client.resources(ws)
                        }.flatMap { resource ->
                            resource.agents?.distinctBy {
                                // There can be duplicates with coder_agent_instance.
                                // TODO: Can we just choose one or do they hold
                                //       different information?
                                it.name
                            }?.map {
                                    agent -> CoderRemoteEnvironment(client.url, ws, agent)
                            } ?: emptyList()
                        }
                    }

                    // Reconfigure if a new environment is found.
                    val newEnvironments = environments.filter { a -> !lastEnvironments.any { b -> a.id == b.id } }
                    if (newEnvironments.isNotEmpty()) {
                        logger.debug("Found new environment(s), reconfiguring CLI: {}", newEnvironments.map { it.name })
                        val cli = CoderCLIManager(client.url)
                        cli.configSsh(environments.map { it.name }.toSet())
                    }

                    consumer.consumeEnvironments(environments)

                    lastEnvironments = environments
                } catch (_: CancellationException) {
                    logger.debug("{} polling loop canceled", client.url)
                    break
                } catch (ex: Exception) {
                    logger.error("Failed to fetch workspace agents", ex)
                    // TODO: Log in again only if 401.
                    //       Show the error on the page otherwise.
                    //       Depending on the error keep retrying or show a retry button.
                    logout("Failed to fetch workspace agents", ex)
                    break
                }
                // TODO: Listening on a web socket might be better?
                delay(5.seconds)
            }
        }
    }

    private fun logout(message: String? = null, ex: Exception? = null) {
        close()
        secrets.url = ""
        secrets.token = ""
        message?.let { signInPage.notify(it, ex) }
    }

    override fun getAccountDropDown(): AccountDropdownField? {
        val username = client?.me?.username
        if (username != null) {
            return AccountDropdownField(username) {
                logout()
            }
        }
        return null
    }

    override fun getAdditionalPluginActions(): List<RunnableActionDescription>  {
        return listOf(Action("Settings", false) {
            ui.showUiPage(settingsPage)
        })
    }

    override fun close() {
        job?.cancel()
        client = null
        lastEnvironments = emptyList()
        consumer.consumeEnvironments(emptyList())
    }

    override fun getName(): String = "Coder Gateway"
    override fun getSvgIcon(): ByteArray {
        return this::class.java.getResourceAsStream("/icon.svg")?.readAllBytes() ?: byteArrayOf()
    }

    override fun getNoEnvironmentsSvgIcon(): ByteArray {
        return this::class.java.getResourceAsStream("/icon.svg")?.readAllBytes() ?: byteArrayOf()
    }

    override fun getNoEnvironmentsDescription(): String = "No environments"

    override fun canCreateNewEnvironments(): Boolean = true
    override fun isSingleEnvironment(): Boolean = false

    override fun setVisible(visibilityState: ProviderVisibilityState) {}

    override fun addEnvironmentsListener(listener: RemoteEnvironmentConsumer) {}
    override fun removeEnvironmentsListener(listener: RemoteEnvironmentConsumer) {}

    override fun handleUri(uri: URI) {
        // TODO: Extract logic from original plugin then import that here.
        logger.debug("External request: {}", uri)
    }

    override fun getOverrideUiPage(): UiPage? {
        // Show sign in page if we have not configured the client yet.
        if (client == null) {
            return signInPage
        }
        // TODO: The environments page shows a "blank page" section.  How to get
        //       rid of that?  Or even better, how to use that for logging in?
        return null
    }
}

/**
 * A page with a field for specifying the deployment URL.
 */
class SignInPage(url: String) : CoderPage() {
    val urlField = TextField("Deployment URL", url, TextType.General)
    var onSignIn: ((url: URL) -> Unit)? = null

    override fun getFields(): MutableList<UiField> {
        return mutableListOf(urlField)
    }

    override fun getActionButtons(): MutableList<RunnableActionDescription> {
        return mutableListOf(Action("Sign In", false) {
            try {
                val url = get(urlField) as String
                onSignIn?.invoke(URL(url))
            } catch (ex: Exception) {
                notify("Invalid deployment URL", ex)
            }
        })
    }

    override fun getTitle(): String = "Sign in to Coder"
}

/**
 * A page with a field for specifying the token.
 *
 * TODO: Would prefer to use a popup maybe, but there is no way to add a link?
 * TODO: Would be neat to get the token automatically (launch a URL then have
 *       the deployment redirect back to the plugin with a token).
 */
class TokenPage(private val deploymentUrl: URL, token: String) : CoderPage() {
    val tokenField = TextField("Token", token, TextType.Password)
    var onSignIn: ((token: String) -> Unit)? = null

    override fun getFields(): MutableList<UiField> {
        return mutableListOf(
            tokenField,
            // TODO: This displays the link text twice.  Toolbox bug?
            LinkField("Get token", deploymentUrl.withPath("/cli-auth").toString()))
    }

    override fun getActionButtons(): MutableList<RunnableActionDescription> {
        return mutableListOf(Action("Sign In", false) {
            try {
                val token = get(tokenField) as String
                if (token.isBlank()) {
                    throw Exception("Token is blank")
                }
                onSignIn?.invoke(token)
            } catch (ex: Exception) {
                notify("Unable to sign in", ex)
            }
        })
    }

    override fun getTitle(): String = "Sign in to $deploymentUrl"
}

/**
 * Page for modifying Coder settings.
 *
 * TODO: Even without an icon there is an unnecessary gap at the top.
 * TODO: There is no scroll, and our settings do not fit.  Maybe there is some
 *       scroll component we should be using?
 */
class CoderSettingsPage(private val settings: CoderSettingsService): CoderPage(false) {
    // TODO: Descriptions?
    // TODO: Check that labels and order match the old plugin.
    // TODO: Anything like bindText that we can use?
    private val binarySourceField = TextField("Binary source", settings.binarySource, TextType.General)
    private val binaryDirectoryField = TextField("Binary directory", settings.binaryDirectory, TextType.General)
    private val dataDirectoryField = TextField("Data directory", settings.dataDirectory, TextType.General)
    private val enableDownloadsField = CheckboxField(settings.enableDownloads, "Enable downloads")
    private val enableBinaryDirectoryFallbackField = CheckboxField(settings.enableBinaryDirectoryFallback, "Enable binary directory fallback")
    private val headerCommandField = TextField("Header command", settings.headerCommand, TextType.General)
    private val tlsCertPathField = TextField("TLS cert path", settings.tlsCertPath, TextType.General)
    private val tlsKeyPathField = TextField("TLS key path", settings.tlsKeyPath, TextType.General)
    private val tlsCAPathField = TextField("TLS CA path", settings.tlsCAPath, TextType.General)
    private val tlsAlternateHostnameField = TextField("TLS alternate hostname", settings.tlsAlternateHostname, TextType.General)
    private val disableAutostartField = CheckboxField(settings.disableAutostart, "Disable autostart")

    override fun getFields(): MutableList<UiField> {
        return mutableListOf(
            binarySourceField,
            enableDownloadsField,
            binaryDirectoryField,
            enableBinaryDirectoryFallbackField,
            dataDirectoryField,
            headerCommandField,
            tlsCertPathField,
            tlsKeyPathField,
            tlsCAPathField,
            tlsAlternateHostnameField,
            disableAutostartField)
    }

    override fun getTitle(): String = "Coder Settings"

    override fun getActionButtons(): MutableList<RunnableActionDescription> {
        return mutableListOf(Action("Save", true) {
            settings.binarySource = get(binarySourceField) as String
            settings.binaryDirectory = get(binaryDirectoryField) as String
            settings.dataDirectory = get(dataDirectoryField) as String
            settings.enableDownloads = get(enableDownloadsField) as Boolean
            settings.enableBinaryDirectoryFallback = get(enableBinaryDirectoryFallbackField) as Boolean
            settings.headerCommand = get(headerCommandField) as String
            settings.tlsCertPath = get(tlsCertPathField) as String
            settings.tlsKeyPath = get(tlsKeyPathField) as String
            settings.tlsCAPath = get(tlsCAPathField) as String
            settings.tlsAlternateHostname = get(tlsAlternateHostnameField) as String
            settings.disableAutostart = get(disableAutostartField) as Boolean
        })
    }
}

/**
 * Base page that handles basic styling, displaying error notifications, and
 * getting state.
 *
 * TODO: Any way to get the return key working for fields?  Right now you have
 *       to use the mouse.
 */
abstract class CoderPage(
    private val showIcon: Boolean = false,
) : UiPage {
    private val logger = LoggerFactory.getLogger(javaClass)
    private var notifier: Consumer<String>? = null
    private var getter: Function<UiField, *>? = null
    private var lastMessage: String? = null

    override fun getSvgIcon(): ByteArray {
        return if (showIcon) {
            this::class.java.getResourceAsStream("/icon.svg")?.readAllBytes() ?: byteArrayOf()
        } else {
            byteArrayOf()
        }
    }

    fun notify(prefix: String, ex: Exception? = null) {
        logger.error(prefix, ex)
        val notifier = notifier
        val message = if (ex == null) prefix else "$prefix $ex"
        // It is possible the error listener is not attached yet.
        if (notifier == null) {
            lastMessage = message
        } else {
            notifier.accept(message)
        }
    }

    // TODO: Is this really meant to be used with casting?  I kind of expected
    //       to be able to do `this.myField.value`.
    fun get(field: UiField): Any {
        val getter = getter ?: throw Exception("Page is not being displayed")
        return getter.apply(field)
    }

    override fun setStateAccessor(setter: BiConsumer<UiField, Any>?, getter: Function<UiField, *>?) {
        this.getter = getter
    }

    override fun close(stateMap: MutableMap<UiField, *>) {
        this.notifier = null
    }

    override fun setActionErrorNotifier(notifier: Consumer<String>?) {
        this.notifier = notifier
        lastMessage?.let {
            if (notifier != null) {
                notifier.accept(it)
                lastMessage = null
            }
        }
    }
}

/**
 * An action that simply runs the provided callback.
 */
class Action(
    private val label: String,
    private val closesPage: Boolean,
    private val cb: () -> Unit,
) : RunnableActionDescription {
    override fun getLabel(): String = label
    override fun getShouldClosePage(): Boolean = closesPage
    override fun run() { cb() }
}
