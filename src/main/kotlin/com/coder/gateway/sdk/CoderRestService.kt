package com.coder.gateway.sdk

import com.coder.gateway.models.SSHKeys
import com.coder.gateway.models.User
import com.coder.gateway.models.Workspace
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query


interface CoderRestService {

    @POST("auth/basic/login")
    fun authenticate(@Body loginRequest: LoginRequest): Call<LoginResponse>

    @GET("api/v0/users/me")
    fun me(@Header("Session-Token") sessionToken: String): Call<User>

    @GET("api/v0/workspaces")
    fun workspaces(@Header("Session-Token") sessionToken: String, @Query("users") users: String): Call<List<Workspace>>

    @GET("api/v0/users/{userId}/sshkey")
    fun sshKeys(@Header("Session-Token") sessionToken: String, @Path("userId") userID: String): Call<SSHKeys>

    @GET("api/private/webrtc/ice")
    fun iceServers(@Header("Session-Token") sessionToken: String): Call<IceServersWrapper>
}