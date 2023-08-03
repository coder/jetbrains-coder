@file:Suppress("DialogTitleCapitalization")

package com.coder.gateway

import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.gateway.api.ConnectionRequestor
import com.jetbrains.gateway.api.GatewayConnectionHandle
import com.jetbrains.gateway.api.GatewayConnectionProvider

class CoderGatewayConnectionProvider : GatewayConnectionProvider {
    override suspend fun connect(parameters: Map<String, String>, requestor: ConnectionRequestor): GatewayConnectionHandle? {
        logger.debug("Launched Coder connection provider", parameters)
        CoderRemoteConnectionHandle().connect(parameters)
        return null
    }

    override fun isApplicable(parameters: Map<String, String>): Boolean {
        return parameters.areCoderType()
    }

    companion object {
        val logger = Logger.getInstance(CoderGatewayConnectionProvider::class.java.simpleName)
    }
}
