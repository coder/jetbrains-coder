package com.coder.gateway

import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceAgent
import com.coder.gateway.sdk.v2.models.WorkspaceAgentLifecycleState
import com.coder.gateway.sdk.v2.models.WorkspaceAgentStatus
import com.coder.gateway.sdk.v2.models.WorkspaceStatus
import com.jetbrains.toolbox.gateway.EnvironmentVisibilityState
import com.jetbrains.toolbox.gateway.RemoteProviderEnvironment
import com.jetbrains.toolbox.gateway.environments.EnvironmentContentsView
import com.jetbrains.toolbox.gateway.states.EnvironmentStateConsumer
import com.jetbrains.toolbox.gateway.states.StandardRemoteEnvironmentState
import com.jetbrains.toolbox.gateway.ui.ActionListener
import java.net.URL
import java.util.concurrent.CompletableFuture

/**
 * Represents an agent and workspace combination.
 */
class CoderRemoteEnvironment(
    private val url: URL,
    private val workspace: Workspace,
    private val agent: WorkspaceAgent,
) : RemoteProviderEnvironment {
    private val stateListeners = mutableSetOf<EnvironmentStateConsumer>()
    private val actionListeners = mutableSetOf<ActionListener>()

    override fun getId(): String = "${workspace.name}.${agent.name}"
    override fun getName(): String = "${workspace.name}.${agent.name}"
    override fun addStateListener(p0: EnvironmentStateConsumer?): Boolean {
       return if (p0 != null) {
           stateListeners += p0
           // TODO: These do not quite fit so add some custom states.
           // TODO: It immediately connects to environments that are started.
           //       Do we really want that?  Would probably prefer connecting
           //       after explicitly clicking the environment first.
           // TODO: If someone clicks into the environment, we should start the
           //       environment and then connect (right now it just shows an
           //       infinitely loading page).
           val state = when (workspace.latestBuild.status) {
               // The workspace is queuing to start.
               WorkspaceStatus.PENDING -> StandardRemoteEnvironmentState.Initializing
               // The workspace is starting.
               WorkspaceStatus.STARTING -> StandardRemoteEnvironmentState.Initializing
               // The workspace is running.
               WorkspaceStatus.RUNNING -> when (agent.status) {
                   // The agent is ready.
                   WorkspaceAgentStatus.CONNECTED -> when (agent.lifecycleState) {
                       // The agent has been created.
                       WorkspaceAgentLifecycleState.CREATED -> StandardRemoteEnvironmentState.Initializing
                       // The startup script is running.
                       WorkspaceAgentLifecycleState.STARTING -> if (agent.loginBeforeReady == true)
                           StandardRemoteEnvironmentState.Active
                       else StandardRemoteEnvironmentState.Initializing
                       // The startup script is taking longer than expected to complete.
                       WorkspaceAgentLifecycleState.START_TIMEOUT -> if (agent.loginBeforeReady == true)
                           StandardRemoteEnvironmentState.Active
                       else StandardRemoteEnvironmentState.Unhealthy
                       // The startup script had an error.
                       WorkspaceAgentLifecycleState.START_ERROR -> StandardRemoteEnvironmentState.Active
                       // The startup script has completed.
                       WorkspaceAgentLifecycleState.READY -> StandardRemoteEnvironmentState.Active
                       // The shutdown script is running.
                       WorkspaceAgentLifecycleState.SHUTTING_DOWN -> StandardRemoteEnvironmentState.Hibernating
                       // The shutdown script is taking longer than expected to complete.
                       WorkspaceAgentLifecycleState.SHUTDOWN_TIMEOUT -> StandardRemoteEnvironmentState.Hibernating
                       // The shutdown script had an error.
                       WorkspaceAgentLifecycleState.SHUTDOWN_ERROR -> StandardRemoteEnvironmentState.Error
                       // The agent has shut down, although not sure how you get this plus connected.
                       WorkspaceAgentLifecycleState.OFF -> StandardRemoteEnvironmentState.Inactive
                   }

                   // The agent has disconnected.
                   WorkspaceAgentStatus.DISCONNECTED -> StandardRemoteEnvironmentState.Unreachable
                   // The agent is taking longer than expected to connect.
                   WorkspaceAgentStatus.TIMEOUT -> StandardRemoteEnvironmentState.Unhealthy
                   // The agent is connecting.
                   WorkspaceAgentStatus.CONNECTING -> StandardRemoteEnvironmentState.Initializing
               }

               WorkspaceStatus.STOPPING -> StandardRemoteEnvironmentState.Initializing
               WorkspaceStatus.STOPPED -> StandardRemoteEnvironmentState.Inactive
               WorkspaceStatus.FAILED -> StandardRemoteEnvironmentState.Failed
               WorkspaceStatus.CANCELING -> StandardRemoteEnvironmentState.Initializing
               WorkspaceStatus.CANCELED -> StandardRemoteEnvironmentState.Inactive
               WorkspaceStatus.DELETING -> StandardRemoteEnvironmentState.Deleting
               WorkspaceStatus.DELETED -> StandardRemoteEnvironmentState.Deleted
           }
           p0.consume(state)
           true
       } else false
    }

    override fun removeStateListener(p0: EnvironmentStateConsumer?) {
       if (p0 != null) {
           stateListeners -= p0
       }
    }

    override fun getContentsView(): CompletableFuture<EnvironmentContentsView> {
        return CompletableFuture.completedFuture(CoderEnvironmentContentsView(url, workspace, agent))
    }

    override fun setVisible(visibilityState: EnvironmentVisibilityState) {
    }

    override fun registerActionListener(p0: ActionListener) {
        actionListeners += p0
    }

    override fun unregisterActionListener(p0: ActionListener) {
        actionListeners -= p0
    }
}
