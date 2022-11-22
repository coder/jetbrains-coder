package com.coder.gateway.sdk.v2.models

import com.google.gson.annotations.SerializedName

data class Role(
    @SerializedName("name") val name: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("site") val site: Permission,
    // Org is a map of orgid to permissions. We represent orgid as a string.
    // We scope the organizations in the role so we can easily combine all the
    // roles.
    @SerializedName("org") val org: Map<String, List<Permission>>,
    @SerializedName("user") val user: List<Permission>,

    )

data class Permission(
    @SerializedName("negate") val negate: Boolean,
    @SerializedName("resource_type") val resourceType: String,
    @SerializedName("action") val action: Action,
)

enum class Action {
    @SerializedName("create")
    CREATE,

    @SerializedName("read")
    READ,

    @SerializedName("update")
    UPDATE,

    @SerializedName("delete")
    DELETE
}