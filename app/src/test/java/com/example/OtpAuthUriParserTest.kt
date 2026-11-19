package com.example

import com.example.crypto.OtpAuthUriParser
import com.example.crypto.SafeBase64
import com.example.data.model.HashAlgorithm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class OtpAuthUriParserTest {

    @Test
    fun `parses standard Google and Microsoft otpauth totp URIs with custom parameters`() {
        val uri =
            "otpauth://totp/GitHub:zayan%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&algorithm=SHA256&digits=8&period=60"
        val result = OtpAuthUriParser.parse(uri)
        assertTrue(result is OtpAuthUriParser.ParseResult.Success)
        val accounts = (result as OtpAuthUriParser.ParseResult.Success).accounts
        assertEquals(1, accounts.size)
        val account = accounts.first()
        assertEquals("GitHub", account.issuer)
        assertEquals("zayan@example.com", account.accountName)
        assertEquals("JBSWY3DPEHPK3PXP", account.secretBase32)
        assertEquals(HashAlgorithm.SHA256, account.algorithm)
        assertEquals(8, account.digits)
        assertEquals(60, account.periodSeconds)
    }

    @Test
    fun `parses otpauth URI without issuer prefix in path`() {
        val uri = "otpauth://totp/alice%40company.io?secret=gezdgn bvgy3tqojq&issuer=Microsoft&algorithm=SHA512"
        val result = OtpAuthUriParser.parse(uri)
        assertTrue(result is OtpAuthUriParser.ParseResult.Success)
        val account = (result as OtpAuthUriParser.ParseResult.Success).accounts.first()
        assertEquals("Microsoft", account.issuer)
        assertEquals("alice@company.io", account.accountName)
        assertEquals("GEZDGNBVGY3TQOJQ", account.secretBase32)
        assertEquals(HashAlgorithm.SHA512, account.algorithm)
        assertEquals(6, account.digits)
        assertEquals(30, account.periodSeconds)
    }

    @Test
    fun `rejects non-totp or invalid secret QR URIs`() {
        val hotp = OtpAuthUriParser.parse("otpauth://hotp/Test:a@b.com?secret=JBSWY3DPEHPK3PXP&counter=1")
        assertTrue(hotp is OtpAuthUriParser.ParseResult.Error)

        val badSecret = OtpAuthUriParser.parse("otpauth://totp/Test:a@b.com?secret=NOT_VALID_019!")
        assertTrue(badSecret is OtpAuthUriParser.ParseResult.Error)

        val randomUrl = OtpAuthUriParser.parse("https://example.com")
        assertTrue(randomUrl is OtpAuthUriParser.ParseResult.Error)
    }

    @Test
    fun `parses Google Authenticator otpauth-migration protobuf QR payload`() {
        // Construct minimal valid MigrationPayload protobuf with one TOTP OtpParameters entry
        val secretBytes = "Hello!\u00DE\u00AD\u00BE\u00EF".toByteArray(Charsets.ISO_8859_1)
        val nameBytes = "Google:user@gmail.com".toByteArray(Charsets.UTF_8)
        val issuerBytes = "Google".toByteArray(Charsets.UTF_8)

        val entry = ByteArrayOutputStream().apply {
            // field 1 (bytes secret): tag = (1 << 3) | 2 = 0x0A
            write(0x0A); write(secretBytes.size); write(secretBytes)
            // field 2 (string name): tag = (2 << 3) | 2 = 0x12
            write(0x12); write(nameBytes.size); write(nameBytes)
            // field 3 (string issuer): tag = (3 << 3) | 2 = 0x1A
            write(0x1A); write(issuerBytes.size); write(issuerBytes)
            // field 4 (algorithm = 2 -> SHA256): tag = (4 << 3) | 0 = 0x20
            write(0x20); write(2)
            // field 5 (digits = 2 -> 8 digits): tag = (5 << 3) | 0 = 0x28
            write(0x28); write(2)
            // field 6 (type = 2 -> TOTP): tag = (6 << 3) | 0 = 0x30
            write(0x30); write(2)
        }.toByteArray()

        val payload = ByteArrayOutputStream().apply {
            // MigrationPayload.otp_parameters field 1 (wire type 2): 0x0A
            write(0x0A); write(entry.size); write(entry)
        }.toByteArray()

        val encoded = SafeBase64.encode(payload)
        val migrationUri = "otpauth-migration://offline?data=$encoded"

        val result = OtpAuthUriParser.parse(migrationUri)
        assertTrue(result is OtpAuthUriParser.ParseResult.Success)
        val accounts = (result as OtpAuthUriParser.ParseResult.Success).accounts
        assertEquals(1, accounts.size)
        assertEquals("Google", accounts.first().issuer)
        assertEquals("user@gmail.com", accounts.first().accountName)
        assertEquals(HashAlgorithm.SHA256, accounts.first().algorithm)
        assertEquals(8, accounts.first().digits)
    }
}
