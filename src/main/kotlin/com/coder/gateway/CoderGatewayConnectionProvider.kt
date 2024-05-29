@file:Suppress("DialogTitleCapitalization")

package com.coder.gateway

import com.coder.gateway.services.CoderSettingsService
import com.coder.gateway.util.handleLink
import com.coder.gateway.util.isCoder
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.gateway.api.ConnectionRequestor
import com.jetbrains.gateway.api.GatewayConnectionHandle
import com.jetbrains.gateway.api.GatewayConnectionProvider

// CoderGatewayConnectionProvider handles connecting via a Gateway link such as
// jetbrains-gateway://connect#type=coder.
class CoderGatewayConnectionProvider : GatewayConnectionProvider {
    private val settings: CoderSettingsService = service<CoderSettingsService>()

    override suspend fun connect(
        parameters: Map<String, String>,
        requestor: ConnectionRequestor,
    ): GatewayConnectionHandle? {
        CoderRemoteConnectionHandle().connect { indicator ->
            logger.debug("Launched Coder link handler", parameters)
            handleLink(parameters, settings) {
                indicator.text = it
            }
        }
        return null
    }

    override fun isApplicable(parameters: Map<String, String>): Boolean {
        return parameters.isCoder()
    }

    companion object {
        val logger = Logger.getInstance(CoderGatewayConnectionProvider::class.java.simpleName)
    }
}
