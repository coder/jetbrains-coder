@file:Suppress("DialogTitleCapitalization")

package com.coder.gateway

import com.coder.gateway.cli.CoderCLIManager
import com.coder.gateway.cli.ensureCLI
import com.coder.gateway.models.TokenSource
import com.coder.gateway.models.WorkspaceAndAgentStatus
import com.coder.gateway.sdk.CoderRestClient
import com.coder.gateway.sdk.ex.AuthenticationResponseException
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceAgent
import com.coder.gateway.sdk.v2.models.WorkspaceStatus
import com.coder.gateway.services.CoderRestClientService
import com.coder.gateway.services.CoderSettingsService
import com.coder.gateway.util.toURL
import com.coder.gateway.util.withPath
import com.coder.gateway.views.steps.CoderWorkspaceStepView
import com.coder.gateway.views.steps.CoderWorkspacesStepSelection
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.jetbrains.gateway.api.ConnectionRequestor
import com.jetbrains.gateway.api.GatewayConnectionHandle
import com.jetbrains.gateway.api.GatewayConnectionProvider
import java.net.URL
import javax.swing.JComponent
import javax.swing.border.Border

// In addition to `type`, these are the keys that we support in our Gateway
// links.
private const val URL = "url"
private const val TOKEN = "token"
private const val WORKSPACE = "workspace"
private const val AGENT_NAME = "agent"
private const val AGENT_ID = "agent_id"
private const val FOLDER = "folder"
private const val IDE_DOWNLOAD_LINK = "ide_download_link"
private const val IDE_PRODUCT_CODE = "ide_product_code"
private const val IDE_BUILD_NUMBER = "ide_build_number"
private const val IDE_PATH_ON_HOST = "ide_path_on_host"

/**
 * A dialog wrapper around CoderWorkspaceStepView.
 */
class CoderWorkspaceStepDialog(
    name: String,
    private val state: CoderWorkspacesStepSelection,
) : DialogWrapper(true) {
    private val view = CoderWorkspaceStepView(showTitle = false)

    init {
        init()
        title = CoderGatewayBundle.message("gateway.connector.view.coder.remoteproject.choose.text", name)
    }

    override fun show() {
        view.init(state)
        view.onPrevious = { close(1) }
        view.onNext = { close(0) }
        super.show()
        view.dispose()
    }

    fun showAndGetData(): Map<String, String>? {
        if (showAndGet()) {
            return view.data()
        }
        return null
    }

    override fun createContentPaneBorder(): Border {
        return JBUI.Borders.empty()
    }

    override fun createCenterPanel(): JComponent {
        return view
    }

    override fun createSouthPanel(): JComponent {
        // The plugin provides its own buttons.
        // TODO: Is it more idiomatic to handle buttons out here?
        return panel{}.apply {
            border = JBUI.Borders.empty()
        }
    }
}

// CoderGatewayConnectionProvider handles connecting via a Gateway link such as
// jetbrains-gateway://connect#type=coder.
class CoderGatewayConnectionProvider : GatewayConnectionProvider {
    private val settings: CoderSettingsService = service<CoderSettingsService>()

