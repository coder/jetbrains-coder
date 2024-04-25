package com.coder.gateway.sdk.ex

import java.io.IOException

class AuthenticationResponseException(reason: String) : IOException(reason)

class WorkspaceResponseException(reason: String) : IOException(reason)

class TemplateResponseException(reason: String) : IOException(reason)
