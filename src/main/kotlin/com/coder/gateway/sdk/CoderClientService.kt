package com.coder.gateway.sdk

import com.intellij.openapi.components.Service
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.gson.gson
import kotlinx.coroutines.runBlocking

@Service
class CoderClientService {
    private val httpClient = HttpClient(CIO) {
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
        install(ContentEncoding)
        install(ContentNegotiation) {
            gson() {
                setPrettyPrinting()
            }
        }
    }

    suspend fun authenthicateWithPassword(url: String, email: String, password: String) {
        val urlPath = url.trimEnd('/')
        val response = httpClient.post("$urlPath/auth/basic/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }

        println(">>> ${response.bodyAsText()}")
    }

    fun dispose() {
        httpClient.close()
    }
}

fun main() {
    val coderClient = CoderClientService()

    runBlocking {
        coderClient.authenthicateWithPassword("http://localhost:7080", "example@email.com", "password example")
    }

    coderClient.dispose()
}