package com.coder.gateway.services

import com.coder.gateway.models.RecentWorkspaceConnection
import com.coder.gateway.models.RecentWorkspaceConnectionState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger

@Service(Service.Level.APP)
@State(
    name = "CoderRecentWorkspaceConnections",
    storages = [Storage("coder-recent-workspace-connections.xml", roamingType = RoamingType.DISABLED, exportable = true)],
)
class CoderRecentWorkspaceConnectionsService : PersistentStateComponent<RecentWorkspaceConnectionState> {
    private var myState = RecentWorkspaceConnectionState()

    fun addRecentConnection(connection: RecentWorkspaceConnection) = myState.add(connection)

    fun removeConnection(connection: RecentWorkspaceConnection) = myState.remove(connection)

    fun getAllRecentConnections() = myState.recentConnections

    override fun getState(): RecentWorkspaceConnectionState = myState

    override fun loadState(loadedState: RecentWorkspaceConnectionState) {
        myState = loadedState
    }

    override fun noStateLoaded() {
        logger.info("No Coder recent connections loaded")
    }

    companion object {
        val logger = Logger.getInstance(CoderRecentWorkspaceConnectionsService::class.java.simpleName)
    }
}
