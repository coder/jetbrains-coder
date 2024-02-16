package com.coder.gateway.sdk

import com.coder.gateway.models.WorkspaceAgentModel
import com.coder.gateway.models.WorkspaceAndAgentStatus
import com.coder.gateway.models.WorkspaceVersionStatus
import com.coder.gateway.sdk.v2.models.*
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
                    architecture = "arch",
                    envVariables = emptyMap(),
                    operatingSystem = "os",
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

        fun workspace(
            name: String,
            agents: Map<String, String> = emptyMap(),
            transition: WorkspaceTransition = WorkspaceTransition.START): Workspace  {
            val wsId = UUID.randomUUID()
            val ownerId = UUID.randomUUID()
            val resources: List<WorkspaceResource> = agents.map{ resource(it.key, it.value) }
            return Workspace(
                wsId,
                createdAt = Date().toInstant(),
                updatedAt = Date().toInstant(),
                ownerId,
                "owner-name",
                templateID = UUID.randomUUID(),
                "template-name",
                "template-display-name",
                "template-icon",
                templateAllowUserCancelWorkspaceJobs = false,
                WorkspaceBuild(
                    id = UUID.randomUUID(),
                    createdAt = Date().toInstant(),
                    updatedAt = Date().toInstant(),
                    wsId,
                    name,
                    ownerId,
                    "owner-name",
                    templateVersionID = UUID.randomUUID(),
                    buildNumber = 0,
                    transition,
                    initiatorID = UUID.randomUUID(),
                    "initiator-name",
                    ProvisionerJob(
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
                    BuildReason.INITIATOR,
                    resources,
                    deadline = null,
                    WorkspaceStatus.RUNNING,
                    dailyCost = 0,
                ),
                outdated = false,
                name,
                autostartSchedule = null,
                ttlMillis = null,
                lastUsedAt = Date().toInstant(),
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
    }
}
