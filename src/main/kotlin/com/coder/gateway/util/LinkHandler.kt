package com.coder.gateway.util

import com.coder.gateway.cli.CoderCLIManager
import com.coder.gateway.cli.ensureCLI
import com.coder.gateway.models.WorkspaceAndAgentStatus
import com.coder.gateway.models.WorkspaceProjectIDE
import com.coder.gateway.sdk.CoderRestClient
import com.coder.gateway.sdk.ex.APIResponseException
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceAgent
import com.coder.gateway.sdk.v2.models.WorkspaceStatus
import com.coder.gateway.services.CoderRestClientService
import com.coder.gateway.settings.CoderSettings
import com.coder.gateway.settings.Source
import okhttp3.OkHttpClient
import java.net.HttpURLConnection
import java.net.URL

open class LinkHandler(
    private val settings: CoderSettings,
    private val httpClient: OkHttpClient?,
    private val dialogUi: DialogUi,
) {
    /**
     * Given a set of URL parameters, prepare the CLI then return a workspace to
     * connect.
     *
     * Throw if required arguments are not supplied or the workspace is not in a
     * connectable state.
     */
    fun handle(
        parameters: Map<String, String>,
        indicator: ((t: String) -> Unit)? = null,
    ): WorkspaceProjectIDE {
        val deploymentURL = parameters.url() ?: dialogUi.ask("Deployment URL", "Enter the full URL of your Coder deployment")
        if (deploymentURL.isNullOrBlank()) {
            throw MissingArgumentException("Query parameter \"$URL\" is missing")
        }

        val queryTokenRaw = parameters.token()
        val queryToken = if (!queryTokenRaw.isNullOrBlank()) {
            Pair(queryTokenRaw, Source.QUERY)
        } else {
            null
        }
        val client = try {
            authenticate(deploymentURL, queryToken)
        } catch (ex: MissingArgumentException) {
            throw MissingArgumentException("Query parameter \"$TOKEN\" is missing")
        }

        // TODO: Show a dropdown and ask for the workspace if missing.
        val workspaceName = parameters.workspace() ?: throw MissingArgumentException("Query parameter \"$WORKSPACE\" is missing")

        // The owner was added to support getting into another user's workspace
        // but may not exist if the Coder Gateway module is out of date.  If no
        // owner is included, assume the current user.
        val owner = (parameters.owner() ?: client.me.username).ifBlank { client.me.username }

        val cli =
            ensureCLI(
                deploymentURL.toURL(),
                client.buildInfo().version,
                settings,
                indicator,
            )

        var workspace : Workspace
        var workspaces : List<Workspace> = emptyList()
        var workspacesAndAgents : Set<Pair<Workspace, WorkspaceAgent>> = emptySet()
        if (cli.features.wildcardSSH) {
            workspace = client.workspaceByOwnerAndName(owner, workspaceName)
        } else {
            workspaces = client.workspaces()
            workspace =
                workspaces.firstOrNull {
                    it.ownerName == owner && it.name == workspaceName
                } ?: throw IllegalArgumentException("The workspace $workspaceName does not exist")
            workspacesAndAgents = client.withAgents(workspaces)
        }

        when (workspace.latestBuild.status) {
            WorkspaceStatus.PENDING, WorkspaceStatus.STARTING ->
                // TODO: Wait for the workspace to turn on.
                throw IllegalArgumentException(
                    "The workspace \"$workspaceName\" is ${workspace.latestBuild.status.toString().lowercase()}; please wait then try again",
                )
            WorkspaceStatus.STOPPING, WorkspaceStatus.STOPPED,
            WorkspaceStatus.CANCELING, WorkspaceStatus.CANCELED,
            ->
                // TODO: Turn on the workspace.
                throw IllegalArgumentException(
                    "The workspace \"$workspaceName\" is ${workspace.latestBuild.status.toString().lowercase()}; please start the workspace and try again",
                )
            WorkspaceStatus.FAILED, WorkspaceStatus.DELETING, WorkspaceStatus.DELETED ->
                throw IllegalArgumentException(
                    "The workspace \"$workspaceName\" is ${workspace.latestBuild.status.toString().lowercase()}; unable to connect",
                )
            WorkspaceStatus.RUNNING -> Unit // All is well
        }

        // TODO: Show a dropdown and ask for an agent if missing.
        val agent = getMatchingAgent(parameters, workspace)
        val status = WorkspaceAndAgentStatus.from(workspace, agent)

        if (status.pending()) {
            // TODO: Wait for the agent to be ready.
            throw IllegalArgumentException(
                "The agent \"${agent.name}\" has a status of \"${status.toString().lowercase()}\"; please wait then try again",
            )
        } else if (!status.ready()) {
            throw IllegalArgumentException("The agent \"${agent.name}\" has a status of \"${status.toString().lowercase()}\"; unable to connect")
        }

        // We only need to log in if we are using token-based auth.
        if (client.token != null) {
            indicator?.invoke("Authenticating Coder CLI...")
            cli.login(client.token)
        }

        indicator?.invoke("Configuring Coder CLI...")
        cli.configSsh(workspacesAndAgents, currentUser = client.me)

        val openDialog =
            parameters.ideProductCode().isNullOrBlank() ||
                parameters.ideBuildNumber().isNullOrBlank() ||
                (parameters.idePathOnHost().isNullOrBlank() && parameters.ideDownloadLink().isNullOrBlank()) ||
                parameters.folder().isNullOrBlank()

        return if (openDialog) {
            askIDE(agent, workspace, cli, client, workspaces) ?: throw MissingArgumentException("IDE selection aborted; unable to connect")
        } else {
            // Check that both the domain and the redirected domain are
            // allowlisted.  If not, check with the user whether to proceed.
            verifyDownloadLink(parameters)
            WorkspaceProjectIDE.fromInputs(
                name = CoderCLIManager.getWorkspaceParts(workspace, agent),
                hostname = CoderCLIManager(deploymentURL.toURL(), settings).getHostName(workspace, client.me, agent),
                projectPath = parameters.folder(),
                ideProductCode = parameters.ideProductCode(),
                ideBuildNumber = parameters.ideBuildNumber(),
                idePathOnHost = parameters.idePathOnHost(),
                downloadSource = parameters.ideDownloadLink(),
                deploymentURL = deploymentURL,
                lastOpened = null, // Have not opened yet.
            )
        }
    }

    /**
     * Return an authenticated Coder CLI, asking for the token as long as it
     * continues to result in an authentication failure and token authentication
     * is required.
     *
     * Throw MissingArgumentException if the user aborts.  Any network or invalid
     * token error may also be thrown.
     */
    private fun authenticate(
        deploymentURL: String,
        tryToken: Pair<String, Source>?,
        error: String? = null,
    ): CoderRestClient {
        val token =
            if (settings.requireTokenAuth) {
                // Try the provided token immediately on the first attempt.
                if (tryToken != null && error == null) {
                    tryToken
                } else {
                    // Otherwise ask for a new token, showing the previous token.
                    dialogUi.askToken(
                        deploymentURL.toURL(),
                        tryToken,
                        useExisting = true,
                        error,
                    )
                }
            } else {
                null
            }
        if (settings.requireTokenAuth && token == null) { // User aborted.
            throw MissingArgumentException("Token is required")
        }
        val client = CoderRestClientService(deploymentURL.toURL(), token?.first, httpClient = httpClient)
        return try {
            client.authenticate()
            client
        } catch (ex: APIResponseException) {
            // If doing token auth we can ask and try again.
            if (settings.requireTokenAuth && ex.isUnauthorized) {
                val msg = humanizeConnectionError(client.url, true, ex)
                authenticate(deploymentURL, token, msg)
            } else {
                throw ex
            }
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

        val url =
            try {
                link.toURL()
            } catch (ex: Exception) {
                throw IllegalArgumentException("$link is not a valid URL")
            }

        val (allowlisted, https, linkWithRedirect) =
            try {
                isAllowlisted(url)
            } catch (e: Exception) {
                throw IllegalArgumentException("Unable to verify $url: $e")
            }
        if (allowlisted && https) {
            return
        }

        val comment =
            if (allowlisted) {
                "The download link is from a non-allowlisted URL"
            } else if (https) {
                "The download link is not using HTTPS"
            } else {
                "The download link is from a non-allowlisted URL and is not using HTTPS"
            }

        if (!dialogUi.confirm(
                "Confirm download URL",
                "$comment. Would you like to proceed to $linkWithRedirect?",
            )
        ) {
            throw IllegalArgumentException("$linkWithRedirect is not allowlisted")
        }
    }
}

/**
 * Return if the URL is allowlisted, https, and the URL and its final
 * destination, if it is a different host.
 */
private fun isAllowlisted(url: URL): Triple<Boolean, Boolean, String> {
    // TODO: Setting for the allowlist, and remember previously allowed
    //  domains.
    val domainAllowlist = listOf("intellij.net", "jetbrains.com")

    // Resolve any redirects.
    val finalUrl = resolveRedirects(url)

    var linkWithRedirect = url.toString()
    if (finalUrl.host != url.host) {
        linkWithRedirect = "$linkWithRedirect (redirects to to $finalUrl)"
    }

    val allowlisted =
        domainAllowlist.any { url.host == it || url.host.endsWith(".$it") } &&
            domainAllowlist.any { finalUrl.host == it || finalUrl.host.endsWith(".$it") }
    val https = url.protocol == "https" && finalUrl.protocol == "https"
    return Triple(allowlisted, https, linkWithRedirect)
}

/**
 * Follow a URL's redirects to its final destination.
 */
internal fun resolveRedirects(url: URL): URL {
    var location = url
    val maxRedirects = 10
    for (i in 1..maxRedirects) {
        val conn = location.openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connect()
        val code = conn.responseCode
        val nextLocation = conn.getHeaderField("Location")
        conn.disconnect()
        // Redirects are triggered by any code starting with 3 plus a
        // location header.
        if (code < 300 || code >= 400 || nextLocation.isNullOrBlank()) {
            return location
        }
        // Location headers might be relative.
        location = URL(location, nextLocation)
    }
    throw Exception("Too many redirects")
}

/**
 * Return the agent matching the provided agent ID or name in the parameters.
 * The name is ignored if the ID is set.  If neither was supplied and the
 * workspace has only one agent, return that.  Otherwise throw an error.
 *
 * @throws [MissingArgumentException, IllegalArgumentException]
 */
internal fun getMatchingAgent(
    parameters: Map<String, String?>,
    workspace: Workspace,
): WorkspaceAgent {
    val agents = workspace.latestBuild.resources.filter { it.agents != null }.flatMap { it.agents!! }
    if (agents.isEmpty()) {
        throw IllegalArgumentException("The workspace \"${workspace.name}\" has no agents")
    }

    // If the agent is missing and the workspace has only one, use that.
    // Prefer the ID over the name if both are set.
    val agent =
        if (!parameters.agentID().isNullOrBlank()) {
            agents.firstOrNull { it.id.toString() == parameters.agentID() }
        } else if (!parameters.agentName().isNullOrBlank()) {
            agents.firstOrNull { it.name == parameters.agentName() }
        } else if (agents.size == 1) {
            agents.first()
        } else {
            null
        }

    if (agent == null) {
        if (!parameters.agentID().isNullOrBlank()) {
            throw IllegalArgumentException("The workspace \"${workspace.name}\" does not have an agent with ID \"${parameters.agentID()}\"")
        } else if (!parameters.agentName().isNullOrBlank()) {
            throw IllegalArgumentException(
                "The workspace \"${workspace.name}\"does not have an agent named \"${parameters.agentName()}\"",
            )
        } else {
            throw MissingArgumentException(
                "Unable to determine which agent to connect to; one of \"$AGENT_NAME\" or \"$AGENT_ID\" must be set because the workspace \"${workspace.name}\" has more than one agent",
            )
        }
    }

    return agent
}

class MissingArgumentException(message: String) : IllegalArgumentException(message)
