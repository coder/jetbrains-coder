package com.coder.gateway.services

import com.coder.gateway.settings.CoderSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

/**
 * Provides Coder settings backed by the settings state service.
 *
 * This also provides some helpers such as resolving the provided settings with
 * environment variables and the defaults.
 *
 * For that reason, and to avoid presenting mutable values to most of the code
 * while letting the settings page still read and mutate the underlying state,
 * prefer using CoderSettingsService over CoderSettingsState directly.
 */
@Service(Service.Level.APP)
class CoderSettingsService : CoderSettings(service<CoderSettingsState>())
