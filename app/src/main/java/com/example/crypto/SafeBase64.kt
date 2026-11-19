package com.example.crypto

import org.bouncycastle.util.encoders.Base64

/**
 * Cross-API-level (minSdk 24+) and JVM-test-safe Base64 helper backed by BouncyCastle.
 */
object SafeBase64 {
    fun encode(bytes: ByteArray): String = Base64.toBase64String(bytes)
    fun decode(value: String): ByteArray = Base64.decode(value)
}
