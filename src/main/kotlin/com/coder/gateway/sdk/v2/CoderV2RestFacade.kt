package com.coder.gateway.sdk.v2

import com.coder.gateway.sdk.v2.models.BuildInfo
import com.coder.gateway.sdk.v2.models.User
import com.coder.gateway.sdk.v2.models.Workspace
import retrofit2.Call
import retrofit2.http.GET

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
}