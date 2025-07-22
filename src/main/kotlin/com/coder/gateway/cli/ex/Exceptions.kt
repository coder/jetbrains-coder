package com.coder.gateway.cli.ex

class ResponseException(message: String, val code: Int) : Exception(message)

class SSHConfigFormatException(message: String) : Exception(message)

class MissingVersionException(message: String) : Exception(message)

class UnsignedBinaryExecutionDeniedException(message: String?) : Exception(message)