    override suspend fun connect(parameters: Map<String, String>, requestor: ConnectionRequestor): GatewayConnectionHandle? {
        CoderRemoteConnectionHandle().connect{ indicator ->
            logger.debug("Launched Coder connection provider", parameters)

            val deploymentURL = parameters[URL]
                ?: CoderRemoteConnectionHandle.ask("Enter the full URL of your Coder deployment")
            if (deploymentURL.isNullOrBlank()) {
                throw IllegalArgumentException("Query parameter \"$URL\" is missing")
            }

            val (client, username) = authenticate(deploymentURL.toURL(), parameters[TOKEN])

            // TODO: If the workspace is missing we could launch the wizard.
            val workspaceName = parameters[WORKSPACE] ?: throw IllegalArgumentException("Query parameter \"$WORKSPACE\" is missing")

            val workspaces = client.workspaces()
            val workspace = workspaces.firstOrNull{ it.name == workspaceName } ?: throw IllegalArgumentException("The workspace $workspaceName does not exist")

            when (workspace.latestBuild.status) {
                WorkspaceStatus.PENDING, WorkspaceStatus.STARTING ->
                    // TODO: Wait for the workspace to turn on.
                    throw IllegalArgumentException("The workspace \"$workspaceName\" is ${workspace.latestBuild.status.toString().lowercase()}; please wait then try again")
                WorkspaceStatus.STOPPING, WorkspaceStatus.STOPPED,
                WorkspaceStatus.CANCELING, WorkspaceStatus.CANCELED ->
                    // TODO: Turn on the workspace.
                    throw IllegalArgumentException("The workspace \"$workspaceName\" is ${workspace.latestBuild.status.toString().lowercase()}; please start the workspace and try again")
                WorkspaceStatus.FAILED, WorkspaceStatus.DELETING, WorkspaceStatus.DELETED ->
                    throw IllegalArgumentException("The workspace \"$workspaceName\" is ${workspace.latestBuild.status.toString().lowercase()}; unable to connect")
                WorkspaceStatus.RUNNING -> Unit // All is well
            }

            // TODO: Show a dropdown and ask for an agent if missing.
            val agent = getMatchingAgent(parameters, workspace)
            val status = WorkspaceAndAgentStatus.from(workspace, agent)

            if (status.pending()) {
                // TODO: Wait for the agent to be ready.
                throw IllegalArgumentException("The agent \"${agent.name}\" is ${status.toString().lowercase()}; please wait then try again")
            } else if (!status.ready()) {
                throw IllegalArgumentException("The agent \"${agent.name}\" is ${status.toString().lowercase()}; unable to connect")
            }

            val cli = ensureCLI(
                deploymentURL.toURL(),
                client.buildInfo().version,
                settings,
                indicator,
            )

            indicator.text = "Authenticating Coder CLI..."
            cli.login(client.token)

            indicator.text = "Configuring Coder CLI..."
            cli.configSsh(client.agentNames(workspaces))

            val name = "${workspace.name}.${agent.name}"
            val openDialog = parameters[IDE_PRODUCT_CODE].isNullOrBlank() ||
                    parameters[IDE_BUILD_NUMBER].isNullOrBlank() ||
                    (parameters[IDE_PATH_ON_HOST].isNullOrBlank() && parameters[IDE_DOWNLOAD_LINK].isNullOrBlank()) ||
                    parameters[FOLDER].isNullOrBlank()

            if (openDialog) {
                var data: Map<String, String>? = null
                ApplicationManager.getApplication().invokeAndWait {
                    val dialog = CoderWorkspaceStepDialog(name,
                        CoderWorkspacesStepSelection(agent, workspace, cli, client, workspaces))
                    data = dialog.showAndGetData()
                }
                data ?: throw Exception("IDE selection aborted; unable to connect")
            } else {
                // Check that both the domain and the redirected domain are
                // allowlisted.  If not, check with the user whether to proceed.
                verifyDownloadLink(parameters)

                parameters
                    .withWorkspaceHostname(CoderCLIManager.getHostName(deploymentURL.toURL(), name))
                    .withProjectPath(parameters[FOLDER]!!)
                    .withWebTerminalLink(client.url.withPath("/@$username/$workspace.name/terminal").toString())
                    .withConfigDirectory(cli.coderConfigPath.toString())
                    .withName(name)
            }
        }
        return null
    }

