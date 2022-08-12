package com.coder.gateway.sdk.v2

import com.coder.gateway.sdk.v2.models.BuildInfo
import com.coder.gateway.sdk.v2.models.CreateWorkspaceBuildRequest
import com.coder.gateway.sdk.v2.models.User
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceBuild
import com.coder.gateway.sdk.v2.models.WorkspaceResource
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
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
    fun workspaces(@Query("q") searchParams: String): Call<List<Workspace>>

    @GET("api/v2/buildinfo")
    fun buildInfo(): Call<BuildInfo>

    @GET("api/v2/workspacebuilds/{buildID}/resources")
    fun workspaceResourceByBuild(@Path("buildID") build: UUID): Call<List<WorkspaceResource>>

    /**
     * Queues a new build to occur for a workspace.
     */
    @POST("api/v2/workspaces/{workspaceID}/builds")
    fun createWorkspaceBuild(@Path("workspaceID") workspaceID: UUID, @Body createWorkspaceBuildRequest: CreateWorkspaceBuildRequest): Call<WorkspaceBuild>
}