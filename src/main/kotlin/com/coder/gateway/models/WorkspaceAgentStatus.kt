package com.coder.gateway.models

import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceStatus
import com.intellij.ui.JBColor

enum class WorkspaceAgentStatus(val label: String) {
    QUEUED("◍ Queued"), STARTING("⦿ Starting"), STOPPING("◍ Stopping"), DELETING("⦸ Deleting"),
    RUNNING("⦿ Running"), STOPPED("◍ Stopped"), DELETED("⦸ Deleted"),
    CANCELING("◍ Canceling action"), CANCELED("◍ Canceled action"), FAILED("ⓧ Failed");

    fun statusColor() = when (this) {
        RUNNING -> JBColor.GREEN
        FAILED -> JBColor.RED
        else -> if (JBColor.isBright()) JBColor.LIGHT_GRAY else JBColor.DARK_GRAY
    }

    // Note that latest_build.status is derived from latest_build.job.status and
    // latest_build.job.transition so there is no need to check those.
    companion object {
        fun from(workspace: Workspace, agent: WorkspaceAgentModel) = when (workspace.latestBuild.status) {
            WorkspaceStatus.PENDING -> QUEUED
            WorkspaceStatus.STARTING -> STARTING
            WorkspaceStatus.RUNNING -> RUNNING
            WorkspaceStatus.STOPPING -> STOPPING
            WorkspaceStatus.STOPPED -> STOPPED
            WorkspaceStatus.FAILED -> FAILED
            WorkspaceStatus.CANCELING -> CANCELING
            WorkspaceStatus.CANCELED -> CANCELED
            WorkspaceStatus.DELETING -> DELETING
            WorkspaceStatus.DELETED -> DELETED
        }

        fun from(str: String) = WorkspaceAgentStatus.values().first { it.label.contains(str, true) }
    }
}
