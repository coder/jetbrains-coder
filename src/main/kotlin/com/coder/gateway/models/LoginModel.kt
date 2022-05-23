package com.coder.gateway.models

data class LoginModel(var uriScheme: UriScheme = UriScheme.HTTP, var host: String = "localhost", var port: Int = 7080, var email: String = "example@email.com", var password: String? = "")