package com.coder.gateway.models

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.XCollection

class RecentWorkspaceConnectionState : BaseState() {
    @get:XCollection
    var recentConnections by list<RecentWorkspaceConnection>()
}