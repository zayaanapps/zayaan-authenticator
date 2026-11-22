package com.example.crypto

import com.example.data.model.AuthenticatorAccount
import com.example.data.model.HashAlgorithm
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom

/**
 * Parses standard otpauth://totp/... provisioning URIs and Google Authenticator
 * otpauth-migration://offline?data=... batch export QR payloads.
 */
object OtpAuthUriParser {

    sealed interface ParseResult {
        data class Success(val accounts: List<AuthenticatorAccount>) : ParseResult
        data class Error(val message: String) : ParseResult
    }

    fun parse(rawUri: String): ParseResult {
        val trimmed = rawUri.trim()
        if (trimmed.startsWith("otpauth-migration://", ignoreCase = true)) {
            return parseMigrationUri(trimmed)
        }
        if (!trimmed.startsWith("otpauth://", ignoreCase = true)) {
            return ParseResult.Error("Unrecognized QR code. Expected an otpauth:// authenticator QR code.")
        }

        return try {
            val uri = URI(trimmed.replace(" ", "%20"))
            val otpType = uri.host?.lowercase()
            if (otpType != "totp") {
                return ParseResult.Error("Only time-based (TOTP) accounts are supported.")
            }

            val queryParams = parseQueryParams(uri.rawQuery.orEmpty())
            val secretRaw = queryParams["secret"]?.trim().orEmpty()
            val normalizedSecret = TotpGenerator.normalizeBase32(secretRaw)
            if (!TotpGenerator.isValidBase32(normalizedSecret)) {
                return ParseResult.Error("Invalid or missing Base32 secret key in QR code.")
            }

            val rawPath = uri.rawPath?.removePrefix("/").orEmpty()
            val decodedLabel = urlDecode(rawPath)
            val (labelIssuer, labelAccount) = splitLabel(decodedLabel)
            val queryIssuer = queryParams["issuer"]?.trim().orEmpty()

            val finalIssuer = queryIssuer.ifBlank { labelIssuer }.ifBlank { "Account" }
            val finalAccountName = labelAccount.ifBlank { decodedLabel }.ifBlank { finalIssuer }

            val algorithm = HashAlgorithm.fromRaw(queryParams["algorithm"])
            val digits = queryParams["digits"]?.toIntOrNull()?.takeIf { it == 6 || it == 8 } ?: 6
            val period = queryParams["period"]?.toIntOrNull()?.takeIf { it in 10..300 } ?: 30

            val account = AuthenticatorAccount(
                issuer = finalIssuer,
                accountName = finalAccountName,
                secretBase32 = normalizedSecret,
                algorithm = algorithm,
                digits = digits,
                periodSeconds = period
            )
            ParseResult.Success(listOf(account))
        } catch (e: Exception) {
            ParseResult.Error("Could not parse authenticator QR code.")
        }
    }

    fun buildOtpAuthUri(account: AuthenticatorAccount): String {
        val label = if (account.issuer.isNotBlank() && account.accountName.isNotBlank()) {
            "${urlEncode(account.issuer)}:${urlEncode(account.accountName)}"
        } else {
            urlEncode(account.displayTitle)
        }
        val params = buildList {
            add("secret=${TotpGenerator.normalizeBase32(account.secretBase32)}")
            if (account.issuer.isNotBlank()) {
                add("issuer=${urlEncode(account.issuer)}")
            }
            add("algorithm=${account.algorithm.uriValue}")
            add("digits=${account.digits}")
            add("period=${account.periodSeconds}")
        }.joinToString("&")
        return "otpauth://totp/$label?$params"
    }

    /**
     * Builds Google Authenticator-compatible otpauth-migration://offline?data=... batch transfer URIs.
     * Splits large lists into batches (default 10 per QR code) so QR density stays easy to scan.
     */
    fun buildMigrationUris(
        accounts: List<AuthenticatorAccount>,
        batchSize: Int = 10
    ): List<String> {
        if (accounts.isEmpty()) return emptyList()
        val safeBatchSize = batchSize.coerceIn(1, 10)
        val chunks = accounts.chunked(safeBatchSize)
        val batchId = SecureRandom().nextInt() and 0x7FFFFFFF

        return chunks.mapIndexed { index, chunk ->
            val payloadBytes = encodeMigrationPayloadProtobuf(
                accounts = chunk,
                batchSize = chunks.size,
                batchIndex = index,
                batchId = batchId
            )
            val base64 = SafeBase64.encode(payloadBytes)
            "otpauth-migration://offline?data=${urlEncode(base64)}"
        }
    }

