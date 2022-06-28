package com.coder.gateway.sdk.v2

import com.coder.gateway.sdk.v2.models.BuildInfo
import com.coder.gateway.sdk.v2.models.User
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceResource
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.UUID

interface CoderV2RestFacade {

    /**
     * Retrieves details about the authenticated user.
     */
    @GET("api/v2/users/me")
    fun me(): Call<User>

    /**
     * Retrieves all workspaces the authenticated user has access to.
     */
    @GET("api/v2/workspaces")
    fun workspaces(): Call<List<Workspace>>

    @GET("api/v2/buildinfo")
    fun buildInfo(): Call<BuildInfo>

    @GET("api/v2/workspacebuilds/{buildID}/resources")
    fun workspaceResourceByBuild(@Path("buildID") build: UUID): Call<List<WorkspaceResource>>
}