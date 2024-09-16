package com.coder.gateway.models

import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceAgent
import com.coder.gateway.sdk.v2.models.WorkspaceAgentLifecycleState
import com.coder.gateway.sdk.v2.models.WorkspaceAgentStatus
import com.coder.gateway.sdk.v2.models.WorkspaceStatus
import com.jetbrains.toolbox.gateway.states.Color
import com.jetbrains.toolbox.gateway.states.CustomRemoteEnvironmentState

/**
 * WorkspaceAndAgentStatus represents the combined status of a single agent and
 * its workspace (or just the workspace if there are no agents).
 */
enum class WorkspaceAndAgentStatus(val label: String, val description: String) {
    // Workspace states.
    QUEUED("Queued", "The workspace is queueing to start."),
    STARTING("Starting", "The workspace is starting."),
    FAILED("Failed", "The workspace has failed to start."),
    DELETING("Deleting", "The workspace is being deleted."),
    DELETED("Deleted", "The workspace has been deleted."),
    STOPPING("Stopping", "The workspace is stopping."),
    STOPPED("Stopped", "The workspace has stopped."),
    CANCELING("Canceling action", "The workspace is being canceled."),
    CANCELED("Canceled action", "The workspace has been canceled."),
    RUNNING("Running", "The workspace is running, waiting for agents."),

    // Agent states.
    CONNECTING("Connecting", "The agent is connecting."),
    DISCONNECTED("Disconnected", "The agent has disconnected."),
    TIMEOUT("Timeout", "The agent is taking longer than expected to connect."),
    AGENT_STARTING("Starting", "The startup script is running."),
    AGENT_STARTING_READY(
        "Starting",
        "The startup script is still running but the agent is ready to accept connections.",
    ),
    CREATED("Created", "The agent has been created."),
    START_ERROR("Started with error", "The agent is ready but the startup script errored."),
    START_TIMEOUT("Starting", "The startup script is taking longer than expected."),
    START_TIMEOUT_READY(
        "Starting",
        "The startup script is taking longer than expected but the agent is ready to accept connections.",
    ),
    SHUTTING_DOWN("Shutting down", "The agent is shutting down."),
    SHUTDOWN_ERROR("Shutdown with error", "The agent shut down but the shutdown script errored."),
    SHUTDOWN_TIMEOUT("Shutting down", "The shutdown script is taking longer than expected."),
    OFF("Off", "The agent has shut down."),
    READY("Ready", "The agent is ready to accept connections."),
    ;

    /**
     * Return the environment state for Toolbox, which tells it the label, color
     * and whether the environment is reachable.
     *
     * We mark all ready and pending states as reachable since if the workspace
     * is pending the cli will wait for it anyway.
     *
     * Additionally, terminal states like stopped are also marked as reachable,
     * since the cli will start them.
     */
    fun toRemoteEnvironmentState(): CustomRemoteEnvironmentState {
        // Use comments; no named arguments for non-Kotlin functions.
        // TODO@JB: Is there a set of default colors we could use?
        return CustomRemoteEnvironmentState(
            label,
            Color(200, 200, 200, 200), // darkThemeColor
            Color(104, 112, 128, 255), // lightThemeColor
            Color(224, 224, 240, 26), // darkThemeBackgroundColor
            Color(224, 224, 245, 250), // lightThemeBackgroundColor
            ready() || pending() || canStart(), // reachable
            // TODO@JB: How does this work?  Would like a spinner for pending states.
            null, // iconId
        )
    }

    /**
     * Return true if the agent is in a connectable state.
     */
    fun ready(): Boolean {
        // It seems that the agent can get stuck in a `created` state if the
        // workspace is updated and the agent is restarted (presumably because
        // lifecycle scripts are not running again).  This feels like either a
        // Coder or template bug, but `coder ssh` and the VS Code plugin will
        // still connect so do the same here to not be the odd one out.
        return listOf(READY, START_ERROR, AGENT_STARTING_READY, START_TIMEOUT_READY, CREATED)
            .contains(this)
    }

    /**
     * Return true if the agent might soon be in a connectable state.
     */
    fun pending(): Boolean {
        // See ready() for why `CREATED` is not in this list.
        return listOf(CONNECTING, TIMEOUT, AGENT_STARTING, START_TIMEOUT, QUEUED, STARTING)
            .contains(this)
    }

    /**
     * Return true if the workspace can be started.
     */
    fun canStart(): Boolean = listOf(STOPPED, FAILED, CANCELED)
        .contains(this)

    // We want to check that the workspace is `running`, the agent is
    // `connected`, and the agent lifecycle state is `ready` to ensure the best
    // possible scenario for attempting a connection.
    //
    // We can also choose to allow `start_error` for the agent lifecycle state;
    // this means the startup script did not successfully complete but the agent
    // will still accept SSH connections.
    //
    // Lastly we can also allow connections when the agent lifecycle state is
    // `starting` or `start_timeout` if `login_before_ready` is true on the
    // workspace response since this bypasses the need to wait for the script.
    //
    // Note that latest_build.status is derived from latest_build.job.status and
    // latest_build.job.transition so there is no need to check those.
    companion object {
        fun from(
            workspace: Workspace,
            agent: WorkspaceAgent? = null,
        ) = when (workspace.latestBuild.status) {
            WorkspaceStatus.PENDING -> QUEUED
            WorkspaceStatus.STARTING -> STARTING
            WorkspaceStatus.RUNNING ->
                when (agent?.status) {
                    WorkspaceAgentStatus.CONNECTED ->
                        when (agent.lifecycleState) {
                            WorkspaceAgentLifecycleState.CREATED -> CREATED
                            WorkspaceAgentLifecycleState.STARTING -> if (agent.loginBeforeReady == true) AGENT_STARTING_READY else AGENT_STARTING
                            WorkspaceAgentLifecycleState.START_TIMEOUT -> if (agent.loginBeforeReady == true) START_TIMEOUT_READY else START_TIMEOUT
                            WorkspaceAgentLifecycleState.START_ERROR -> START_ERROR
                            WorkspaceAgentLifecycleState.READY -> READY
                            WorkspaceAgentLifecycleState.SHUTTING_DOWN -> SHUTTING_DOWN
                            WorkspaceAgentLifecycleState.SHUTDOWN_TIMEOUT -> SHUTDOWN_TIMEOUT
                            WorkspaceAgentLifecycleState.SHUTDOWN_ERROR -> SHUTDOWN_ERROR
                            WorkspaceAgentLifecycleState.OFF -> OFF
                        }

                    WorkspaceAgentStatus.DISCONNECTED -> DISCONNECTED
                    WorkspaceAgentStatus.TIMEOUT -> TIMEOUT
                    WorkspaceAgentStatus.CONNECTING -> CONNECTING
                    else -> RUNNING
                }

            WorkspaceStatus.STOPPING -> STOPPING
            WorkspaceStatus.STOPPED -> STOPPED
            WorkspaceStatus.FAILED -> FAILED
            WorkspaceStatus.CANCELING -> CANCELING
            WorkspaceStatus.CANCELED -> CANCELED
            WorkspaceStatus.DELETING -> DELETING
            WorkspaceStatus.DELETED -> DELETED
        }
    }
}
