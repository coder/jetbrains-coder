package com.coder.gateway.sdk

import spock.lang.*

@Unroll
class CoderRestClientTest extends Specification {
    def "gets headers"() {
        expect:
        CoderRestClient.getHeaders(new URL("http://localhost"), command) == expected

        where:
        command                         | expected
        null                            | [:]
        ""                              | [:]
        "printf 'foo=bar\\nbaz=qux'"    | ["foo": "bar", "baz": "qux"]
        "printf 'foo=bar\\r\\nbaz=qux'" | ["foo": "bar", "baz": "qux"]
        "printf 'foo=bar\\r\\n'"        | ["foo": "bar"]
        "printf 'foo=bar'"              | ["foo": "bar"]
        "printf 'foo=bar='"             | ["foo": "bar="]
        "printf 'foo=bar=baz'"          | ["foo": "bar=baz"]
        "printf 'foo='"                 | ["foo": ""]
    }

    def "fails to get headers"() {
        when:
        CoderRestClient.getHeaders(new URL("http://localhost"), command)

        then:
        thrown(Exception)

        where:
        command << [
            "printf 'foo=bar\\r\\n\\r\\n'",
            "printf '\\r\\nfoo=bar'",
            "printf '=foo'",
            "printf 'foo'",
            "printf '  =foo'",
            "printf 'foo  =bar'",
            "printf 'foo  foo=bar'",
            "printf ''",
            "exit 1",
        ]
    }

    @IgnoreIf({ os.windows })
    def "has access to environment variables"() {
        expect:
        CoderRestClient.getHeaders(new URL("http://localhost"), "printf url=\$CODER_URL") == [
            "url": "http://localhost",
        ]
    }

    @Requires({ os.windows })
    def "has access to environment variables"() {
        expect:
        CoderRestClient.getHeaders(new URL("http://localhost"), "printf url=%CODER_URL%") == [
            "url": "http://localhost",
        ]

    }
}
