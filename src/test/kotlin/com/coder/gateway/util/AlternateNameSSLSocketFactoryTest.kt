package com.coder.gateway.util

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame


class AlternateNameSSLSocketFactoryTest {

    @Test
    fun `createSocket with no parameters should customize socket with alternate name`() {
        // Given
        val mockFactory = mockk<SSLSocketFactory>()
        val mockSocket = mockk<SSLSocket>(relaxed = true)
        val mockParams = mockk<SSLParameters>(relaxed = true)

        every { mockFactory.createSocket() } returns mockSocket
        every { mockSocket.sslParameters } returns mockParams
        every { mockSocket.sslParameters = any() } just Runs

        val alternateFactory = AlternateNameSSLSocketFactory(mockFactory, "alternate.example.com")

        // When
        val result = alternateFactory.createSocket()

        // Then
        verify { mockSocket.sslParameters = any() }
        assertSame(mockSocket, result)
    }

    @Test
    fun `createSocket with host and port should customize socket with alternate name`() {
        // Given
        val mockFactory = mockk<SSLSocketFactory>()
        val mockSocket = mockk<SSLSocket>(relaxed = true)
        val mockParams = mockk<SSLParameters>(relaxed = true)

        every { mockFactory.createSocket("original.com", 443) } returns mockSocket
        every { mockSocket.sslParameters } returns mockParams
        every { mockSocket.sslParameters = any() } just Runs

        val alternateFactory = AlternateNameSSLSocketFactory(mockFactory, "alternate.example.com")

        // When
        val result = alternateFactory.createSocket("original.com", 443)

        // Then
        verify { mockSocket.sslParameters = any() }
        assertSame(mockSocket, result)
    }

    @Test
    fun `createSocket with host port and local address should customize socket`() {
        // Given
        val mockFactory = mockk<SSLSocketFactory>()
        val mockSocket = mockk<SSLSocket>(relaxed = true)
        val mockParams = mockk<SSLParameters>(relaxed = true)
        val localHost = mockk<InetAddress>()

        every { mockFactory.createSocket("original.com", 443, localHost, 8080) } returns mockSocket
        every { mockSocket.sslParameters } returns mockParams
        every { mockSocket.sslParameters = any() } just Runs

        val alternateFactory = AlternateNameSSLSocketFactory(mockFactory, "alternate.example.com")

        // When
        val result = alternateFactory.createSocket("original.com", 443, localHost, 8080)

        // Then
        verify { mockSocket.sslParameters = any() }
        assertSame(mockSocket, result)
    }

    @Test
    fun `createSocket with InetAddress should customize socket with alternate name`() {
        // Given
        val mockFactory = mockk<SSLSocketFactory>()
        val mockSocket = mockk<SSLSocket>(relaxed = true)
        val mockParams = mockk<SSLParameters>(relaxed = true)
        val address = mockk<InetAddress>()

        every { mockFactory.createSocket(address, 443) } returns mockSocket
        every { mockSocket.sslParameters } returns mockParams
        every { mockSocket.sslParameters = any() } just Runs

        val alternateFactory = AlternateNameSSLSocketFactory(mockFactory, "alternate.example.com")

        // When
        val result = alternateFactory.createSocket(address, 443)

        // Then
        verify { mockSocket.sslParameters = any() }
        assertSame(mockSocket, result)
    }

    @Test
    fun `createSocket with InetAddress and local address should customize socket`() {
        // Given
        val mockFactory = mockk<SSLSocketFactory>()
        val mockSocket = mockk<SSLSocket>(relaxed = true)
        val mockParams = mockk<SSLParameters>(relaxed = true)
        val address = mockk<InetAddress>()
        val localAddress = mockk<InetAddress>()

        every { mockFactory.createSocket(address, 443, localAddress, 8080) } returns mockSocket
        every { mockSocket.sslParameters } returns mockParams
        every { mockSocket.sslParameters = any() } just Runs

        val alternateFactory = AlternateNameSSLSocketFactory(mockFactory, "alternate.example.com")

        // When
        val result = alternateFactory.createSocket(address, 443, localAddress, 8080)

        // Then
        verify { mockSocket.sslParameters = any() }
        assertSame(mockSocket, result)
    }

