package com.coder.gateway.models

import com.coder.gateway.sdk.v2.models.ProvisionerJobStatus
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceTransition
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

    companion object {
        fun from(workspace: Workspace) = when (workspace.latestBuild.job.status) {
            ProvisionerJobStatus.PENDING -> QUEUED
            ProvisionerJobStatus.RUNNING -> when (workspace.latestBuild.transition) {
                WorkspaceTransition.START -> STARTING
                WorkspaceTransition.STOP -> STOPPING
                WorkspaceTransition.DELETE -> DELETING
            }

            ProvisionerJobStatus.SUCCEEDED -> when (workspace.latestBuild.transition) {
                WorkspaceTransition.START -> RUNNING
                WorkspaceTransition.STOP -> STOPPED
                WorkspaceTransition.DELETE -> DELETED
            }

            ProvisionerJobStatus.CANCELING -> CANCELING
            ProvisionerJobStatus.CANCELED -> CANCELED
            ProvisionerJobStatus.FAILED -> FAILED
        }

        fun from(str: String) = WorkspaceAgentStatus.values().first { it.label.contains(str, true) }
    }
}