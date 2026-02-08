package com.rytmo.library.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PaginationTokenEncryptorTest {
    private val validKey = "0123456789abcdef" // 16 bytes

    @Test
    fun `encrypt and decrypt should return original data`() {
        val encryptor = PaginationTokenEncryptor(validKey)
        val originalData = mapOf("id" to "user-123", "sortKey" to "2024-01-01")

        val encrypted = encryptor.encrypt(originalData)
        val decrypted = encryptor.decrypt(encrypted)

        assertEquals(originalData, decrypted)
    }

    @Test
    fun `encrypted token should be URL safe`() {
        val encryptor = PaginationTokenEncryptor(validKey)
        val data = mapOf("id" to "test-id")

        val encrypted = encryptor.encrypt(data)

        assertNotEquals(data.toString(), encrypted)
        assertEquals(encrypted, encrypted.replace(Regex("[^A-Za-z0-9_-]"), ""))
    }

    @Test
    fun `different encryptions of same data should produce different tokens`() {
        val encryptor = PaginationTokenEncryptor(validKey)
        val data = mapOf("id" to "test-id")

        val encrypted1 = encryptor.encrypt(data)
        val encrypted2 = encryptor.encrypt(data)

        assertNotEquals(encrypted1, encrypted2)
    }

    @Test
    fun `should handle multiple key-value pairs`() {
        val encryptor = PaginationTokenEncryptor(validKey)
        val data =
            mapOf(
                "pk" to "customer#123",
                "sk" to "order#456",
                "gsi1pk" to "status#active",
            )

        val encrypted = encryptor.encrypt(data)
        val decrypted = encryptor.decrypt(encrypted)

        assertEquals(data, decrypted)
    }

    @Test
    fun `should reject invalid key lengths`() {
        assertThrows(IllegalArgumentException::class.java) { PaginationTokenEncryptor("short") }
    }

    @Test
    fun `should accept 24 byte key`() {
        val encryptor = PaginationTokenEncryptor("0123456789abcdef01234567") // 24 bytes
        val data = mapOf("id" to "test")

        val encrypted = encryptor.encrypt(data)
        val decrypted = encryptor.decrypt(encrypted)

        assertEquals(data, decrypted)
    }

    @Test
    fun `should accept 32 byte key`() {
        val encryptor = PaginationTokenEncryptor("0123456789abcdef0123456789abcdef") // 32 bytes
        val data = mapOf("id" to "test")

        val encrypted = encryptor.encrypt(data)
        val decrypted = encryptor.decrypt(encrypted)

        assertEquals(data, decrypted)
    }

    @Test
    fun `should fail to decrypt with wrong key`() {
        val encryptor1 = PaginationTokenEncryptor("0123456789abcdef")
        val encryptor2 = PaginationTokenEncryptor("fedcba9876543210")

        val data = mapOf("id" to "test")
        val encrypted = encryptor1.encrypt(data)

        assertThrows(Exception::class.java) { encryptor2.decrypt(encrypted) }
    }
}
