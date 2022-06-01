package com.coder.gateway.sdk.v1

import com.coder.gateway.models.v1.LoginRequest
import com.coder.gateway.models.v1.LoginResponse
import com.coder.gateway.models.v1.SSHKeys
import com.coder.gateway.models.v1.User
import com.coder.gateway.models.v1.Workspace
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query


interface CoderV1RestFacade {

    @POST("auth/basic/login") // V2 -> /api/v2/users/login
    fun authenticate(@Body loginRequest: LoginRequest): Call<LoginResponse>

    @GET("api/v0/users/me") // V2 -> /api/v2/users/%s
    fun me(@Header("Session-Token") sessionToken: String): Call<User>

    @GET("api/v0/workspaces") // V2 -> /api/v2/workspaces
    fun workspaces(@Header("Session-Token") sessionToken: String, @Query("users") users: String): Call<List<Workspace>>

    @GET("api/v0/users/{userId}/sshkey") //V2 -/api/v2/workspaceagents/me/gitsshkey
    fun sshKeys(@Header("Session-Token") sessionToken: String, @Path("userId") userID: String): Call<SSHKeys>

    @GET("api/private/webrtc/ice")
    fun iceServers(@Header("Session-Token") sessionToken: String): Call<IceServersWrapper>
}