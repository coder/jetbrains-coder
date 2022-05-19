package com.coder.gateway.models

internal data class LoginModel(var host: String = "localhost", var port: Int = 7080, var email: String = "example@email.com", var password: String? = "")