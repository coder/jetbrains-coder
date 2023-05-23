package com.coder.gateway.services

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(
    name = "CoderSettingsState",
    storages = [Storage("coder-settings.xml", roamingType = RoamingType.DISABLED, exportable = true)]
)
class CoderSettingsState : PersistentStateComponent<CoderSettingsState> {
    var binarySource: String = ""
    var binaryDirectory: String = ""
    var dataDirectory: String = ""
    var enableDownloads: Boolean = true
    var enableBinaryDirectoryFallback: Boolean = false
    override fun getState(): CoderSettingsState {
        return this
    }

    override fun loadState(state: CoderSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }
}
