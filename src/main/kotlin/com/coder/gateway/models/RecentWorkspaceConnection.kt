package com.coder.gateway.models

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.Attribute

class RecentWorkspaceConnection() : BaseState() {
    constructor(hostname: String, prjPath: String, openedAt: String, productCode: String, buildNumber: String, source: String) : this() {
        coderWorkspaceHostname = hostname
        projectPath = prjPath
        lastOpened = openedAt
        ideProductCode = productCode
        ideBuildNumber = buildNumber
        downloadSource = source
    }

    @get:Attribute
    var coderWorkspaceHostname by string()

    @get:Attribute
    var projectPath by string()

    @get:Attribute
    var lastOpened by string()

    @get:Attribute
    var ideProductCode by string()

    @get:Attribute
    var ideBuildNumber by string()

    @get:Attribute
    var downloadSource by string()
}