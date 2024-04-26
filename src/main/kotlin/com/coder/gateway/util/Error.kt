package com.coder.gateway.util

import com.coder.gateway.CoderGatewayBundle
import com.coder.gateway.cli.ex.ResponseException
import com.coder.gateway.sdk.ex.APIResponseException
import org.zeroturnaround.exec.InvalidExitValueException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

fun humanizeConnectionError(deploymentURL: URL, requireTokenAuth: Boolean, e: Exception): String {
    val reason = e.message ?: CoderGatewayBundle.message("gateway.connector.view.workspaces.connect.no-reason")
    return when (e) {
        is java.nio.file.AccessDeniedException ->
            CoderGatewayBundle.message(
                "gateway.connector.view.workspaces.connect.access-denied",
                e.file,
            )
        is UnknownHostException ->
            CoderGatewayBundle.message(
                "gateway.connector.view.workspaces.connect.unknown-host",
                e.message ?: deploymentURL.host,
            )
        is InvalidExitValueException ->
            CoderGatewayBundle.message(
                "gateway.connector.view.workspaces.connect.unexpected-exit",
                e.exitValue,
            )
        is APIResponseException -> {
            if (e.isUnauthorized) {
                CoderGatewayBundle.message(
                    if (requireTokenAuth) {
                        "gateway.connector.view.workspaces.connect.unauthorized-token"
                    } else {
                        "gateway.connector.view.workspaces.connect.unauthorized-other"
                    },
                    deploymentURL,
                )
            } else {
                reason
            }
        }
        is SocketTimeoutException -> {
            CoderGatewayBundle.message(
                "gateway.connector.view.workspaces.connect.timeout",
                deploymentURL,
            )
        }
        is ResponseException, is ConnectException -> {
            CoderGatewayBundle.message(
                "gateway.connector.view.workspaces.connect.download-failed",
                reason,
            )
        }
        is SSLHandshakeException -> {
            CoderGatewayBundle.message(
                "gateway.connector.view.workspaces.connect.ssl-error",
                deploymentURL.host,
                reason,
            )
        }
        else -> reason
    }
}
