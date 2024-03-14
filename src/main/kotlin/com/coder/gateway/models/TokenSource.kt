package com.coder.gateway.models

/**
 * Describes where a token came from.
 */
enum class TokenSource {
    CONFIG,    // Pulled from the Coder CLI config.
    USER,      // Input by the user.
    QUERY,     // From the Gateway link as a query parameter.
}

