package com.coder.gateway

import com.jetbrains.gateway.api.ConnectionRequestor
import com.jetbrains.gateway.api.GatewayConnectionHandle
import com.jetbrains.gateway.api.GatewayConnectionProvider

class CoderGatewayConnectionProvider: GatewayConnectionProvider {
    override suspend fun connect(parameters: Map<String, String>, requestor: ConnectionRequestor): GatewayConnectionHandle? {
        TODO("Not yet implemented")
    }

    override fun isApplicable(parameters: Map<String, String>): Boolean {
        TODO("Not yet implemented")
    }
}