package com.coder.gateway.sdk

import com.coder.gateway.models.WorkspaceAgentListModel
import com.coder.gateway.sdk.v2.models.Template
import com.coder.gateway.sdk.v2.models.User
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceAgent
import com.coder.gateway.sdk.v2.models.WorkspaceAgentLifecycleState
import com.coder.gateway.sdk.v2.models.WorkspaceAgentStatus
import com.coder.gateway.sdk.v2.models.WorkspaceBuild
import com.coder.gateway.sdk.v2.models.WorkspaceResource
import com.coder.gateway.sdk.v2.models.WorkspaceStatus
import com.coder.gateway.sdk.v2.models.toAgentList
import com.coder.gateway.util.Arch
import com.coder.gateway.util.OS
import java.util.UUID

class DataGen {
    companion object {
        // Create a list of random agents for a random workspace.
        fun agentList(workspaceName: String, vararg agentName: String): List<WorkspaceAgentListModel> {
            val workspace = workspace(workspaceName, agents = agentName.associateWith { UUID.randomUUID().toString() })
            return workspace.toAgentList()
        }

        fun resource(agentName: String, agentId: String): WorkspaceResource {
            return WorkspaceResource(
                agents = listOf(WorkspaceAgent(
                    id = UUID.fromString(agentId),
                    status = WorkspaceAgentStatus.CONNECTED,
                    name = agentName,
                    architecture = Arch.from("amd64"),
                    operatingSystem = OS.from("linux"),
                    directory = null,
                    expandedDirectory = null,
                    lifecycleState = WorkspaceAgentLifecycleState.READY,
                    loginBeforeReady = false,
                )),
            )
        }

        fun workspace(name: String,
                      templateID: UUID = UUID.randomUUID(),
                      agents: Map<String, String> = emptyMap()): Workspace  {
            val wsId = UUID.randomUUID()
            return Workspace(
                id = wsId,
                templateID = templateID,
                templateName = "template-name",
                templateDisplayName = "template-display-name",
                templateIcon = "template-icon",
                latestBuild = build(
                    resources = agents.map{ resource(it.key, it.value) },
                ),
                outdated = false,
                name = name,
            )
        }

        fun build(templateVersionID: UUID = UUID.randomUUID(),
                  resources: List<WorkspaceResource> = emptyList()): WorkspaceBuild {
            return WorkspaceBuild(
                templateVersionID = templateVersionID,
                resources = resources,
                status = WorkspaceStatus.RUNNING,
            )
        }

        fun template(): Template {
            return Template(
                id = UUID.randomUUID(),
                activeVersionID = UUID.randomUUID(),
            )
        }

        fun user(): User {
            return User(
                "tester",
            )
        }
    }
}
