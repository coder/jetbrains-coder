package com.coder.gateway.models

import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceAgent
import com.coder.gateway.sdk.v2.models.WorkspaceAgentLifecycleState
import com.coder.gateway.sdk.v2.models.WorkspaceAgentStatus
import com.coder.gateway.sdk.v2.models.WorkspaceStatus
import com.intellij.ui.JBColor

/**
 * WorkspaceAndAgentStatus represents the combined status of a single agent and
 * its workspace (or just the workspace if there are no agents).
 */
enum class WorkspaceAndAgentStatus(val label: String, val description: String) {
    // Workspace states.
    QUEUED("◍ Queued", "The workspace is queueing to start."),
    STARTING("⦿ Starting", "The workspace is starting."),
    FAILED("ⓧ Failed", "The workspace has failed to start."),
    DELETING("⦸ Deleting", "The workspace is being deleted."),
    DELETED("⦸ Deleted", "The workspace has been deleted."),
    STOPPING("◍ Stopping", "The workspace is stopping."),
    STOPPED("◍ Stopped", "The workspace has stopped."),
    CANCELING("◍ Canceling action", "The workspace is being canceled."),
    CANCELED("◍ Canceled action", "The workspace has been canceled."),
    RUNNING("⦿ Running", "The workspace is running, waiting for agents."),

    // Agent states.
    CONNECTING("⦿ Connecting", "The agent is connecting."),
    DISCONNECTED("⦸ Disconnected", "The agent has disconnected."),
    TIMEOUT("ⓧ Timeout", "The agent has timed out."),
    AGENT_STARTING("⦿ Starting", "The agent is running the startup script."),
    AGENT_STARTING_READY("⦿ Starting", "The agent is running the startup script but is ready to accept connections."),
    CREATED("⦿ Created", "The agent has been created."),
    START_ERROR("◍ Started with error", "The agent is ready but the startup script errored."),
    START_TIMEOUT("◍ Started with timeout", "The agent is ready but the startup script timed out"),
    SHUTTING_DOWN("◍ Shutting down", "The agent is shutting down."),
    SHUTDOWN_ERROR("⦸ Shutdown with error", "The agent shut down but the shutdown script errored."),
    SHUTDOWN_TIMEOUT("⦸ Shutdown with timeout", "The agent shut down but the shutdown script timed out."),
    OFF("⦸ Off", "The agent has shut down."),
    READY("⦿ Ready", "The agent is ready to accept connections.");

    fun statusColor(): JBColor = when (this) {
        READY, AGENT_STARTING_READY -> JBColor.GREEN
        START_ERROR, START_TIMEOUT -> JBColor.YELLOW
        FAILED, DISCONNECTED, TIMEOUT, SHUTTING_DOWN, SHUTDOWN_ERROR, SHUTDOWN_TIMEOUT -> JBColor.RED
        else -> if (JBColor.isBright()) JBColor.LIGHT_GRAY else JBColor.DARK_GRAY
    }

    // We want to check that the workspace is `running`, the agent is
    // `connected`, and the agent lifecycle state is `ready` to ensure the best
    // possible scenario for attempting a connection.
    //
    // We can also choose to allow `start_timeout` and `start_error` for the
    // agent state; this means the startup script did not successfully complete
    // but the agent will accept SSH connections.
    //
    // Lastly we can also allow connections when the agent lifecycle state is
    // `starting` if `login_before_ready` is true on the workspace response.
    //
    // Note that latest_build.status is derived from latest_build.job.status and
    // latest_build.job.transition so there is no need to check those.
    companion object {
        fun from(workspace: Workspace, agent: WorkspaceAgent? = null) = when (workspace.latestBuild.status) {
            WorkspaceStatus.PENDING -> QUEUED
            WorkspaceStatus.STARTING -> STARTING
            WorkspaceStatus.RUNNING -> when (agent?.status) {
                WorkspaceAgentStatus.CONNECTED -> when (agent.lifecycleState) {
                    WorkspaceAgentLifecycleState.CREATED -> CREATED
                    WorkspaceAgentLifecycleState.STARTING -> if (agent.loginBeforeReady == true) AGENT_STARTING_READY else AGENT_STARTING
                    WorkspaceAgentLifecycleState.START_TIMEOUT -> START_TIMEOUT
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

        fun from(str: String) = WorkspaceAndAgentStatus.values().first { it.label.contains(str, true) }
    }
}
