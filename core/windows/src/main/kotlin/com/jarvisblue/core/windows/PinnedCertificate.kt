package com.jarvisblue.core.windows

import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

/**
 * Certificate pinning for the Windows bridge.
 *
 * The desktop serves the bridge over TLS with a certificate that is usually
 * self-signed, because it belongs to one machine on one private network. The
 * pairing code carries the SHA-256 fingerprint of that certificate, and the
 * phone trusts that one certificate and nothing else.
 *
 * Pinning is stronger than a name check: the certificate has to be exactly
 * the one that was paired, so a machine in the middle cannot hand over a
 * certificate of its own and still pass. The host name is therefore not
 * checked, because the certificate belongs to a private address such as
 * `192.168.1.20` and the phone reaches it under a name it does not know.
 */
object PinnedCertificate {

    /** The SHA-256 fingerprint of a certificate, as lowercase hex. */
    fun fingerprint(certificate: X509Certificate): String = fingerprintOf(certificate.encoded)

    /** The SHA-256 fingerprint of the DER form of a certificate. */
    fun fingerprintOf(der: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(der)
            .joinToString("") { "%02x".format(it) }

    /** Whether two fingerprints are the same certificate. */
    fun matches(expected: String, actual: String): Boolean =
        expected.trim().equals(actual.trim(), ignoreCase = true)

    /** Whether a fingerprint is written the way the desktop writes it. */
    fun isValidFingerprint(value: String): Boolean =
        value.length == 64 && value.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
}

/** A trust manager that accepts one certificate and nothing else. */
class PinnedTrustManager(private val expectedFingerprint: String) : X509TrustManager {

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("The phone does not accept client certificates.")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull()
            ?: throw CertificateException("The desktop sent no certificate.")
        val actual = PinnedCertificate.fingerprint(leaf)
        if (!PinnedCertificate.matches(expectedFingerprint, actual)) {
            throw CertificateException(
                "The desktop certificate is not the one that was paired. " +
                    "Expected $expectedFingerprint but the desktop sent $actual.",
            )
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/**
 * The host name check is skipped on purpose.
 *
 * The certificate is pinned, so the only thing that decides whether the
 * connection is allowed is whether it is the paired certificate. A name
 * check would only reject a correct certificate that was made for a private
 * address.
 */
class PinnedHostnameVerifier : HostnameVerifier {
    override fun verify(hostname: String?, session: SSLSession?): Boolean = true
}

/**
 * A client that trusts the paired certificate only.
 *
 * Without a fingerprint the client keeps the normal system trust, which is
 * what happens when the desktop sits behind a tunnel that terminates TLS
 * with a certificate from a real authority.
 */
fun pinnedHttpClient(fingerprint: String, base: OkHttpClient = BridgeClient.defaultClient()): OkHttpClient {
    if (!PinnedCertificate.isValidFingerprint(fingerprint)) {
        throw IllegalArgumentException("The certificate fingerprint is damaged.")
    }
    val trustManager = PinnedTrustManager(fingerprint)
    val context = SSLContext.getInstance("TLS")
    context.init(null, arrayOf(trustManager), SecureRandom())
    return base.newBuilder()
        .sslSocketFactory(context.socketFactory, trustManager)
        .hostnameVerifier(PinnedHostnameVerifier())
        .build()
}
