package com.example

import com.example.crypto.TotpGenerator
import com.example.data.model.HashAlgorithm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies Zayan Authenticator's TOTP engine against ALL official RFC 6238 Appendix B test vectors
 * for SHA-1 (20-byte key), SHA-256 (32-byte key), and SHA-512 (64-byte key), plus 6-digit/8-digit,
 * 30s/60s intervals, and Base32 validation.
 */
class TotpRfc6238Test {

    // Official RFC 6238 Appendix B ASCII test seeds
    private val sha1Seed = "12345678901234567890".toByteArray(Charsets.US_ASCII)
    private val sha256Seed = "12345678901234567890123456789012".toByteArray(Charsets.US_ASCII)
    private val sha512Seed =
        "1234567890123456789012345678901234567890123456789012345678901234".toByteArray(Charsets.US_ASCII)

    @Test
    fun `rfc6238 official appendix B test vectors match for SHA-1 SHA-256 and SHA-512`() {
        data class Vector(val time: Long, val sha1: String, val sha256: String, val sha512: String)

        val vectors = listOf(
            Vector(59L, "94287082", "46119246", "90693936"),
            Vector(1111111109L, "07081804", "68084774", "25091201"),
            Vector(1111111111L, "14050471", "67062674", "99943326"),
            Vector(1234567890L, "89005924", "91819424", "93441116"),
            Vector(2000000000L, "69279037", "90698825", "38618901"),
            Vector(20000000000L, "65353130", "77737706", "47863826")
        )

        for (v in vectors) {
            assertEquals(
                "SHA-1 mismatch at t=${v.time}",
                v.sha1,
                TotpGenerator.generateCodeFromBytes(sha1Seed, v.time, periodSeconds = 30, digits = 8, algorithm = HashAlgorithm.SHA1)
            )
            assertEquals(
                "SHA-256 mismatch at t=${v.time}",
                v.sha256,
                TotpGenerator.generateCodeFromBytes(sha256Seed, v.time, periodSeconds = 30, digits = 8, algorithm = HashAlgorithm.SHA256)
            )
            assertEquals(
                "SHA-512 mismatch at t=${v.time}",
                v.sha512,
                TotpGenerator.generateCodeFromBytes(sha512Seed, v.time, periodSeconds = 30, digits = 8, algorithm = HashAlgorithm.SHA512)
            )
        }
    }

    @Test
    fun `6-digit 30s and 60s intervals work accurately via Base32 secret`() {
        val base32Sha1Seed = TotpGenerator.encodeBase32(sha1Seed)
        // RFC 6238 t=59 => 8-digit 94287082 => 6-digit 287082
        val code6 = TotpGenerator.generateCode(
            secretBase32 = base32Sha1Seed,
            timeSeconds = 59L,
            periodSeconds = 30,
            digits = 6,
            algorithm = HashAlgorithm.SHA1
        )
        assertEquals("287082", code6)

        // With 60s interval, t=60 and t=119 share counter=1 (same counter as t=30..59 with 30s period)
        val code60a = TotpGenerator.generateCode(
            secretBase32 = base32Sha1Seed,
            timeSeconds = 60L,
            periodSeconds = 60,
            digits = 6,
            algorithm = HashAlgorithm.SHA1
        )
        val code60b = TotpGenerator.generateCode(
            secretBase32 = base32Sha1Seed,
            timeSeconds = 119L,
            periodSeconds = 60,
            digits = 6,
            algorithm = HashAlgorithm.SHA1
        )
        assertEquals(code6, code60a)
        assertEquals(code60a, code60b)
    }

    @Test
    fun `base32 normalization handles spaces hyphens lowercase and rejects invalid characters`() {
        val formatted = "jbsw-y3dp ehpk 3pxp==="
        assertTrue(TotpGenerator.isValidBase32(formatted))
        assertEquals("JBSWY3DPEHPK3PXP", TotpGenerator.normalizeBase32(formatted))
        assertFalse(TotpGenerator.isValidBase32("INVALID0189!@#"))
        assertFalse(TotpGenerator.isValidBase32("ABC"))
    }

    @Test
    fun `remaining seconds and code formatting behave accurately`() {
        assertEquals(30, TotpGenerator.remainingSeconds(timeSeconds = 60L, periodSeconds = 30))
        assertEquals(1, TotpGenerator.remainingSeconds(timeSeconds = 89L, periodSeconds = 30))
        assertEquals(60, TotpGenerator.remainingSeconds(timeSeconds = 120L, periodSeconds = 60))
        assertEquals("287 082", TotpGenerator.formatCodeForDisplay("287082"))
        assertEquals("9428 7082", TotpGenerator.formatCodeForDisplay("94287082"))
    }
}
