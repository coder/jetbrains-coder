package com.coder.gateway.util

import com.coder.gateway.settings.CoderTLSSettings
import okhttp3.internal.tls.OkHostnameVerifier
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.net.InetAddress
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Locale
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

fun sslContextFromPEMs(certPath: String, keyPath: String, caPath: String) : SSLContext {
    var km: Array<KeyManager>? = null
    if (certPath.isNotBlank() && keyPath.isNotBlank()) {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val certInputStream = FileInputStream(expand(certPath))
        val certChain = certificateFactory.generateCertificates(certInputStream)
        certInputStream.close()

        // Ideally we would use something like PemReader from BouncyCastle, but
        // BC is used by the IDE.  This makes using BC very impractical since
        // type casting will mismatch due to the different class loaders.
        val privateKeyPem = File(expand(keyPath)).readText()
        val start: Int = privateKeyPem.indexOf("-----BEGIN PRIVATE KEY-----")
        val end: Int = privateKeyPem.indexOf("-----END PRIVATE KEY-----", start)
        val pemBytes: ByteArray = Base64.getDecoder().decode(
            privateKeyPem.substring(start + "-----BEGIN PRIVATE KEY-----".length, end)
                .replace("\\s+".toRegex(), "")
        )

        val privateKey = try {
            val kf = KeyFactory.getInstance("RSA")
            val keySpec = PKCS8EncodedKeySpec(pemBytes)
            kf.generatePrivate(keySpec)
        } catch (e: InvalidKeySpecException) {
            val kf = KeyFactory.getInstance("EC")
            val keySpec = PKCS8EncodedKeySpec(pemBytes)
            kf.generatePrivate(keySpec)
        }

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null)
        certChain.withIndex().forEach {
            keyStore.setCertificateEntry("cert${it.index}", it.value as X509Certificate)
        }
        keyStore.setKeyEntry("key", privateKey, null, certChain.toTypedArray())

        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, null)
        km = keyManagerFactory.keyManagers
    }

    val sslContext = SSLContext.getInstance("TLS")

    val trustManagers = coderTrustManagers(caPath)
    sslContext.init(km, trustManagers, null)
    return sslContext
}

fun coderSocketFactory(settings: CoderTLSSettings) : SSLSocketFactory {
    val sslContext = sslContextFromPEMs(settings.certPath, settings.keyPath, settings.caPath)
    if (settings.altHostname.isBlank()) {
        return sslContext.socketFactory
    }

    return AlternateNameSSLSocketFactory(sslContext.socketFactory, settings.altHostname)
}

fun coderTrustManagers(tlsCAPath: String) : Array<TrustManager> {
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    if (tlsCAPath.isBlank()) {
        // return default trust managers
        trustManagerFactory.init(null as KeyStore?)
        return trustManagerFactory.trustManagers
    }


    val certificateFactory = CertificateFactory.getInstance("X.509")
    val caInputStream = FileInputStream(expand(tlsCAPath))
    val certChain = certificateFactory.generateCertificates(caInputStream)

    val truststore = KeyStore.getInstance(KeyStore.getDefaultType())
    truststore.load(null)
    certChain.withIndex().forEach {
        truststore.setCertificateEntry("cert${it.index}", it.value as X509Certificate)
    }
    trustManagerFactory.init(truststore)
    return trustManagerFactory.trustManagers.map { MergedSystemTrustManger(it as X509TrustManager) }.toTypedArray()
}

class AlternateNameSSLSocketFactory(private val delegate: SSLSocketFactory, private val alternateName: String) : SSLSocketFactory() {
    override fun getDefaultCipherSuites(): Array<String> {
        return delegate.defaultCipherSuites
    }

    override fun getSupportedCipherSuites(): Array<String> {
        return delegate.supportedCipherSuites
    }

    override fun createSocket(): Socket {
        val socket = delegate.createSocket() as SSLSocket
        customizeSocket(socket)
        return socket
    }

    override fun createSocket(host: String?, port: Int): Socket {
        val socket = delegate.createSocket(host,  port) as SSLSocket
        customizeSocket(socket)
        return socket
    }

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket {
        val socket = delegate.createSocket(host, port, localHost, localPort) as SSLSocket
        customizeSocket(socket)
        return socket
    }

    override fun createSocket(host: InetAddress?, port: Int): Socket {
        val socket = delegate.createSocket(host, port) as SSLSocket
        customizeSocket(socket)
        return socket
    }

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket {
        val socket = delegate.createSocket(address, port, localAddress, localPort) as SSLSocket
        customizeSocket(socket)
        return socket
    }

    override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket {
        val socket = delegate.createSocket(s, host, port, autoClose) as SSLSocket
        customizeSocket(socket)
        return socket
    }

    private fun customizeSocket(socket: SSLSocket) {
        val params = socket.sslParameters
        params.serverNames = listOf(SNIHostName(alternateName))
        socket.sslParameters = params
    }
}

class CoderHostnameVerifier(private val alternateName: String) : HostnameVerifier {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun verify(host: String, session: SSLSession): Boolean {
        if (alternateName.isEmpty()) {
            return OkHostnameVerifier.verify(host, session)
        }
        val certs = session.peerCertificates ?: return false
        for (cert in certs) {
            if (cert !is X509Certificate) {
                continue
            }
            val entries = cert.subjectAlternativeNames ?: continue
            for (entry in entries) {
                val kind = entry[0] as Int
                if (kind != 2) { // DNS Name
                    continue
                }
                val hostname = entry[1] as String
                logger.debug("Found cert hostname: $hostname")
                if (hostname.lowercase(Locale.getDefault()) == alternateName) {
                    return true
                }
            }
        }
        return false
    }
}

class MergedSystemTrustManger(private val otherTrustManager: X509TrustManager) : X509TrustManager {
    private val systemTrustManager : X509TrustManager
    init {
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(null as KeyStore?)
        systemTrustManager = trustManagerFactory.trustManagers.first { it is X509TrustManager } as X509TrustManager
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String?) {
        try {
            otherTrustManager.checkClientTrusted(chain, authType)
        } catch (e: CertificateException) {
            systemTrustManager.checkClientTrusted(chain, authType)
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String?) {
        try {
            otherTrustManager.checkServerTrusted(chain, authType)
        } catch (e: CertificateException) {
            systemTrustManager.checkServerTrusted(chain, authType)
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return otherTrustManager.acceptedIssuers + systemTrustManager.acceptedIssuers
    }
}
