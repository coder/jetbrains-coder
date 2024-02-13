package com.coder.gateway

import kotlin.test.Test
import kotlin.test.assertEquals

import com.coder.gateway.util.toURL
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import kotlin.test.assertFailsWith

internal class CoderRemoteConnectionHandleTest {
    /**
     * Create, start, and return a server that uses the provided handler.
     */
    private fun mockServer(handler: HttpHandler): Pair<HttpServer, String> {
        val srv = HttpServer.create(InetSocketAddress(0), 0)
        srv.createContext("/", handler)
        srv.start()
        return Pair(srv, "http://localhost:" + srv.address.port)
    }

    /**
     * Create, start, and return a server that mocks redirects.
     */
    private fun mockRedirectServer(location: String, temp: Boolean): Pair<HttpServer, String> {
        return mockServer { exchange ->
            exchange.responseHeaders.set("Location", location)
            exchange.sendResponseHeaders(
                if (temp) HttpURLConnection.HTTP_MOVED_TEMP else HttpURLConnection.HTTP_MOVED_PERM,
                -1)
            exchange.close()
        }
    }

    @Test
    fun followsRedirects() {
        val (srv1, url1) = mockServer{ exchange ->
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1)
            exchange.close()
        }
        val (srv2, url2) = mockRedirectServer(url1, false)
        val (srv3, url3) = mockRedirectServer(url2, true)

        assertEquals(url1.toURL(), CoderRemoteConnectionHandle.resolveRedirects(java.net.URL(url3)))

        srv1.stop(0)
        srv2.stop(0)
        srv3.stop(0)
    }

    @Test
    fun followsMaximumRedirects() {
        val (srv, url) = mockRedirectServer(".", true)

        assertFailsWith(
            exceptionClass = Exception::class,
            block = { CoderRemoteConnectionHandle.resolveRedirects(java.net.URL(url)) })

        srv.stop(0)
    }
}