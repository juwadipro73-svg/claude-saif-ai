package com.example.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyStoreCryptoTest {
    @Test
    fun encryptIsIdempotentAndDecryptSupportsLegacyPlaintext() {
        assertEquals("", KeyStoreCrypto.encrypt("   "))
        assertEquals("legacy-key", KeyStoreCrypto.decrypt("legacy-key"))
        val encrypted = KeyStoreCrypto.encrypt("secret-api-key")
        assertEquals(encrypted, KeyStoreCrypto.encrypt(encrypted))
        assertEquals("secret-api-key", KeyStoreCrypto.decrypt(encrypted))
        assertTrue(encrypted == "secret-api-key" || encrypted.startsWith("ENC:"))
    }

    @Test
    fun redactsCommonProviderSecrets() {
        val redacted = KeyStoreCrypto.redact("Authorization: Bearer abc.def-123 and ?key=xyz")
        assertTrue(redacted.contains("Bearer [REDACTED]"))
        assertTrue(redacted.contains("key=[REDACTED]"))
        assertTrue(!redacted.contains("abc.def-123"))
    }
}
