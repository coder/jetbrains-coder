@file:Suppress("DialogTitleCapitalization")

package com.coder.gateway

import com.coder.gateway.models.TokenSource
import com.coder.gateway.sdk.CoderCLIManager
import com.coder.gateway.sdk.CoderRestClient
import com.coder.gateway.sdk.ex.AuthenticationResponseException
import com.coder.gateway.sdk.toURL
import com.coder.gateway.sdk.v2.models.toAgentModels
import com.coder.gateway.sdk.withPath
import com.coder.gateway.services.CoderSettingsState
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.gateway.api.ConnectionRequestor
import com.jetbrains.gateway.api.GatewayConnectionHandle
import com.jetbrains.gateway.api.GatewayConnectionProvider
import java.net.URL

// In addition to `type`, these are the keys that we support in our Gateway
// links.
private const val URL = "url"
private const val TOKEN = "token"
private const val WORKSPACE = "workspace"
private const val AGENT = "agent"
private const val FOLDER = "folder"
private const val IDE_DOWNLOAD_LINK = "ide_download_link"
private const val IDE_PRODUCT_CODE = "ide_product_code"
private const val IDE_BUILD_NUMBER = "ide_build_number"
private const val IDE_PATH_ON_HOST = "ide_path_on_host"

// CoderGatewayConnectionProvider handles connecting via a Gateway link such as
// jetbrains-gateway://connect#type=coder.
class CoderGatewayConnectionProvider : GatewayConnectionProvider {
    private val settings: CoderSettingsState = service()

    override suspend fun connect(parameters: Map<String, String>, requestor: ConnectionRequestor): GatewayConnectionHandle? {
        CoderRemoteConnectionHandle().connect{ indicator ->
            logger.debug("Launched Coder connection provider", parameters)

            val deploymentURL = parameters[URL]
                ?: CoderRemoteConnectionHandle.ask("Enter the full URL of your Coder deployment")
            if (deploymentURL.isNullOrBlank()) {
                throw IllegalArgumentException("Query parameter \"$URL\" is missing")
            }

            val (client, username) = authenticate(deploymentURL.toURL(), parameters[TOKEN])

            // TODO: If these are missing we could launch the wizard.
            val name = parameters[WORKSPACE] ?: throw IllegalArgumentException("Query parameter \"$WORKSPACE\" is missing")
            val agent = parameters[AGENT] ?: throw IllegalArgumentException("Query parameter \"$AGENT\" is missing")

            val workspaces = client.workspaces()
            val agents = workspaces.flatMap { it.toAgentModels() }
            val workspace = agents.firstOrNull { it.name == "$name.$agent" }
                ?: throw IllegalArgumentException("The agent $agent does not exist on the workspace $name or the workspace is off")

            // TODO: Turn on the workspace if it is off then wait for the agent
            //       to be ready.  Also, distinguish between whether the
            //       workspace is off or the agent does not exist in the error
            //       above instead of showing a combined error.

            val cli = CoderCLIManager.ensureCLI(
                deploymentURL.toURL(),
                client.buildInfo().version,
                settings,
                indicator,
            )

            indicator.text = "Authenticating Coder CLI..."
            cli.login(client.token)

            indicator.text = "Configuring Coder CLI..."
            cli.configSsh(agents)

            // TODO: Ask for these if missing.  Maybe we can reuse the second
            //  step of the wizard?  Could also be nice if we automatically used
            //  the last IDE.
            if (parameters[IDE_PRODUCT_CODE].isNullOrBlank()) {
                throw IllegalArgumentException("Query parameter \"$IDE_PRODUCT_CODE\" is missing")
            }
            if (parameters[IDE_BUILD_NUMBER].isNullOrBlank()) {
                throw IllegalArgumentException("Query parameter \"$IDE_BUILD_NUMBER\" is missing")
            }
            if (parameters[IDE_PATH_ON_HOST].isNullOrBlank() && parameters[IDE_DOWNLOAD_LINK].isNullOrBlank()) {
                throw IllegalArgumentException("One of \"$IDE_PATH_ON_HOST\" or \"$IDE_DOWNLOAD_LINK\" is required")
            }

            // TODO: Ask for the project path if missing and validate the path.
            val folder = parameters[FOLDER] ?: throw IllegalArgumentException("Query parameter \"$FOLDER\" is missing")

            parameters
                .withWorkspaceHostname(CoderCLIManager.getHostName(deploymentURL.toURL(), workspace))
                .withProjectPath(folder)
                .withWebTerminalLink(client.url.withPath("/@$username/$workspace.name/terminal").toString())
                .withConfigDirectory(cli.coderConfigPath.toString())
                .withName(name)
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
        )
        if (token == null) { // User aborted.
            throw IllegalArgumentException("Unable to connect to $deploymentURL, $TOKEN is missing")
        }
        val client = CoderRestClient(deploymentURL, token.first)
        return try {
            Pair(client, client.me().username)
        } catch (ex: AuthenticationResponseException) {
            authenticate(deploymentURL, queryToken, token)
        }
    }

    override fun isApplicable(parameters: Map<String, String>): Boolean {
        return parameters.areCoderType()
    }

    companion object {
        val logger = Logger.getInstance(CoderGatewayConnectionProvider::class.java.simpleName)
    }
}
