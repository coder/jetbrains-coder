package com.coder.gateway

class CoderSetupCommandException : Exception {

    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}