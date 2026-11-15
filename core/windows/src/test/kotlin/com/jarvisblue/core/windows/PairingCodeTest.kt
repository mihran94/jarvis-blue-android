package com.jarvisblue.core.windows

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingCodeTest {

    private val secret = "s".repeat(40)
    private val expires = 4102444800L

    @Test
    fun `a code written by the desktop is read back`() {
        val text = PairingCode.build(
            bridgeUrl = "https://pc.example.lan:8765",
            keyId = "jarvis-android-v1",
            secret = secret,
            expiresAt = expires,
            fingerprint = "a".repeat(64),
        )
        val parsed = PairingCode.parse(text)
        assertEquals("https://pc.example.lan:8765", parsed.bridgeUrl)
        assertEquals("jarvis-android-v1", parsed.keyId)
        assertEquals(secret, parsed.secret)
        assertEquals(expires, parsed.expiresAt)
        assertEquals("a".repeat(64), parsed.fingerprint)
    }

    @Test
    fun `the host and the port come out of the address`() {
        val parsed = PairingCode.parse(
            PairingCode.build("https://192.168.1.20:8765", "key", secret, expires),
        )
        assertEquals("192.168.1.20", parsed.host)
        assertEquals(8765, parsed.port)
    }

    @Test
    fun `a code without a fingerprint is still usable`() {
        val parsed = PairingCode.parse(
            PairingCode.build("https://pc.example.lan:8765", "key", secret, expires),
        )
        assertEquals("", parsed.fingerprint)
    }

    @Test
    fun `a code that is not a pairing code is refused`() {
        assertThrows(PairingCodeError::class.java) { PairingCode.parse("hello") }
        assertThrows(PairingCodeError::class.java) { PairingCode.parse("") }
    }

    @Test
    fun `a code without HTTPS is refused`() {
        val text = "JARVIS-BRIDGE-v1\nbridge=http://pc.example.lan:8765\nkey=k\n" +
            "secret=$secret\nexpires=$expires"
        assertThrows(PairingCodeError::class.java) { PairingCode.parse(text) }
    }

    @Test
    fun `a code with a short secret is refused`() {
        val text = "JARVIS-BRIDGE-v1\nbridge=https://pc.example.lan:8765\nkey=k\n" +
            "secret=short\nexpires=$expires"
        assertThrows(PairingCodeError::class.java) { PairingCode.parse(text) }
    }

    @Test
    fun `an expired code is refused`() {
        val text = "JARVIS-BRIDGE-v1\nbridge=https://pc.example.lan:8765\nkey=k\n" +
            "secret=$secret\nexpires=1000"
        assertThrows(PairingCodeError::class.java) { PairingCode.parse(text) }
    }

    @Test
    fun `a damaged fingerprint is refused`() {
        val text = "JARVIS-BRIDGE-v1\nbridge=https://pc.example.lan:8765\nkey=k\n" +
            "secret=$secret\nexpires=$expires\nfp=not-a-fingerprint"
        assertThrows(PairingCodeError::class.java) { PairingCode.parse(text) }
    }

    @Test
    fun `a damaged line is refused`() {
        val text = "JARVIS-BRIDGE-v1\nbridge=https://pc.example.lan:8765\nbroken line"
        assertThrows(PairingCodeError::class.java) { PairingCode.parse(text) }
    }

    @Test
    fun `a fingerprint has the right shape`() {
        assertTrue(PinnedCertificate.isValidFingerprint("a".repeat(64)))
        assertTrue(PinnedCertificate.isValidFingerprint("A".repeat(64)))
        assertFalse(PinnedCertificate.isValidFingerprint("a".repeat(63)))
        assertFalse(PinnedCertificate.isValidFingerprint("z".repeat(64)))
        assertFalse(PinnedCertificate.isValidFingerprint(""))
    }
}
