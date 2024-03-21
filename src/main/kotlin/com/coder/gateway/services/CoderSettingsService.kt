package com.coder.gateway.services

import com.coder.gateway.settings.CoderSettings
import com.coder.gateway.settings.CoderSettingsState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Provides Coder settings backed by the settings state service.
 *
 * This also provides some helpers such as resolving the provided settings with
 * environment variables and the defaults.
 *
 * For that reason, and to avoid presenting mutable values to most of the code
 * while letting the settings page still read and mutate the underlying state,
 * prefer using CoderSettingsService over CoderSettingsStateService.
 */
@Service(Service.Level.APP)
class CoderSettingsService : CoderSettings(service<CoderSettingsStateService>())

/**
 * Controls serializing and deserializing raw settings to and from disk.  Use
 * only when you need to directly mutate the settings (such as from the settings
 * page) and in tests, otherwise use CoderSettingsService.
 */
@Service(Service.Level.APP)
@State(
    name = "CoderSettingsState",
    storages = [Storage("coder-settings.xml", roamingType = RoamingType.DISABLED, exportable = true)]
)

class CoderSettingsStateService : CoderSettingsState(), PersistentStateComponent<CoderSettingsStateService> {
    override fun getState(): CoderSettingsStateService {
        return this
    }

    override fun loadState(state: CoderSettingsStateService) {
        XmlSerializerUtil.copyBean(state, this)
    }
}
