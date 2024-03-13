package com.coder.gateway.sdk.v2.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.UUID

/**
 * Represents a deployment of a template. It references a specific version and
 * can be updated.
 */
@JsonClass(generateAdapter = true)
data class Workspace(
    @Json(name = "id") val id: UUID,
    @Json(name = "template_id") val templateID: UUID,
    @Json(name = "template_name") val templateName: String,
    @Json(name = "template_display_name") val templateDisplayName: String,
    @Json(name = "template_icon") val templateIcon: String,
    @Json(name = "latest_build") val latestBuild: WorkspaceBuild,
    @Json(name = "outdated") val outdated: Boolean,
    @Json(name = "name") val name: String,
)