    private fun encodeMigrationPayloadProtobuf(
        accounts: List<AuthenticatorAccount>,
        batchSize: Int,
        batchIndex: Int,
        batchId: Int
    ): ByteArray {
        val out = ByteArrayOutputStream()
        for (account in accounts) {
            val entryBytes = encodeOtpParametersProtobuf(account)
            if (entryBytes.isNotEmpty()) {
                writeLengthDelimited(out, fieldNumber = 1, bytes = entryBytes)
            }
        }
        // version = 1 (field 2, varint)
        writeVarintField(out, fieldNumber = 2, value = 1L)
        // batch_size (field 3, varint)
        writeVarintField(out, fieldNumber = 3, value = batchSize.toLong())
        // batch_index (field 4, varint)
        writeVarintField(out, fieldNumber = 4, value = batchIndex.toLong())
        // batch_id (field 5, varint)
        writeVarintField(out, fieldNumber = 5, value = batchId.toLong())
        return out.toByteArray()
    }

    private fun encodeOtpParametersProtobuf(account: AuthenticatorAccount): ByteArray {
        val secretBytes = try {
            TotpGenerator.decodeBase32(account.secretBase32)
        } catch (_: Exception) {
            return ByteArray(0)
        }
        val out = ByteArrayOutputStream()
        try {
            writeLengthDelimited(out, fieldNumber = 1, bytes = secretBytes)
            val name = if (account.issuer.isNotBlank() && account.accountName.isNotBlank()) {
                "${account.issuer}:${account.accountName}"
            } else {
                account.displayTitle
            }
            writeLengthDelimited(out, fieldNumber = 2, bytes = name.toByteArray(Charsets.UTF_8))
            if (account.issuer.isNotBlank()) {
                writeLengthDelimited(out, fieldNumber = 3, bytes = account.issuer.toByteArray(Charsets.UTF_8))
            }
            val algoCode = when (account.algorithm) {
                HashAlgorithm.SHA1 -> 1L
                HashAlgorithm.SHA256 -> 2L
                HashAlgorithm.SHA512 -> 3L
            }
            writeVarintField(out, fieldNumber = 4, value = algoCode)
            val digitsCode = if (account.digits == 8) 2L else 1L
            writeVarintField(out, fieldNumber = 5, value = digitsCode)
            // type = 2 (TOTP)
            writeVarintField(out, fieldNumber = 6, value = 2L)
            return out.toByteArray()
        } finally {
            secretBytes.fill(0)
        }
    }

    private fun writeVarintField(out: ByteArrayOutputStream, fieldNumber: Int, value: Long) {
        writeVarint(out, ((fieldNumber shl 3) or 0).toLong())
        writeVarint(out, value)
    }

    private fun writeLengthDelimited(out: ByteArrayOutputStream, fieldNumber: Int, bytes: ByteArray) {
        writeVarint(out, ((fieldNumber shl 3) or 2).toLong())
        writeVarint(out, bytes.size.toLong())
        out.write(bytes)
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while ((v and 0x7FL.inv()) != 0L) {
            out.write(((v and 0x7FL) or 0x80L).toInt())
            v = v ushr 7
        }
        out.write((v and 0x7FL).toInt())
    }

    private fun parseMigrationUri(trimmed: String): ParseResult {
        return try {
            val uri = URI(trimmed)
            val queryParams = parseQueryParams(uri.rawQuery.orEmpty())
            val dataParam = queryParams["data"]
                ?: return ParseResult.Error("Missing migration data in QR code.")
            val bytes = SafeBase64.decode(dataParam.replace(" ", "+"))
            val accounts = parseMigrationPayloadProtobuf(bytes)
            if (accounts.isEmpty()) {
                ParseResult.Error("No TOTP accounts found in Google Authenticator migration QR code.")
            } else {
                ParseResult.Success(accounts)
            }
        } catch (e: Exception) {
            ParseResult.Error("Invalid Google Authenticator migration QR code.")
        }
    }

    /**
     * Minimal protobuf wire-format reader for Google Authenticator MigrationPayload:
     * message MigrationPayload { repeated OtpParameters otp_parameters = 1; }
     * message OtpParameters {
     *   bytes secret = 1;
     *   string name = 2;
     *   string issuer = 3;
     *   int32 algorithm = 4; // 1=SHA1, 2=SHA256, 3=SHA512
     *   int32 digits = 5;    // 1=6 digits, 2=8 digits
     *   int32 type = 6;      // 2=TOTP
     * }
     */
    private fun parseMigrationPayloadProtobuf(payload: ByteArray): List<AuthenticatorAccount> {
        val result = mutableListOf<AuthenticatorAccount>()
        val input = ByteArrayInputStream(payload)
        while (input.available() > 0) {
            val tag = readVarint(input) ?: break
            val fieldNumber = (tag ushr 3).toInt()
            val wireType = (tag and 0x07L).toInt()
            when {
                fieldNumber == 1 && wireType == 2 -> {
                    val length = readVarint(input)?.toInt() ?: break
                    val entryBytes = ByteArray(length)
                    val read = input.read(entryBytes)
                    if (read == length) {
                        parseOtpParameters(entryBytes)?.let { result.add(it) }
                    }
                }
                else -> skipField(input, wireType)
            }
        }
        return result
    }

