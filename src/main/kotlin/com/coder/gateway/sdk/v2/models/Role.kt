package com.coder.gateway.sdk.v2.models

import com.squareup.moshi.Json

data class Role(
    @Json(name = "name") val name: String,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "site") val site: Permission,
    // Org is a map of orgid to permissions. We represent orgid as a string.
    // We scope the organizations in the role so we can easily combine all the
    // roles.
    @Json(name = "org") val org: Map<String, List<Permission>>,
    @Json(name = "user") val user: List<Permission>,
)

data class Permission(
    @Json(name = "negate") val negate: Boolean,
    @Json(name = "resource_type") val resourceType: String,
    @Json(name = "action") val action: Action,
)

enum class Action {
    @Json(name = "create") CREATE,
    @Json(name = "read") READ,
    @Json(name = "update") UPDATE,
    @Json(name = "delete") DELETE
}
