package com.coder.gateway.models

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.Attribute

class RecentWorkspaceConnection(
    @get:Attribute
    var coderWorkspaceHostname: String? = null,
    @get:Attribute
    var projectPath: String? = null,
    @get:Attribute
    var lastOpened: String? = null,
    @get:Attribute
    var ideProductCode: String? = null,
    @get:Attribute
    var ideBuildNumber: String? = null,
    @get:Attribute
    var downloadSource: String? = null,
    @get:Attribute
    var idePathOnHost: String? = null,
    @get:Attribute
    var webTerminalLink: String? = null,
    @get:Attribute
    var configDirectory: String? = null,
    @get:Attribute
    var name: String? = null,
) : BaseState(), Comparable<RecentWorkspaceConnection> {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as RecentWorkspaceConnection

        if (coderWorkspaceHostname != other.coderWorkspaceHostname) return false
        if (projectPath != other.projectPath) return false
        if (ideProductCode != other.ideProductCode) return false
        if (ideBuildNumber != other.ideBuildNumber) return false
        if (downloadSource != other.downloadSource) return false
        if (idePathOnHost != other.idePathOnHost) return false
        if (webTerminalLink != other.webTerminalLink) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (coderWorkspaceHostname?.hashCode() ?: 0)
        result = 31 * result + (projectPath?.hashCode() ?: 0)
        result = 31 * result + (ideProductCode?.hashCode() ?: 0)
        result = 31 * result + (ideBuildNumber?.hashCode() ?: 0)
        result = 31 * result + (downloadSource?.hashCode() ?: 0)
        result = 31 * result + (idePathOnHost?.hashCode() ?: 0)
        result = 31 * result + (webTerminalLink?.hashCode() ?: 0)

        return result
    }

    override fun compareTo(other: RecentWorkspaceConnection): Int {
        val i = other.coderWorkspaceHostname?.let { coderWorkspaceHostname?.compareTo(it) }
        if (i != null && i != 0) return i

        val j = other.projectPath?.let { projectPath?.compareTo(it) }
        if (j != null && j != 0) return j

        val k = other.ideProductCode?.let { ideProductCode?.compareTo(it) }
        if (k != null && k != 0) return k

        val l = other.ideBuildNumber?.let { ideBuildNumber?.compareTo(it) }
        if (l != null && l != 0) return l

        val m = other.downloadSource?.let { downloadSource?.compareTo(it) }
        if (m != null && m != 0) return m

        val n = other.idePathOnHost?.let { idePathOnHost?.compareTo(it) }
        if (n != null && n != 0) return n

        val o = other.webTerminalLink?.let { webTerminalLink?.compareTo(it) }
        if (o != null && o != 0) return o

        return 0
    }
}
