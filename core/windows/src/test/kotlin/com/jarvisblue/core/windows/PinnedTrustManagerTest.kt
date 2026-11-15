package com.jarvisblue.core.windows

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.cert.CertificateException
import java.security.cert.X509Certificate

class PinnedTrustManagerTest {

    /** A certificate that stands in for the desktop one. */
    private class FakeCertificate(private val bytes: ByteArray) : X509Certificate() {
        override fun getEncoded(): ByteArray = bytes
        override fun checkValidity() = Unit
        override fun checkValidity(p1: java.util.Date?) = Unit
        override fun getVersion(): Int = 3
        override fun getSerialNumber(): java.math.BigInteger = java.math.BigInteger.ONE
        override fun getIssuerDN() = javax.security.auth.x500.X500Principal("CN=test")
        override fun getSubjectDN() = javax.security.auth.x500.X500Principal("CN=test")
        override fun getNotBefore() = java.util.Date(0)
        override fun getNotAfter() = java.util.Date(Long.MAX_VALUE)
        override fun getTBSCertificate() = byteArrayOf()
        override fun getSignature() = byteArrayOf()
        override fun getSigAlgName() = "SHA256withRSA"
        override fun getSigAlgOID() = null
        override fun getSigAlgParams() = null
        override fun getIssuerUniqueID() = null
        override fun getSubjectUniqueID() = null
        override fun getKeyUsage() = booleanArrayOf()
        override fun getBasicConstraints(): Int = 0
        override fun getPublicKey() = null
        override fun hasUnsupportedCriticalExtension() = false
        override fun getCriticalExtensionOIDs(): MutableSet<String>? = null
        override fun getNonCriticalExtensionOIDs(): MutableSet<String>? = null
        override fun getExtendedKeyUsage(): MutableList<String>? = null
        override fun getExtensionValue(oid: String): ByteArray? = null
        override fun verify(key: java.security.PublicKey) = Unit
        override fun verify(key: java.security.PublicKey, sigProvider: String) = Unit
        override fun toString(): String = "FakeCertificate"
    }

    private fun certificate(seed: String) = FakeCertificate(seed.toByteArray())

    @Test
    fun `the fingerprint is the sha-256 of the certificate`() {
        val fingerprint = PinnedCertificate.fingerprint(certificate("desktop"))
        assertEquals(64, fingerprint.length)
        assertTrue(fingerprint.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `the pinned certificate is accepted`() {
        val manager = PinnedTrustManager(PinnedCertificate.fingerprint(certificate("desktop")))
        manager.checkServerTrusted(arrayOf(certificate("desktop")), "RSA")
    }

    @Test
    fun `a different certificate is refused`() {
        val manager = PinnedTrustManager(PinnedCertificate.fingerprint(certificate("desktop")))
        assertThrows(CertificateException::class.java) {
            manager.checkServerTrusted(arrayOf(certificate("someone else")), "RSA")
        }
    }

    @Test
    fun `the comparison does not care about the case`() {
        val manager = PinnedTrustManager(PinnedCertificate.fingerprint(certificate("desktop")).uppercase())
        manager.checkServerTrusted(arrayOf(certificate("desktop")), "RSA")
    }

    @Test
    fun `an empty chain is refused`() {
        val manager = PinnedTrustManager(PinnedCertificate.fingerprint(certificate("desktop")))
        assertThrows(CertificateException::class.java) {
            manager.checkServerTrusted(emptyArray(), "RSA")
        }
    }

    @Test
    fun `the phone does not accept a client certificate`() {
        val manager = PinnedTrustManager(PinnedCertificate.fingerprint(certificate("desktop")))
        assertThrows(CertificateException::class.java) {
            manager.checkClientTrusted(arrayOf(certificate("desktop")), "RSA")
        }
    }

    @Test
    fun `a client with a damaged fingerprint is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            pinnedHttpClient("not-a-fingerprint")
        }
    }
}
