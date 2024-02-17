package com.coder.gateway.sdk

import com.coder.gateway.models.WorkspaceAgentModel
import com.coder.gateway.models.WorkspaceAndAgentStatus
import com.coder.gateway.models.WorkspaceVersionStatus
import com.coder.gateway.sdk.v2.models.BuildReason
import com.coder.gateway.sdk.v2.models.ProvisionerJob
import com.coder.gateway.sdk.v2.models.ProvisionerJobStatus
import com.coder.gateway.sdk.v2.models.ProvisionerType
import com.coder.gateway.sdk.v2.models.Template
import com.coder.gateway.sdk.v2.models.User
import com.coder.gateway.sdk.v2.models.UserStatus
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceAgent
import com.coder.gateway.sdk.v2.models.WorkspaceAgentLifecycleState
import com.coder.gateway.sdk.v2.models.WorkspaceAgentStatus
import com.coder.gateway.sdk.v2.models.WorkspaceBuild
import com.coder.gateway.sdk.v2.models.WorkspaceResource
import com.coder.gateway.sdk.v2.models.WorkspaceStatus
import com.coder.gateway.sdk.v2.models.WorkspaceTransition
import com.coder.gateway.util.Arch
import com.coder.gateway.util.OS
import java.time.Instant
import java.util.*

class DataGen {
    companion object {
        // Create a random workspace agent model.  If the workspace name is omitted
        // then return a model without any agent bits, similar to what
        // toAgentModels() does if the workspace does not specify any agents.
        // TODO: Maybe better to randomly generate the workspace and then call
        //       toAgentModels() on it.  Also the way an "agent" model can have no
        //       agent in it seems weird; can we refactor to remove
        //       WorkspaceAgentModel and use the original structs from the API?
        fun workspaceAgentModel(name: String, workspaceName: String = "", agentId: UUID = UUID.randomUUID()): WorkspaceAgentModel  {
            return WorkspaceAgentModel(
                if (workspaceName == "") null else agentId,
                UUID.randomUUID(),
                if (workspaceName == "") name else workspaceName,
                if (workspaceName == "") name else ("$workspaceName.$name"),
                UUID.randomUUID(),
                "template-name",
                "template-icon-path",
                null,
                WorkspaceVersionStatus.UPDATED,
                WorkspaceStatus.RUNNING,
                WorkspaceAndAgentStatus.READY,
                WorkspaceTransition.START,
                null,
                null,
                null
            )
        }

        fun resource(agentName: String, agentId: String): WorkspaceResource {
            return WorkspaceResource(
                id = UUID.randomUUID(),
                createdAt = Date().toInstant(),
                jobID = UUID.randomUUID(),
                WorkspaceTransition.START,
                "type",
                "name",
                hide = false,
                "icon",
                listOf(WorkspaceAgent(
                    UUID.fromString(agentId),
                    createdAt = Date().toInstant(),
                    updatedAt = Date().toInstant(),
                    firstConnectedAt = null,
                    lastConnectedAt = null,
                    disconnectedAt = null,
                    WorkspaceAgentStatus.CONNECTED,
                    agentName,
                    resourceID = UUID.randomUUID(),
                    instanceID = null,
                    architecture = Arch.from("amd64"),
                    envVariables = emptyMap(),
                    operatingSystem = OS.from("linux"),
                    startupScript = null,
                    directory = null,
                    expandedDirectory = null,
                    version = "version",
                    apps = emptyList(),
                    derpLatency = null,
                    connectionTimeoutSeconds = 0,
                    troubleshootingURL = "url",
                    WorkspaceAgentLifecycleState.READY,
                    loginBeforeReady = false,
                )),
                null,                   // metadata
                0,                      // daily_cost
            )
        }

        fun workspace(name: String,
                      templateID: UUID = UUID.randomUUID(),
                      agents: Map<String, String> = emptyMap(),
                      transition: WorkspaceTransition = WorkspaceTransition.START): Workspace  {
            val wsId = UUID.randomUUID()
            val ownerId = UUID.randomUUID()
            return Workspace(
                id = wsId,
                createdAt = Date().toInstant(),
                updatedAt = Date().toInstant(),
                ownerID = ownerId,
                ownerName = "owner-name",
                templateID = templateID,
                templateName = "template-name",
                templateDisplayName = "template-display-name",
                templateIcon = "template-icon",
                templateAllowUserCancelWorkspaceJobs = false,
                latestBuild = build(
                    workspaceID = wsId,
                    workspaceName = name,
                    ownerID = ownerId,
                    ownerName = "owner-name",
                    transition = transition,
                    resources = agents.map{ resource(it.key, it.value) },
                ),
                outdated = false,
                name = name,
                autostartSchedule = null,
                ttlMillis = null,
                lastUsedAt = Date().toInstant(),
            )
        }

        fun build(workspaceID: UUID,
                  workspaceName: String,
                  ownerID: UUID,
                  ownerName: String,
                  transition: WorkspaceTransition = WorkspaceTransition.START,
                  templateVersionID: UUID = UUID.randomUUID(),
                  resources: List<WorkspaceResource> = emptyList()): WorkspaceBuild {
            return WorkspaceBuild(
                id = UUID.randomUUID(),
                createdAt = Date().toInstant(),
                updatedAt = Date().toInstant(),
                workspaceID = workspaceID,
                workspaceName = workspaceName,
                workspaceOwnerID = ownerID,
                workspaceOwnerName = ownerName,
                templateVersionID = templateVersionID,
                buildNumber = 0,
                transition = transition,
                initiatorID = UUID.randomUUID(),
                initiatorUsername = ownerName,
                job = ProvisionerJob(
                    id = UUID.randomUUID(),
                    createdAt = Date().toInstant(),
                    startedAt = null,
                    completedAt = null,
                    canceledAt = null,
                    error = null,
                    ProvisionerJobStatus.SUCCEEDED,
                    workerID = null,
                    fileID = UUID.randomUUID(),
                    tags = emptyMap(),
                ),
                reason = BuildReason.INITIATOR,
                resources = resources,
                deadline = Date().toInstant(),
                status = WorkspaceStatus.RUNNING,
                dailyCost = 0,
            )
        }

        fun template(name: String): Template {
            return Template(
                id = UUID.randomUUID(),
                createdAt = Date().toInstant(),
                updatedAt = Date().toInstant(),
                organizationIterator = UUID.randomUUID(),
                name = name,
                displayName = name,
                provisioner = ProvisionerType.ECHO,
                activeVersionID = UUID.randomUUID(),
                workspaceOwnerCount = 0,
                activeUserCount = 0,
                buildTimeStats = emptyMap(),
                description = "",
                icon = "",
                defaultTTLMillis = 0,
                createdByID = UUID.randomUUID(),
                createdByName = "",
                allowUserCancelWorkspaceJobs = true,
            )
        }

        fun user(): User {
            return User(
                UUID.randomUUID(),
                "tester",
                "tester@example.com",
                Instant.now(),
                Instant.now(),
                UserStatus.ACTIVE,
                listOf(),
                listOf(),
                "",
            )
        }
    }
}
