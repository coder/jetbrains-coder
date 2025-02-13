package com.coder.gateway.views

import com.jetbrains.toolbox.api.ui.components.UiField


/**
 * A page for creating new environments.  It displays at the top of the
 * environments list.
 *
 * For now we just use this to display the deployment URL since we do not
 * support creating environments from the plugin.
 */
class NewEnvironmentPage(private val deploymentURL: String?) : CoderPage() {
    override fun getFields(): MutableList<UiField> = mutableListOf()
    override fun getTitle(): String = deploymentURL ?: ""
}
