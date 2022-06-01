package com.coder.gateway.sdk.v2

import com.coder.gateway.sdk.v2.models.AgentGitSSHKeys
import com.coder.gateway.sdk.v2.models.LoginWithPasswordRequest
import com.coder.gateway.sdk.v2.models.LoginWithPasswordResponse
import com.coder.gateway.sdk.v2.models.User
import com.coder.gateway.sdk.v2.models.Workspace
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface CoderV2RestFacade {

    /**
     * Retrieves a session token authenticating with an email and password.
     */
    @POST("api/v2/users/login")
    fun authenticate(@Body loginRequest: LoginWithPasswordRequest): Call<LoginWithPasswordResponse>

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

    @GET("api/v2/workspaceagents/me/gitsshkey")
    fun sshKeys(): Call<AgentGitSSHKeys>
}