    /**
     * Return an authenticated Coder CLI and the user's name, asking for the
     * token as long as it continues to result in an authentication failure.
     */
    private fun authenticate(deploymentURL: URL, queryToken: String?, lastToken: Pair<String, TokenSource>? = null): Pair<CoderRestClient, String> {
        // Use the token from the query, unless we already tried that.
        val isRetry = lastToken != null
        val token = if (!queryToken.isNullOrBlank() && !isRetry)
            Pair(queryToken, TokenSource.QUERY)
        else CoderRemoteConnectionHandle.askToken(
            deploymentURL,
            lastToken,
            isRetry,
            useExisting = true,
            settings,
        )
        if (token == null) { // User aborted.
            throw IllegalArgumentException("Unable to connect to $deploymentURL, $TOKEN is missing")
        }
        val client = CoderRestClientService(deploymentURL, token.first)
        return try {
            Pair(client, client.me().username)
        } catch (ex: AuthenticationResponseException) {
            authenticate(deploymentURL, queryToken, token)
        }
    }

    /**
     * Check that the link is allowlisted.  If not, confirm with the user.
     */
    private fun verifyDownloadLink(parameters: Map<String, String>) {
        val link = parameters[IDE_DOWNLOAD_LINK]
        if (link.isNullOrBlank()) {
            return // Nothing to verify
        }

        val url = try {
            link.toURL()
        } catch (ex: Exception) {
            throw IllegalArgumentException("$link is not a valid URL")
        }

        val (allowlisted, https, linkWithRedirect) = try {
            CoderRemoteConnectionHandle.isAllowlisted(url)
        } catch (e: Exception) {
            throw IllegalArgumentException("Unable to verify $url: $e")
        }
        if (allowlisted && https) {
            return
        }

        val comment = if (allowlisted) "The download link is from a non-allowlisted URL"
        else if (https) "The download link is not using HTTPS"
        else "The download link is from a non-allowlisted URL and is not using HTTPS"

        if (!CoderRemoteConnectionHandle.confirm(
                "Confirm download URL",
                "$comment. Would you like to proceed?",
                linkWithRedirect,
            )) {
            throw IllegalArgumentException("$linkWithRedirect is not allowlisted")
        }
    }

    override fun isApplicable(parameters: Map<String, String>): Boolean {
        return parameters.areCoderType()
    }

    companion object {
        val logger = Logger.getInstance(CoderGatewayConnectionProvider::class.java.simpleName)
    }
}

/**
 * Return the agent matching the provided agent ID or name in the parameters.
 * The name is ignored if the ID is set.  If neither was supplied and the
 * workspace has only one agent, return that.  Otherwise throw an error.
 *
 * @throws [MissingArgumentException, IllegalArgumentException]
 */
fun getMatchingAgent(parameters: Map<String, String?>, workspace: Workspace): WorkspaceAgent {
    val agents = workspace.latestBuild.resources.filter { it.agents != null }.flatMap { it.agents!! }
    if (agents.isEmpty()) {
        throw IllegalArgumentException("The workspace \"${workspace.name}\" has no agents")
    }

    // If the agent is missing and the workspace has only one, use that.
    // Prefer the ID over the name if both are set.
    val agent = if (!parameters[AGENT_ID].isNullOrBlank())
        agents.firstOrNull { it.id.toString() == parameters[AGENT_ID] }
    else if (!parameters[AGENT_NAME].isNullOrBlank())
        agents.firstOrNull { it.name == parameters[AGENT_NAME]}
    else if (agents.size == 1) agents.first()
    else null

    if (agent == null) {
        if (!parameters[AGENT_ID].isNullOrBlank()) {
            throw IllegalArgumentException("The workspace \"${workspace.name}\" does not have an agent with ID \"${parameters[AGENT_ID]}\"")
        } else if (!parameters[AGENT_NAME].isNullOrBlank()){
            throw IllegalArgumentException("The workspace \"${workspace.name}\"does not have an agent named \"${parameters[AGENT_NAME]}\"")
        } else {
            throw MissingArgumentException("Unable to determine which agent to connect to; one of \"$AGENT_NAME\" or \"$AGENT_ID\" must be set because the workspace \"${workspace.name}\" has more than one agent")
        }
    }

    return agent
}

class MissingArgumentException(message: String) : IllegalArgumentException(message)
