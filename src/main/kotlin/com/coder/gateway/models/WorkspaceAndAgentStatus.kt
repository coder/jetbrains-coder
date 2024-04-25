package com.coder.gateway.models

import com.coder.gateway.icons.CoderIcons
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceAgent
import com.coder.gateway.sdk.v2.models.WorkspaceAgentLifecycleState
import com.coder.gateway.sdk.v2.models.WorkspaceAgentStatus
import com.coder.gateway.sdk.v2.models.WorkspaceStatus
import com.intellij.ui.JBColor
import javax.swing.Icon

/**
 * WorkspaceAndAgentStatus represents the combined status of a single agent and
 * its workspace (or just the workspace if there are no agents).
 */
enum class WorkspaceAndAgentStatus(val icon: Icon, val label: String, val description: String) {
    // Workspace states.
    QUEUED(CoderIcons.PENDING, "Queued", "The workspace is queueing to start."),
    STARTING(CoderIcons.PENDING, "Starting", "The workspace is starting."),
    FAILED(CoderIcons.OFF, "Failed", "The workspace has failed to start."),
    DELETING(CoderIcons.PENDING, "Deleting", "The workspace is being deleted."),
    DELETED(CoderIcons.OFF, "Deleted", "The workspace has been deleted."),
    STOPPING(CoderIcons.PENDING, "Stopping", "The workspace is stopping."),
    STOPPED(CoderIcons.OFF, "Stopped", "The workspace has stopped."),
    CANCELING(CoderIcons.PENDING, "Canceling action", "The workspace is being canceled."),
    CANCELED(CoderIcons.OFF, "Canceled action", "The workspace has been canceled."),
    RUNNING(CoderIcons.RUN, "Running", "The workspace is running, waiting for agents."),

    // Agent states.
    CONNECTING(CoderIcons.PENDING, "Connecting", "The agent is connecting."),
    DISCONNECTED(CoderIcons.OFF, "Disconnected", "The agent has disconnected."),
    TIMEOUT(CoderIcons.PENDING, "Timeout", "The agent is taking longer than expected to connect."),
    AGENT_STARTING(CoderIcons.PENDING, "Starting", "The startup script is running."),
    AGENT_STARTING_READY(
        CoderIcons.RUNNING,
        "Starting",
        "The startup script is still running but the agent is ready to accept connections.",
    ),
    CREATED(CoderIcons.PENDING, "Created", "The agent has been created."),
    START_ERROR(CoderIcons.RUNNING, "Started with error", "The agent is ready but the startup script errored."),
    START_TIMEOUT(CoderIcons.PENDING, "Starting", "The startup script is taking longer than expected."),
    START_TIMEOUT_READY(
        CoderIcons.RUNNING,
        "Starting",
        "The startup script is taking longer than expected but the agent is ready to accept connections.",
    ),
    SHUTTING_DOWN(CoderIcons.PENDING, "Shutting down", "The agent is shutting down."),
    SHUTDOWN_ERROR(CoderIcons.OFF, "Shutdown with error", "The agent shut down but the shutdown script errored."),
    SHUTDOWN_TIMEOUT(CoderIcons.OFF, "Shutting down", "The shutdown script is taking longer than expected."),
    OFF(CoderIcons.OFF, "Off", "The agent has shut down."),
    READY(CoderIcons.RUNNING, "Ready", "The agent is ready to accept connections."),
    ;

    fun statusColor(): JBColor =
        when (this) {
            READY, AGENT_STARTING_READY, START_TIMEOUT_READY -> JBColor.GREEN
            START_ERROR, START_TIMEOUT, SHUTDOWN_TIMEOUT -> JBColor.YELLOW
            FAILED, DISCONNECTED, TIMEOUT, SHUTDOWN_ERROR -> JBColor.RED
            else -> if (JBColor.isBright()) JBColor.LIGHT_GRAY else JBColor.DARK_GRAY
        }

    /**
     * Return true if the agent is in a connectable state.
     */
    fun ready(): Boolean {
        return listOf(READY, START_ERROR, AGENT_STARTING_READY, START_TIMEOUT_READY)
            .contains(this)
    }

    /**
     * Return true if the agent might soon be in a connectable state.
     */
    fun pending(): Boolean {
        return listOf(CONNECTING, TIMEOUT, CREATED, AGENT_STARTING, START_TIMEOUT)
            .contains(this)
    }

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
