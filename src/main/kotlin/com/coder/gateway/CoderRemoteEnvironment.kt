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
    private var status = WorkspaceAndAgentStatus.from(workspace, agent)

    /**
     * Update the workspace/agent status to the listeners, if it has changed.
     */
    fun update(workspace: Workspace, agent: WorkspaceAgent) {
        val newStatus = WorkspaceAndAgentStatus.from(workspace, agent)
        if (newStatus != status) {
            status = newStatus
            val state = status.toRemoteEnvironmentState()
            listenerSet.forEach { it.consume(state) }
        }
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
     * Immediately send the state to the listener and store for updates.
     */
    override fun addStateListener(consumer: EnvironmentStateConsumer): Boolean {
        consumer.consume(status.toRemoteEnvironmentState())
        return super.addStateListener(consumer)
    }

    /**
     * An environment is equal if it has the same ID.
     */
    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this === other) return true // Note the triple ===
        if (other !is CoderRemoteEnvironment) return false
        if (getId() != other.getId()) return false
        return true
    }

    /**
     * Companion to equals, for sets.
     */
    override fun hashCode(): Int = getId().hashCode()
}
