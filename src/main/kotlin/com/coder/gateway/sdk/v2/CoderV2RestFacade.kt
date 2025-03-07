package com.coder.gateway.sdk.v2

import com.coder.gateway.sdk.v2.models.BuildInfo
import com.coder.gateway.sdk.v2.models.CreateWorkspaceBuildRequest
import com.coder.gateway.sdk.v2.models.Template
import com.coder.gateway.sdk.v2.models.User
import com.coder.gateway.sdk.v2.models.Workspace
import com.coder.gateway.sdk.v2.models.WorkspaceBuild
import com.coder.gateway.sdk.v2.models.WorkspaceResource
import com.coder.gateway.sdk.v2.models.WorkspacesResponse
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
     * Retrieves a specific workspace by owner and name.
     */
    @GET("api/v2/users/{user}/workspace/{workspace}")
    fun workspaceByOwnerAndName(
        @Path("user") user: String,
        @Path("workspace") workspace: String,
    ): Call<Workspace>

    /**
     * Retrieves all workspaces the authenticated user has access to.
     */
    @GET("api/v2/workspaces")
    fun workspaces(
        @Query("q") searchParams: String,
    ): Call<WorkspacesResponse>

    @GET("api/v2/buildinfo")
    fun buildInfo(): Call<BuildInfo>

    /**
     * Queues a new build to occur for a workspace.
     */
    @POST("api/v2/workspaces/{workspaceID}/builds")
    fun createWorkspaceBuild(
        @Path("workspaceID") workspaceID: UUID,
        @Body createWorkspaceBuildRequest: CreateWorkspaceBuildRequest,
    ): Call<WorkspaceBuild>

    @GET("api/v2/templates/{templateID}")
    fun template(
        @Path("templateID") templateID: UUID,
    ): Call<Template>

    @GET("api/v2/templateversions/{templateID}/resources")
    fun templateVersionResources(
        @Path("templateID") templateID: UUID,
    ): Call<List<WorkspaceResource>>
}
