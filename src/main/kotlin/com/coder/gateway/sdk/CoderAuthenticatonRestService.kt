package com.coder.gateway.sdk

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Coder does basic authentication on http, and once a session token is
 * received, everything else will happen on https.
 */
interface CoderAuthenticatonRestService {
    @POST("auth/basic/login")
    fun authenticate(@Body loginRequest: LoginRequest): Call<LoginResponse>
}