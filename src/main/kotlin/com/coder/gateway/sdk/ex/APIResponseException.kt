package com.coder.gateway.sdk.ex

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class APIResponseException(action: String, url: URL, res: retrofit2.Response<*>) :
    IOException(
        "Unable to $action: url=$url, code=${res.code()}, details=${
            when (res.code()) {
                HttpURLConnection.HTTP_NOT_FOUND -> "The requested resource could not be found"
                else -> res.errorBody()?.charStream()?.use {
                    val text = it.readText()
                    // Be careful with the length because if you try to show a
                    // notification in Toolbox that is too large it crashes the
                    // application.
                    if (text.length > 500) {
                        "${text.substring(0, 500)}…"
                    } else {
                        text
                    }
                } ?: "no details provided"
            }}",
    ) {
    val isUnauthorized = res.code() == HttpURLConnection.HTTP_UNAUTHORIZED
}
