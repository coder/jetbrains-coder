package com.coder.gateway.sdk.v2.models

import com.squareup.moshi.Json

/**
 * Contains build information for a Coder instance.
 *
 * @param externalUrl a URL referencing the current Coder version.
 *                   For production builds, this will link directly to a release.
 *                   For development builds, this will link to a commit.
 *
 * @param version the semantic version of the build.
 */
data class BuildInfo(
    @Json(name = "external_url") val externalUrl: String,
    @Json(name = "version") val version: String
)
