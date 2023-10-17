import com.coder.gateway.models.WorkspaceAgentModel
import com.coder.gateway.models.WorkspaceAndAgentStatus
import com.coder.gateway.models.WorkspaceVersionStatus
import com.coder.gateway.sdk.v2.models.*

class DataGen {
    // Create a random workspace agent model.  If the workspace name is omitted
    // then return a model without any agent bits, similar to what
    // toAgentModels() does if the workspace does not specify any agents.
    // TODO: Maybe better to randomly generate the workspace and then call
    //       toAgentModels() on it.  Also the way an "agent" model can have no
    //       agent in it seems weird; can we refactor to remove
    //       WorkspaceAgentModel and use the original structs from the API?
    static WorkspaceAgentModel workspaceAgentModel(String name, String workspaceName = "", UUID agentId = UUID.randomUUID()) {
        return new WorkspaceAgentModel(
                workspaceName == "" ? null : agentId,
                UUID.randomUUID(),
                workspaceName == "" ? name : workspaceName,
                workspaceName == "" ? name : (workspaceName + "." + name),
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

    static WorkspaceResource resource(String agentName, String agentId){
        return new WorkspaceResource(
                UUID.randomUUID(),      // id
                new Date().toInstant(), // created_at
                UUID.randomUUID(),      // job_id
                WorkspaceTransition.START,
                "type",
                "name",
                false,                  // hide
                "icon",
                List.of(new WorkspaceAgent(
                        UUID.fromString(agentId),
                        new Date().toInstant(),    // created_at
                        new Date().toInstant(),    // updated_at
                        null,                      // first_connected_at
                        null,                      // last_connected_at
                        null,                      // disconnected_at
                        WorkspaceAgentStatus.CONNECTED,
                        agentName,
                        UUID.randomUUID(),         // resource_id
                        null,                      // instance_id
                        "arch",                    // architecture
                        [:],                       // environment_variables
                        "os",                      // operating_system
                        null,                      // startup_script
                        null,                      // directory
                        null,                      // expanded_directory
                        "version",                 // version
                        List.of(),                 // apps
                        null,                      // latency
                        0,                         // connection_timeout_seconds
                        "url",                     // troubleshooting_url
                        WorkspaceAgentLifecycleState.READY,
                        false,                     // login_before_ready
                )),
                null,                   // metadata
                0,                      // daily_cost
        )
    }

    static Workspace workspace(String name, Map<String, String> agents = [:]) {
        UUID wsId = UUID.randomUUID()
        UUID ownerId = UUID.randomUUID()
        List<WorkspaceResource> resources = agents.collect{ resource(it.key, it.value)}
        return new Workspace(
                wsId,
                new Date().toInstant(), // created_at
                new Date().toInstant(), // updated_at
                ownerId,
                "owner-name",
                UUID.randomUUID(),      // template_id
                "template-name",
                "template-display-name",
                "template-icon",
                false,                  // template_allow_user_cancel_workspace_jobs
                new WorkspaceBuild(
                        UUID.randomUUID(),      // id
                        new Date().toInstant(), // created_at
                        new Date().toInstant(), // updated_at
                        wsId,
                        name,
                        ownerId,
                        "owner-name",
                        UUID.randomUUID(),      // template_version_id
                        0,                      // build_number
                        WorkspaceTransition.START,
                        UUID.randomUUID(),      // initiator_id
                        "initiator-name",
                        new ProvisionerJob(
                                UUID.randomUUID(),      // id
                                new Date().toInstant(), // created_at
                                null,                   // started_at
                                null,                   // completed_at
                                null,                   // canceled_at
                                null,                   // error
                                ProvisionerJobStatus.SUCCEEDED,
                                null,                   // worker_id
                                UUID.randomUUID(),      // file_id
                                [:],                    // tags
                        ),
                        BuildReason.INITIATOR,
                        resources,
                        null,                   // deadline
                        WorkspaceStatus.RUNNING,
                        0,                      // daily_cost
                ),
                false,                  // outdated
                name,
                null,                   // autostart_schedule
                null,                   // ttl_ms
                new Date().toInstant(), // last_used_at
        )
    }
}
