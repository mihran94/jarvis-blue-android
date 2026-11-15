package com.jarvisblue.core.windows

/**
 * The pairing code that the Windows desktop writes.
 *
 * The desktop draws the code as a QR picture. A phone that has no camera, or
 * a person who does not want to scan, can paste the same text. The format is
 * the one the iPhone page reads, so there is only one code in the ecosystem.
 *
 *     JARVIS-BRIDGE-v1
 *     bridge=https://pc.example.lan:8765
 *     key=jarvis-android-v1
 *     secret=<the one time secret>
 *     expires=1764000000
 *     fp=<the SHA-256 of the desktop certificate>
 */
data class BridgePairing(
    val bridgeUrl: String,
    val keyId: String,
    val secret: String,
    val expiresAt: Long,
    val fingerprint: String,
) {
    /** The host of the desktop, without the scheme and without the port. */
    val host: String get() = bridgeUrl.substringAfter("://").substringBefore('/').substringBefore(':')

    /** The port of the desktop, or the default one. */
    val port: Int get() = bridgeUrl.substringAfter("//").substringAfter(':', "").substringBefore('/').toIntOrNull() ?: 8765
}

class PairingCodeError(message: String) : IllegalArgumentException(message)

object PairingCode {

    const val FORMAT = "JARVIS-BRIDGE-v1"
    const val MINIMUM_SECRET_LENGTH = 32
    const val DEFAULT_PORT = 8765

    /** Read a pairing code, or say why it cannot be used. */
    fun parse(text: String, now: Long = System.currentTimeMillis() / 1000): BridgePairing {
        if (text.isBlank()) {
            throw PairingCodeError("The pairing code is empty.")
        }
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.firstOrNull() != FORMAT) {
            throw PairingCodeError("That is not a JARVIS pairing code.")
        }
        val fields = mutableMapOf<String, String>()
        for (line in lines.drop(1)) {
            val separator = line.indexOf('=')
            if (separator <= 0) {
                throw PairingCodeError("The pairing code is damaged.")
            }
            fields[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
        }
        val bridgeUrl = fields["bridge"].orEmpty()
        val keyId = fields["key"].orEmpty()
        val secret = fields["secret"].orEmpty()
        val expiresAt = fields["expires"]?.toLongOrNull() ?: 0L
        val fingerprint = (fields["fp"].orEmpty()).lowercase()
        if (bridgeUrl.isEmpty()) {
            throw PairingCodeError("The pairing code has no bridge address.")
        }
        if (!bridgeUrl.startsWith("https://")) {
            throw PairingCodeError("The pairing code does not use HTTPS.")
        }
        if (keyId.isEmpty()) {
            throw PairingCodeError("The pairing code has no device key id.")
        }
        if (secret.length < MINIMUM_SECRET_LENGTH) {
            throw PairingCodeError("The pairing code has no usable secret.")
        }
        if (expiresAt <= 0L) {
            throw PairingCodeError("The pairing code has no expiry.")
        }
        if (expiresAt <= now) {
            throw PairingCodeError("That pairing code has expired. Pair the phone again.")
        }
        if (fingerprint.isNotEmpty() && !PinnedCertificate.isValidFingerprint(fingerprint)) {
            throw PairingCodeError("The pairing code has a damaged certificate fingerprint.")
        }
        return BridgePairing(
            bridgeUrl = bridgeUrl.trimEnd('/'),
            keyId = keyId,
            secret = secret,
            expiresAt = expiresAt,
            fingerprint = fingerprint,
        )
    }

    /** Write a pairing code, the way the desktop does. */
    fun build(
        bridgeUrl: String,
        keyId: String,
        secret: String,
        expiresAt: Long,
        fingerprint: String = "",
    ): String {
        val lines = mutableListOf(
            FORMAT,
            "bridge=${bridgeUrl.trim()}",
            "key=${keyId.trim()}",
            "secret=$secret",
            "expires=$expiresAt",
        )
        if (fingerprint.isNotBlank()) {
            lines.add("fp=${fingerprint.trim().lowercase()}")
        }
        return lines.joinToString("\n")
    }
}
