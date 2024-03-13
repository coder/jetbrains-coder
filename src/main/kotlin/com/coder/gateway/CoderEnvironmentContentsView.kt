package com.coder.gateway

import com.coder.gateway.cli.CoderCLIManager
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceAgent
import com.jetbrains.toolbox.gateway.environments.SshEnvironmentContentsView
import com.jetbrains.toolbox.gateway.ssh.SshConnectionInfo
import java.net.URL
import java.util.concurrent.CompletableFuture

class CoderEnvironmentContentsView(
    private val url: URL,
    private val workspace: Workspace,
    private val agent: WorkspaceAgent,
) : SshEnvironmentContentsView {
    override fun getConnectionInfo(): CompletableFuture<SshConnectionInfo> {
        return CompletableFuture.completedFuture(object : SshConnectionInfo {
            override fun getHost(): String {
                return CoderCLIManager.getHostName(url, "${workspace.name}.${agent.name}")
            }

            override fun getPort(): Int {
                // This is ignored by the Coder proxy command.
                return 22
            }

            override fun getUserName(): String {
                // This is ignored by the Coder proxy command.
                return "coder"
            }
        })
    }
}
