package com.coder.gateway.views

import com.coder.gateway.cli.CoderCLIManager
import com.coder.gateway.cli.ensureCLI
import com.coder.gateway.sdk.CoderRestClient
import com.coder.gateway.settings.CoderSettings
import com.coder.gateway.util.humanizeConnectionError
import com.jetbrains.toolbox.api.ui.actions.RunnableActionDescription
import com.jetbrains.toolbox.api.ui.components.LabelField
import com.jetbrains.toolbox.api.ui.components.UiField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.net.URL

/**
 * A page that connects a REST client and cli to Coder.
 */
class ConnectPage(
    private val url: URL,
    private val token: String?,
    private val settings: CoderSettings,
    private val httpClient: OkHttpClient,
    private val coroutineScope: CoroutineScope,
    private val onCancel: () -> Unit,
    private val onConnect: (
        client: CoderRestClient,
        cli: CoderCLIManager,
    ) -> Unit,
) : CoderPage() {
    private var signInJob: Job? = null

    private var statusField = LabelField("Connecting to ${url.host}...")

    override fun getTitle(): String = "Connecting to Coder"
    override fun getDescription(): String = "Please wait while we configure Toolbox for ${url.host}."

    init {
        connect()
    }

    /**
     * Fields for this page, displayed in order.
     *
     * TODO@JB: This looks kinda sparse.  A centered spinner would be welcome.
     */
    override fun getFields(): MutableList<UiField> = listOfNotNull(
        statusField,
        errorField,
    ).toMutableList()

    /**
     * Show a retry button on error.
     */
    override fun getActionButtons(): MutableList<RunnableActionDescription> = listOfNotNull(
        if (errorField != null) Action("Retry", closesPage = false) { retry() } else null,
        if (errorField != null) Action("Cancel", closesPage = false) { onCancel() } else null,
    ).toMutableList()

    /**
     * Update the status and error fields then refresh.
     */
    private fun updateStatus(newStatus: String, error: String?) {
        statusField = LabelField(newStatus)
        updateError(error) // Will refresh.
    }

    /**
     * Try connecting again after an error.
     */
    private fun retry() {
        updateStatus("Connecting to ${url.host}...", null)
        connect()
    }

    /**
     * Try connecting to Coder with the provided URL and token.
     */
    private fun connect() {
        signInJob?.cancel()
        signInJob = coroutineScope.launch {
            try {
                // The http client Toolbox gives us is already set up with the
                // proxy config, so we do net need to explicitly add it.
                // TODO: How to get the plugin version?
                val client = CoderRestClient(url, token, settings, proxyValues = null, "production", httpClient)
                client.authenticate()
                updateStatus("Checking Coder binary...", error = null)
                val cli = ensureCLI(client.url, client.buildVersion, settings) { status ->
                    updateStatus(status, error = null)
                }
                // We only need to log in if we are using token-based auth.
                if (client.token != null) {
                    updateStatus("Configuring CLI...", error = null)
                    cli.login(client.token)
                }
                onConnect(client, cli)
            } catch (ex: Exception) {
                val msg = humanizeConnectionError(url, settings.requireTokenAuth, ex)
                notify("Failed to configure ${url.host}", ex)
                updateStatus("Failed to configure ${url.host}", msg)
            }
        }
    }
}
