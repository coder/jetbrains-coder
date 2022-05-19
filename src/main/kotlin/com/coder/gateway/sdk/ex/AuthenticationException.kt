package com.coder.gateway.sdk.ex

import java.io.IOException

class AuthenticationException(val reason: String) : IOException(reason)