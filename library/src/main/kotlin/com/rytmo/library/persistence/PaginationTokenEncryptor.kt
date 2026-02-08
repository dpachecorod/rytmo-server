package com.rytmo.library.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class PaginationTokenEncryptor(encryptionKey: String) {
    private val secretKey: SecretKeySpec
    private val objectMapper = ObjectMapper().registerKotlinModule()

    companion object {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }

    init {
        val keyBytes = encryptionKey.toByteArray(Charsets.UTF_8)
        require(keyBytes.size == 16 || keyBytes.size == 24 || keyBytes.size == 32) {
            "Encryption key must be 16, 24, or 32 bytes long"
        }
        secretKey = SecretKeySpec(keyBytes, ALGORITHM)
    }

    fun encrypt(lastEvaluatedKey: Map<String, String>): String {
        val json = objectMapper.writeValueAsString(lastEvaluatedKey)
        val plaintext = json.toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)

        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

        return Base64.getUrlEncoder().withoutPadding().encodeToString(combined)
    }

    fun decrypt(token: String): Map<String, String> {
        val combined = Base64.getUrlDecoder().decode(token)

        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val plaintext = cipher.doFinal(ciphertext)
        val json = String(plaintext, Charsets.UTF_8)

        return objectMapper.readValue(json)
    }
}
