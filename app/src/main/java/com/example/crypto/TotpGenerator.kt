package com.example.crypto

import com.example.data.model.HashAlgorithm
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * Pure RFC 4648 Base32 & RFC 6238 / RFC 4226 TOTP implementation.
 * Works 100% offline with zero network calls.
 * Supports SHA-1, SHA-256, SHA-512, 6-digit and 8-digit codes, and 30s / 60s (or custom) periods.
 */
object TotpGenerator {

    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun normalizeBase32(input: String): String {
        return input
            .uppercase()
            .replace(" ", "")
            .replace("-", "")
            .trimEnd('=')
    }

    fun isValidBase32(secret: String): Boolean {
        val cleaned = normalizeBase32(secret)
        if (cleaned.length < 8) return false
        return cleaned.all { it in BASE32_ALPHABET }
    }

    fun decodeBase32(base32: String): ByteArray {
        val cleaned = normalizeBase32(base32)
        require(cleaned.isNotEmpty()) { "Secret key cannot be empty" }

        val output = ByteArray((cleaned.length * 5) / 8)
        var buffer = 0
        var bitsLeft = 0
        var count = 0

        for (char in cleaned) {
            val value = BASE32_ALPHABET.indexOf(char)
            require(value >= 0) { "Invalid Base32 character in secret key" }
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                output[count++] = ((buffer shr (bitsLeft - 8)) and 0xFF).toByte()
                bitsLeft -= 8
            }
        }
        return if (count == output.size) output else output.copyOf(count)
    }

    fun encodeBase32(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val result = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = bytes[0].toInt() and 0xFF
        var next = 1
        var bitsLeft = 8
        while (bitsLeft > 0 || next < bytes.size) {
            if (bitsLeft < 5) {
                if (next < bytes.size) {
                    buffer = (buffer shl 8) or (bytes[next++].toInt() and 0xFF)
                    bitsLeft += 8
                } else {
                    val pad = 5 - bitsLeft
                    buffer = buffer shl pad
                    bitsLeft += pad
                }
            }
            val index = (buffer shr (bitsLeft - 5)) and 0x1F
            bitsLeft -= 5
            result.append(BASE32_ALPHABET[index])
        }
        return result.toString()
    }

    /**
     * Generates a TOTP code from raw secret key bytes according to RFC 6238.
     */
    fun generateCodeFromBytes(
        secretBytes: ByteArray,
        timeSeconds: Long = System.currentTimeMillis() / 1000L,
        periodSeconds: Int = 30,
        digits: Int = 6,
        algorithm: HashAlgorithm = HashAlgorithm.SHA1
    ): String {
        require(secretBytes.isNotEmpty()) { "Secret bytes cannot be empty" }
        require(periodSeconds > 0) { "Period must be positive" }
        require(digits in 6..8) { "Digits must be between 6 and 8" }

        val counter = Math.floorDiv(timeSeconds, periodSeconds.toLong())
        val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()

        val mac = Mac.getInstance(algorithm.jcaName)
        mac.init(SecretKeySpec(secretBytes, algorithm.jcaName))
        val hash = mac.doFinal(counterBytes)

        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary =
            ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)

        val modulo = 10.0.pow(digits).toInt()
        val otp = binary % modulo
        return otp.toString().padStart(digits, '0')
    }

    /**
     * Generates a TOTP code from a Base32-encoded secret string.
     */
    fun generateCode(
        secretBase32: String,
        timeSeconds: Long = System.currentTimeMillis() / 1000L,
        periodSeconds: Int = 30,
        digits: Int = 6,
        algorithm: HashAlgorithm = HashAlgorithm.SHA1
    ): String {
        val secretBytes = decodeBase32(secretBase32)
        return try {
            generateCodeFromBytes(
                secretBytes = secretBytes,
                timeSeconds = timeSeconds,
                periodSeconds = periodSeconds,
                digits = digits,
                algorithm = algorithm
            )
        } finally {
            secretBytes.fill(0)
        }
    }

    fun formatCodeForDisplay(code: String): String {
        return when (code.length) {
            6 -> "${code.substring(0, 3)} ${code.substring(3, 6)}"
            8 -> "${code.substring(0, 4)} ${code.substring(4, 8)}"
            else -> code
        }
    }

    fun remainingSeconds(
        timeSeconds: Long = System.currentTimeMillis() / 1000L,
        periodSeconds: Int = 30
    ): Int {
        val safePeriod = periodSeconds.coerceAtLeast(1)
        val elapsed = Math.floorMod(timeSeconds, safePeriod.toLong()).toInt()
        return safePeriod - elapsed
    }

    fun remainingProgress(
        timeMillis: Long = System.currentTimeMillis(),
        periodSeconds: Int = 30
    ): Float {
        val periodMs = periodSeconds.coerceAtLeast(1) * 1000L
        val elapsedMs = Math.floorMod(timeMillis, periodMs)
        return ((periodMs - elapsedMs).toFloat() / periodMs.toFloat()).coerceIn(0f, 1f)
    }
}
