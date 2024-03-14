package com.coder.gateway.sdk.v2.models

import com.squareup.moshi.Json
import java.util.UUID

data class CreateWorkspaceBuildRequest(
    @Json(name = "template_version_id") val templateVersionID: UUID?,
    @Json(name = "transition") val transition: WorkspaceTransition,
    @Json(name = "dry_run") val dryRun: Boolean?,
    @Json(name = "state") val provisionerState: Array<Byte>?,
    // Orphan may be set for the Destroy transition.
    @Json(name = "orphan") val orphan: Boolean?,
    @Json(name = "parameter_values") val parameterValues: Array<CreateParameterRequest>?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CreateWorkspaceBuildRequest

        if (templateVersionID != other.templateVersionID) return false
        if (transition != other.transition) return false
        if (dryRun != other.dryRun) return false
        if (provisionerState != null) {
            if (other.provisionerState == null) return false
            if (!provisionerState.contentEquals(other.provisionerState)) return false
        } else if (other.provisionerState != null) return false
        if (orphan != other.orphan) return false
        if (parameterValues != null) {
            if (other.parameterValues == null) return false
            if (!parameterValues.contentEquals(other.parameterValues)) return false
        } else if (other.parameterValues != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = templateVersionID?.hashCode() ?: 0
        result = 31 * result + transition.hashCode()
        result = 31 * result + (dryRun?.hashCode() ?: 0)
        result = 31 * result + (provisionerState?.contentHashCode() ?: 0)
        result = 31 * result + (orphan?.hashCode() ?: 0)
        result = 31 * result + (parameterValues?.contentHashCode() ?: 0)
        return result
    }
}
