package com.coder.gateway

import com.coder.gateway.models.WorkspaceAndAgentStatus
import com.coder.gateway.sdk.CoderRestClient
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceAgent
import com.coder.gateway.views.EnvironmentView
import com.jetbrains.toolbox.gateway.AbstractRemoteProviderEnvironment
import com.jetbrains.toolbox.gateway.EnvironmentVisibilityState
import com.jetbrains.toolbox.gateway.environments.EnvironmentContentsView
import com.jetbrains.toolbox.gateway.states.EnvironmentStateConsumer
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
    private val status = WorkspaceAndAgentStatus.from(workspace, agent)


    // Map each state to whether a connection can be attempted.
    private var state = status.toRemoteEnvironmentState()

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
