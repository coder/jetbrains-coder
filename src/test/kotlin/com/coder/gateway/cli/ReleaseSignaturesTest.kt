package com.coder.gateway.cli

import com.coder.gateway.cli.downloader.bundledSignatures
import org.bouncycastle.bcpg.ArmoredInputStream
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class ReleaseSignaturesTest {
    @Test
    fun `bundled signatures hold a signature from the trusted key for every platform of each release`() {
        val platforms = setOf(
            "coder-darwin-amd64",
            "coder-darwin-arm64",
            "coder-linux-amd64",
            "coder-linux-arm64",
            "coder-linux-armv7",
            "coder-windows-amd64.exe",
            "coder-windows-arm64.exe",
        )
        val version = Regex("""\d+\.\d+\.\d+""")
        val trustedKeys = javaClass.getResourceAsStream("/META-INF/trusted-keys/pgp-public.key")!!.use {
            PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(it), JcaKeyFingerprintCalculator())
        }

        val releases = bundledSignatures(javaClass.getResource("/META-INF/release-signatures.json")!!)

        assertTrue(releases.isNotEmpty())
        releases.forEach { (release, signatures) ->
            assertTrue(version.matches(release), release)
            assertEquals(platforms, signatures.keys, release)
            signatures.forEach { (binary, signature) ->
                val signatureList =
                    JcaPGPObjectFactory(ArmoredInputStream(signature.byteInputStream())).nextObject() as PGPSignatureList
                assertNotNull(trustedKeys.getPublicKey(signatureList[0].keyID), "$release $binary")
            }
        }
    }
}
