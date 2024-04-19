@file:Suppress("DialogTitleCapitalization")

package com.coder.gateway

import com.coder.gateway.cli.CoderCLIManager
import com.coder.gateway.cli.ensureCLI
import com.coder.gateway.models.AGENT_ID
import com.coder.gateway.models.AGENT_NAME
import com.coder.gateway.models.TOKEN
import com.coder.gateway.models.TokenSource
import com.coder.gateway.models.URL
import com.coder.gateway.models.WORKSPACE
import com.coder.gateway.models.WorkspaceAndAgentStatus
import com.coder.gateway.models.WorkspaceProjectIDE
import com.coder.gateway.models.agentID
import com.coder.gateway.models.agentName
import com.coder.gateway.models.folder
import com.coder.gateway.models.ideBuildNumber
import com.coder.gateway.models.ideDownloadLink
import com.coder.gateway.models.idePathOnHost
import com.coder.gateway.models.ideProductCode
import com.coder.gateway.models.isCoder
import com.coder.gateway.models.token
import com.coder.gateway.models.url
import com.coder.gateway.models.workspace
import com.coder.gateway.sdk.CoderRestClient
import com.coder.gateway.sdk.ex.AuthenticationResponseException
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceAgent
import com.coder.gateway.sdk.v2.models.WorkspaceStatus
import com.coder.gateway.services.CoderRestClientService
import com.coder.gateway.services.CoderSettingsService
import com.coder.gateway.util.toURL
import com.coder.gateway.util.withPath
import com.coder.gateway.views.steps.CoderWorkspaceProjectIDEStepView
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
import javax.swing.JComponent
import javax.swing.border.Border

/**
 * A dialog wrapper around CoderWorkspaceStepView.
 */
class CoderWorkspaceStepDialog(
    name: String,
    private val state: CoderWorkspacesStepSelection,
) : DialogWrapper(true) {
    private val view = CoderWorkspaceProjectIDEStepView(showTitle = false)

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

    fun showAndGetData(): WorkspaceProjectIDE? {
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

            val deploymentURL = parameters.url()
                ?: CoderRemoteConnectionHandle.ask("Enter the full URL of your Coder deployment")
            if (deploymentURL.isNullOrBlank()) {
                throw IllegalArgumentException("Query parameter \"$URL\" is missing")
            }

            val (client, username) = authenticate(deploymentURL, parameters.token())

            // TODO: If the workspace is missing we could launch the wizard.
            val workspaceName = parameters.workspace() ?: throw IllegalArgumentException("Query parameter \"$WORKSPACE\" is missing")

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

            // We only need to log in if we are using token-based auth.
            if (client.token !== null) {
                indicator.text = "Authenticating Coder CLI..."
                cli.login(client.token)
            }

            indicator.text = "Configuring Coder CLI..."
            cli.configSsh(client.agentNames(workspaces))

            val name = "${workspace.name}.${agent.name}"
            val openDialog = parameters.ideProductCode().isNullOrBlank() ||
                    parameters.ideBuildNumber().isNullOrBlank() ||
                    (parameters.idePathOnHost().isNullOrBlank() && parameters.ideDownloadLink().isNullOrBlank()) ||
                    parameters.folder().isNullOrBlank()

            if (openDialog) {
                var data: WorkspaceProjectIDE? = null
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
                WorkspaceProjectIDE.fromInputs(
                    name = name,
                    hostname = CoderCLIManager.getHostName(deploymentURL.toURL(), name),
                    projectPath = parameters.folder(),
                    ideProductCode = parameters.ideProductCode(),
                    ideBuildNumber = parameters.ideBuildNumber(),
                    webTerminalLink = client.url.withPath("/@$username/$workspace.name/terminal").toString(),
                    configDirectory = cli.coderConfigPath.toString(),
                    idePathOnHost = parameters.idePathOnHost(),
                    downloadSource = parameters.ideDownloadLink(),
                    lastOpened = null, // Have not opened yet.
                )
            }
        }
        return null
    }

    /**
     * Return an authenticated Coder CLI and the user's name, asking for the
     * token as long as it continues to result in an authentication failure.
     */
    private fun authenticate(deploymentURL: String, queryToken: String?, lastToken: Pair<String, TokenSource>? = null): Pair<CoderRestClient, String> {
        // Use the token from the query, unless we already tried that.
        val isRetry = lastToken != null
        val token = if (!queryToken.isNullOrBlank() && !isRetry)
            Pair(queryToken, TokenSource.QUERY)
        else CoderRemoteConnectionHandle.askToken(
            deploymentURL.toURL(),
            lastToken,
            isRetry,
            useExisting = true,
            settings,
        )
        if (token == null) { // User aborted.
            throw IllegalArgumentException("Unable to connect to $deploymentURL, query parameter \"$TOKEN\" is missing")
        }
        val client = CoderRestClientService(deploymentURL.toURL(), token.first)
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
        val link = parameters.ideDownloadLink()
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
        return parameters.isCoder()
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
    val agent = if (!parameters.agentID().isNullOrBlank())
        agents.firstOrNull { it.id.toString() == parameters.agentID() }
    else if (!parameters.agentName().isNullOrBlank())
        agents.firstOrNull { it.name == parameters.agentName()}
    else if (agents.size == 1) agents.first()
    else null

    if (agent == null) {
        if (!parameters.agentID().isNullOrBlank()) {
            throw IllegalArgumentException("The workspace \"${workspace.name}\" does not have an agent with ID \"${parameters.agentID()}\"")
        } else if (!parameters.agentName().isNullOrBlank()){
            throw IllegalArgumentException("The workspace \"${workspace.name}\"does not have an agent named \"${parameters.agentName()}\"")
        } else {
            throw MissingArgumentException("Unable to determine which agent to connect to; one of \"$AGENT_NAME\" or \"$AGENT_ID\" must be set because the workspace \"${workspace.name}\" has more than one agent")
        }
    }

    return agent
}

class MissingArgumentException(message: String) : IllegalArgumentException(message)