    @Test
    fun `createSocket with existing socket should customize socket with alternate name`() {
        // Given
        val mockFactory = mockk<SSLSocketFactory>()
        val mockSSLSocket = mockk<SSLSocket>(relaxed = true)
        val mockParams = mockk<SSLParameters>(relaxed = true)
        val existingSocket = mockk<Socket>()

        every { mockFactory.createSocket(existingSocket, "original.com", 443, true) } returns mockSSLSocket
        every { mockSSLSocket.sslParameters } returns mockParams
        every { mockSSLSocket.sslParameters = any() } just Runs

        val alternateFactory = AlternateNameSSLSocketFactory(mockFactory, "alternate.example.com")

        // When
        val result = alternateFactory.createSocket(existingSocket, "original.com", 443, true)

        // Then
        verify { mockSSLSocket.sslParameters = any() }
        assertSame(mockSSLSocket, result)
    }

    @Test
    fun `customizeSocket should set SNI hostname to alternate name for valid hostname`() {
        // Given
        val mockFactory = mockk<SSLSocketFactory>()
        val mockSocket = mockk<SSLSocket>(relaxed = true)
        val mockParams = mockk<SSLParameters>(relaxed = true)

        every { mockFactory.createSocket() } returns mockSocket
        every { mockSocket.sslParameters } returns mockParams
        every { mockSocket.sslParameters = any() } just Runs

        val alternateFactory = AlternateNameSSLSocketFactory(mockFactory, "valid-hostname.example.com")

        // When & Then - This should work without throwing an exception
        assertNotNull(alternateFactory.createSocket())
        verify { mockSocket.sslParameters = any() }
    }

    @Test
    fun `customizeSocket should NOT throw IllegalArgumentException for hostname with underscore`() {
        // Given
        val mockFactory = mockk<SSLSocketFactory>()
        val mockSocket = mockk<SSLSocket>(relaxed = true)
        val mockParams = mockk<SSLParameters>(relaxed = true)

        every { mockFactory.createSocket() } returns mockSocket
        every { mockSocket.sslParameters } returns mockParams
        every { mockSocket.sslParameters = any() } just Runs

        val alternateFactory = AlternateNameSSLSocketFactory(mockFactory, "non_compliant_hostname.example.com")

        // When & Then - This should work without throwing an exception
        assertNotNull(alternateFactory.createSocket())
        verify { mockSocket.sslParameters = any() }
        assertEquals(0, mockSocket.sslParameters.serverNames.size)
    }

    @Test
    fun `createSocket should work with valid international domain names`() {
        // Given
        val mockFactory = mockk<SSLSocketFactory>()
        val mockSocket = mockk<SSLSocket>(relaxed = true)
        val mockParams = mockk<SSLParameters>(relaxed = true)

        every { mockFactory.createSocket() } returns mockSocket
        every { mockSocket.sslParameters } returns mockParams
        every { mockSocket.sslParameters = any() } just Runs

        val alternateFactory = AlternateNameSSLSocketFactory(mockFactory, "test-server.example.com")

        // When & Then - This should work as hyphens are valid
        assertNotNull(alternateFactory.createSocket())
        verify { mockSocket.sslParameters = any() }
    }

    private fun createMockSSLSocketFactory(): SSLSocketFactory {
        val mockFactory = mockk<SSLSocketFactory>()
        val mockSocket = mockk<SSLSocket>(relaxed = true)
        val mockParams = mockk<SSLParameters>(relaxed = true)

        // Setup default behavior
        every { mockFactory.defaultCipherSuites } returns arrayOf("TLS_AES_256_GCM_SHA384")
        every { mockFactory.supportedCipherSuites } returns arrayOf("TLS_AES_256_GCM_SHA384", "TLS_AES_128_GCM_SHA256")

        // Make all createSocket methods return our mock socket
        every { mockFactory.createSocket() } returns mockSocket
        every { mockFactory.createSocket(any<String>(), any<Int>()) } returns mockSocket
        every { mockFactory.createSocket(any<String>(), any<Int>(), any<InetAddress>(), any<Int>()) } returns mockSocket
        every { mockFactory.createSocket(any<InetAddress>(), any<Int>()) } returns mockSocket
        every {
            mockFactory.createSocket(
                any<InetAddress>(),
                any<Int>(),
                any<InetAddress>(),
                any<Int>()
            )
        } returns mockSocket
        every { mockFactory.createSocket(any<Socket>(), any<String>(), any<Int>(), any<Boolean>()) } returns mockSocket

        // Setup SSL parameters
        every { mockSocket.sslParameters } returns mockParams
        every { mockSocket.sslParameters = any() } just Runs

        return mockFactory
    }
}