    private fun parseOtpParameters(entryBytes: ByteArray): AuthenticatorAccount? {
        var secretBytes: ByteArray? = null
        var name = ""
        var issuer = ""
        var algoCode = 1
        var digitsCode = 1
        var typeCode = 2

        val input = ByteArrayInputStream(entryBytes)
        while (input.available() > 0) {
            val tag = readVarint(input) ?: break
            val fieldNumber = (tag ushr 3).toInt()
            val wireType = (tag and 0x07L).toInt()
            when {
                fieldNumber == 1 && wireType == 2 -> {
                    val len = readVarint(input)?.toInt() ?: break
                    secretBytes = ByteArray(len)
                    input.read(secretBytes)
                }
                fieldNumber == 2 && wireType == 2 -> {
                    val len = readVarint(input)?.toInt() ?: break
                    val buf = ByteArray(len)
                    input.read(buf)
                    name = String(buf, Charsets.UTF_8)
                }
                fieldNumber == 3 && wireType == 2 -> {
                    val len = readVarint(input)?.toInt() ?: break
                    val buf = ByteArray(len)
                    input.read(buf)
                    issuer = String(buf, Charsets.UTF_8)
                }
                fieldNumber == 4 && wireType == 0 -> algoCode = readVarint(input)?.toInt() ?: 1
                fieldNumber == 5 && wireType == 0 -> digitsCode = readVarint(input)?.toInt() ?: 1
                fieldNumber == 6 && wireType == 0 -> typeCode = readVarint(input)?.toInt() ?: 2
                else -> skipField(input, wireType)
            }
        }

        // Only import TOTP (type 2 or unspecified 0) with valid secret bytes
        if (typeCode != 2 && typeCode != 0) return null
        val rawSecret = secretBytes ?: return null
        if (rawSecret.isEmpty()) return null

        val base32 = TotpGenerator.encodeBase32(rawSecret)
        rawSecret.fill(0)

        val (labelIssuer, labelAccount) = splitLabel(name)
        val resolvedIssuer = issuer.ifBlank { labelIssuer }.ifBlank { "Account" }
        val resolvedName = labelAccount.ifBlank { name }.ifBlank { resolvedIssuer }
        val algorithm = when (algoCode) {
            2 -> HashAlgorithm.SHA256
            3 -> HashAlgorithm.SHA512
            else -> HashAlgorithm.SHA1
        }
        val digits = if (digitsCode == 2) 8 else 6

        return AuthenticatorAccount(
            issuer = resolvedIssuer,
            accountName = resolvedName,
            secretBase32 = base32,
            algorithm = algorithm,
            digits = digits,
            periodSeconds = 30
        )
    }

    private fun readVarint(input: ByteArrayInputStream): Long? {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            val b = input.read()
            if (b == -1) return null
            result = result or ((b.toLong() and 0x7FL) shl shift)
            if ((b and 0x80) == 0) return result
            shift += 7
        }
        return null
    }

    private fun skipField(input: ByteArrayInputStream, wireType: Int) {
        when (wireType) {
            0 -> readVarint(input)
            1 -> input.skip(8)
            2 -> {
                val len = readVarint(input) ?: return
                input.skip(len)
            }
            5 -> input.skip(4)
        }
    }

    private fun splitLabel(label: String): Pair<String, String> {
        val colonIndex = label.indexOf(':')
        return if (colonIndex > 0) {
            val issuer = label.substring(0, colonIndex).trim()
            val account = label.substring(colonIndex + 1).trim()
            issuer to account
        } else {
            "" to label.trim()
        }
    }

    private fun parseQueryParams(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()
        val map = mutableMapOf<String, String>()
        for (pair in rawQuery.split('&')) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = urlDecode(pair.substring(0, idx)).lowercase()
                val value = urlDecode(pair.substring(idx + 1))
                map[key] = value
            }
        }
        return map
    }

    private fun urlDecode(value: String): String =
        URLDecoder.decode(value, Charsets.UTF_8.name())

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
