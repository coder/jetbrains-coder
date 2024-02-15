package com.coder.gateway.sdk

import java.net.ProxySelector

/**
 * Holds proxy information.  Exists only to interface with tests since they
 * cannot create an HttpConfigurable instance.
 */
data class ProxyValues (
    val username: String?,
    val password: String?,
    val useAuth: Boolean,
    val selector: ProxySelector,
)