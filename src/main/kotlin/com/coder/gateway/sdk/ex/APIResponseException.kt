package com.coder.gateway.sdk.ex

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class APIResponseException(action: String, url: URL, res: retrofit2.Response<*>) :
    IOException(
        "Unable to $action: url=$url, code=${res.code()}, details=${
            res.errorBody()?.charStream()?.use {
                it.readText()
            } ?: "no details provided"}",
    ) {
    val isUnauthorized = res.code() == HttpURLConnection.HTTP_UNAUTHORIZED
}
