package com.coder.gateway.models

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.XCollection

class RecentWorkspaceConnectionState : BaseState() {
    @get:XCollection
    var recentConnections by treeSet<RecentWorkspaceConnection>()

    fun add(connection: RecentWorkspaceConnection): Boolean {
        // If the item is already there but with a different last updated
        // timestamp or config directory, remove it.
        recentConnections.remove(connection)
        val result = recentConnections.add(connection)
        if (result) incrementModificationCount()
        return result
    }

    fun remove(connection: RecentWorkspaceConnection): Boolean {
        val result = recentConnections.remove(connection)
        if (result) incrementModificationCount()
        return result
    }
}
