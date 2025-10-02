package com.coder.gateway.util

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSession
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoderHostnameVerifierTest {

    private lateinit var sslSession: SSLSession
    private lateinit var x509Certificate: X509Certificate
    private lateinit var logger: Logger
    private lateinit var verifier: CoderHostnameVerifier

    @BeforeEach
    fun setUp() {
        sslSession = mockk()
        x509Certificate = mockk()
        logger = mockk(relaxed = true)
    }

    @Test
    fun `should return false when no certificates are present`() {
        // Given
        verifier = CoderHostnameVerifier("test_host.example.com")
        every { sslSession.peerCertificates } returns null

        // When
        val result = verifier.verify("example.com", sslSession)

        // Then
        assertFalse(result)
    }

    @Test
    fun `should return false when certificates array is empty`() {
        // Given
        verifier = CoderHostnameVerifier("test_host.example.com")
        every { sslSession.peerCertificates } returns arrayOf()

        // When
        val result = verifier.verify("example.com", sslSession)

        // Then
        assertFalse(result)
    }

    @Test
    fun `should return true when SAN contains matching alternate name with underscore`() {
        // Given
        val alternateNameWithUnderscore = "test_server.internal.com"
        verifier = CoderHostnameVerifier(alternateNameWithUnderscore)

        // Mock certificate with SAN containing underscore
        val sanEntries = listOf(
            listOf(2, "example.com"),                    // Standard DNS name
            listOf(2, "test_server.internal.com"),       // SAN with underscore
            listOf(2, "api.example.com")                 // Another DNS name
        )

        every { sslSession.peerCertificates } returns arrayOf(x509Certificate)
        every { x509Certificate.subjectAlternativeNames } returns sanEntries

        // When
        val result = verifier.verify("example.com", sslSession)

        // Then
        assertTrue(result, "Should return true when SAN contains matching alternate name with underscore")
    }

    @Test
    fun `should return false when SAN does not contain matching alternate name`() {
        // Given
        verifier = CoderHostnameVerifier("missing_host.example.com")

        // Mock certificate without matching SAN
        val sanEntries = listOf(
            listOf(2, "example.com"),
            listOf(2, "api.example.com"),
            listOf(2, "different_host.example.com")
        )

        every { sslSession.peerCertificates } returns arrayOf(x509Certificate)
        every { x509Certificate.subjectAlternativeNames } returns sanEntries

        // When
        val result = verifier.verify("example.com", sslSession)

        // Then
        assertFalse(result, "Should return false when SAN does not contain matching alternate name")
    }

    @Test
    fun `should ignore non-DNS SAN entries`() {
        // Given
        verifier = CoderHostnameVerifier("test_host.example.com")

        // Mock certificate with various SAN types
        val sanEntries = listOf(
            listOf(1, "user@example.com"),           // Email (type 1)
            listOf(6, "http://example.com"),         // URI (type 6)
            listOf(7, "192.168.1.1"),               // IP Address (type 7)
            listOf(2, "test_host.example.com")       // DNS Name (type 2) - this should match
        )

        every { sslSession.peerCertificates } returns arrayOf(x509Certificate)
        every { x509Certificate.subjectAlternativeNames } returns sanEntries

        // When
        val result = verifier.verify("example.com", sslSession)

        // Then
        assertTrue(result, "Should ignore non-DNS SAN entries and find the matching DNS entry")
    }

    @Test
    fun `should return false when certificate has no SAN extension`() {
        // Given
        verifier = CoderHostnameVerifier("test_host.example.com")

        every { sslSession.peerCertificates } returns arrayOf(x509Certificate)
        every { x509Certificate.subjectAlternativeNames } returns null

        // When
        val result = verifier.verify("example.com", sslSession)

        // Then
        assertFalse(result, "Should return false when certificate has no SAN extension")
    }

    @Test
    fun `should handle multiple certificates and find match in second certificate`() {
        // Given
        verifier = CoderHostnameVerifier("api_server.internal.com")

        val cert1Mock = mockk<X509Certificate>()
        val cert2Mock = mockk<X509Certificate>()

        // First certificate has no matching SAN
        val sanEntries1 = listOf(
            listOf(2, "example.com"),
            listOf(2, "www.example.com")
        )

        // Second certificate has matching SAN with underscore
        val sanEntries2 = listOf(
            listOf(2, "internal.com"),
            listOf(2, "api_server.internal.com")
        )

        every { sslSession.peerCertificates } returns arrayOf(cert1Mock, cert2Mock)
        every { cert1Mock.subjectAlternativeNames } returns sanEntries1
        every { cert2Mock.subjectAlternativeNames } returns sanEntries2

        // When
        val result = verifier.verify("example.com", sslSession)

        // Then
        assertTrue(result, "Should find match in second certificate")
    }

    @Test
    fun `should handle non-X509 certificates gracefully`() {
        // Given
        verifier = CoderHostnameVerifier("test_host.example.com")

        val nonX509Cert = mockk<Certificate>()  // Not an X509Certificate
        every { sslSession.peerCertificates } returns arrayOf(nonX509Cert, x509Certificate)

        val sanEntries = listOf(
            listOf(2, "test_host.example.com")
        )
        every { x509Certificate.subjectAlternativeNames } returns sanEntries

        // When
        val result = verifier.verify("example.com", sslSession)

        // Then
        assertTrue(result, "Should skip non-X509 certificates and process X509 certificates")
    }

    @Test
    fun `should reproduce the underscore bug scenario`() {
        // Given - This test reproduces the exact scenario from the bug report
        val problematicHostname = "coder_instance.dev.company.com"
        verifier = CoderHostnameVerifier(problematicHostname)

        // Mock a certificate that would be valid but contains underscore in SAN
        val sanEntries = listOf(
            listOf(2, "dev.company.com"),
            listOf(2, "coder_instance.dev.company.com"),  // This contains underscore
            listOf(2, "*.dev.company.com")
        )

        every { x509Certificate.subjectAlternativeNames } returns sanEntries
        every { sslSession.peerCertificates } returns arrayOf(x509Certificate)

        // When
        val result = verifier.verify("dev.company.com", sslSession)

        // Then
        assertTrue(result, "Should successfully verify hostname with underscore in SAN")

        // Additional verification that the problematic hostname would be found
        val foundHostnames = mutableListOf<String>()
        sanEntries.forEach { entry ->
            if (entry[0] == 2) {  // DNS name type
                foundHostnames.add(entry[1] as String)
            }
        }

        assertTrue(
            foundHostnames.any { it.equals(problematicHostname, ignoreCase = true) },
            "Certificate should contain the problematic hostname with underscore"
        )
    }

    @Test
    fun `should handle edge case with empty SAN list`() {
        // Given
        verifier = CoderHostnameVerifier("test_host.example.com")

        every { sslSession.peerCertificates } returns arrayOf(x509Certificate)
        every { x509Certificate.subjectAlternativeNames } returns emptyList()

        // When
        val result = verifier.verify("example.com", sslSession)

        // Then
        assertFalse(result, "Should return false when SAN list is empty")
    }
}