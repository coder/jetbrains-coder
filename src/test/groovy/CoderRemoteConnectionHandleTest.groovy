package com.coder.gateway

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class CoderRemoteConnectionHandleTest extends Specification {
    /**
     * Create, start, and return a server that uses the provided handler.
     */
    def mockServer(HttpHandler handler) {
        HttpServer srv = HttpServer.create(new InetSocketAddress(0), 0)
        srv.createContext("/", handler)
        srv.start()
        return [srv, "http://localhost:" + srv.address.port]
    }

    /**
     * Create, start, and return a server that mocks redirects.
     */
    def mockRedirectServer(String location, Boolean temp) {
        return mockServer(new HttpHandler() {
            void handle(HttpExchange exchange) {
                exchange.responseHeaders.set("Location", location)
                exchange.sendResponseHeaders(
                        temp ? HttpURLConnection.HTTP_MOVED_TEMP : HttpURLConnection.HTTP_MOVED_PERM,
                        -1)
                exchange.close()
            }
        })
    }

    def "follows redirects"() {
        given:
        def (srv1, url1) = mockServer(new HttpHandler() {
            void handle(HttpExchange exchange) {
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1)
                exchange.close()
            }
        })
        def (srv2, url2) = mockRedirectServer(url1, false)
        def (srv3, url3) = mockRedirectServer(url2, true)

        when:
        def resolved = CoderRemoteConnectionHandle.resolveRedirects(new URL(url3))

        then:
        resolved.toString() == url1

        cleanup:
        srv1.stop(0)
        srv2.stop(0)
        srv3.stop(0)
    }

    def "follows maximum redirects"() {
        given:
        def (srv, url) = mockRedirectServer(".", true)

        when:
        CoderRemoteConnectionHandle.resolveRedirects(new URL(url))

        then:
        thrown(Exception)

        cleanup:
        srv.stop(0)
    }
}
