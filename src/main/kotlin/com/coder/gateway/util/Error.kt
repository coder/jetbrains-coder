package com.coder.gateway.util

import com.coder.gateway.cli.ex.ResponseException
import com.coder.gateway.sdk.ex.APIResponseException
import org.zeroturnaround.exec.InvalidExitValueException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

fun humanizeConnectionError(deploymentURL: URL, requireTokenAuth: Boolean, e: Exception): String {
    val reason = e.message ?: "No reason was provided."
    return when (e) {
        is java.nio.file.AccessDeniedException -> "Access denied to ${e.file}."
        is UnknownHostException -> "Unknown host ${e.message ?: deploymentURL.host}."
        is InvalidExitValueException -> "CLI exited unexpectedly with ${e.exitValue}."
        is APIResponseException -> {
            if (e.isUnauthorized) {
                if (requireTokenAuth) {
                    "Token was rejected by $deploymentURL; has your token expired?"
                } else {
                    "Authorization failed to $deploymentURL."
                }
            } else {
                reason
            }
        }
        is SocketTimeoutException -> "Unable to connect to $deploymentURL; is it up?"
        is ResponseException, is ConnectException -> "Failed to download Coder CLI: $reason"
        is SSLHandshakeException -> "Connection to $deploymentURL failed: $reason. See the <a href='https://coder.com/docs/user-guides/workspace-access/jetbrains#configuring-the-gateway-plugin-to-use-internal-certificates'>documentation for TLS certificates</a> for information on how to make your system trust certificates coming from your deployment."
        else -> reason
    }
}
