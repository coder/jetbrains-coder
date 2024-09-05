package com.coder.gateway

import com.coder.gateway.sdk.CoderRestClient
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceAgent
import com.coder.gateway.sdk.v2.models.WorkspaceAgentStatus
import com.coder.gateway.sdk.v2.models.WorkspaceStatus
import com.coder.gateway.views.EnvironmentView
import com.jetbrains.toolbox.gateway.AbstractRemoteProviderEnvironment
import com.jetbrains.toolbox.gateway.EnvironmentVisibilityState
import com.jetbrains.toolbox.gateway.environments.EnvironmentContentsView
import com.jetbrains.toolbox.gateway.states.EnvironmentStateConsumer
import com.jetbrains.toolbox.gateway.states.StandardRemoteEnvironmentState
import com.jetbrains.toolbox.gateway.ui.ObservablePropertiesFactory
import java.util.concurrent.CompletableFuture

/**
 * Represents an agent and workspace combination.
 *
 * Used in the environment list view.
 */
class CoderRemoteEnvironment(
    private val client: CoderRestClient,
    private val workspace: Workspace,
    private val agent: WorkspaceAgent,
    observablePropertiesFactory: ObservablePropertiesFactory,
) : AbstractRemoteProviderEnvironment(observablePropertiesFactory) {
    override fun getId(): String = "${workspace.name}.${agent.name}"
    override fun getName(): String = "${workspace.name}.${agent.name}"

    // Active (and unhealthy) here indicate that the workspace is in a state
    // where a connection can be attempted, not that the workspace is up and
    // running.  Once a connection is actually initiated, the CLI will then
    // start the workspace if it is off.
    private var state = when (workspace.latestBuild.status) {
        WorkspaceStatus.PENDING -> StandardRemoteEnvironmentState.Active
        WorkspaceStatus.STARTING -> StandardRemoteEnvironmentState.Active
        WorkspaceStatus.RUNNING -> when (agent.status) {
            WorkspaceAgentStatus.CONNECTED -> StandardRemoteEnvironmentState.Active
            WorkspaceAgentStatus.DISCONNECTED -> StandardRemoteEnvironmentState.Unreachable
            WorkspaceAgentStatus.TIMEOUT -> StandardRemoteEnvironmentState.Unhealthy
            WorkspaceAgentStatus.CONNECTING -> StandardRemoteEnvironmentState.Active
        }
        WorkspaceStatus.STOPPING -> StandardRemoteEnvironmentState.Initializing
        WorkspaceStatus.STOPPED -> StandardRemoteEnvironmentState.Active
        WorkspaceStatus.FAILED -> StandardRemoteEnvironmentState.Unhealthy
        WorkspaceStatus.CANCELING -> StandardRemoteEnvironmentState.Initializing
        WorkspaceStatus.CANCELED -> StandardRemoteEnvironmentState.Active
        WorkspaceStatus.DELETING -> StandardRemoteEnvironmentState.Deleting
        WorkspaceStatus.DELETED -> StandardRemoteEnvironmentState.Deleted
    }

    /**
     * The contents are provided by the SSH view provided by Toolbox, all we
     * have to do is provide it a host name.
     */
    override fun getContentsView(): CompletableFuture<EnvironmentContentsView> =
        CompletableFuture.completedFuture(EnvironmentView(client.url, workspace, agent))

    /**
     * Does nothing.  In theory we could do something like start the workspace
     * when you click into the workspace but you would still need to press
     * "connect" anyway before the content is populated so there does not seem
     * to be much value.
     */
    override fun setVisible(visibilityState: EnvironmentVisibilityState) {}

    /**
     * Immediately send the state to the listener.
     *
     * Currently we consume the entire workspace list and are not updating
     * individual workspaces, so the state here is static and the listener is
     * only used once.
     */
    override fun addStateListener(consumer: EnvironmentStateConsumer): Boolean {
        consumer.consume(state)
        return super.addStateListener(consumer)
    }
}
