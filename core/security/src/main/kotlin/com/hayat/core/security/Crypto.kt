package com.hayat.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password-based key derivation and authenticated encryption primitives (pure JVM, testable). */
object Crypto {
    private val random = SecureRandom()
    const val SALT_BYTES = 16
    const val IV_BYTES = 12
    const val KEY_BITS = 256
    const val GCM_TAG_BITS = 128

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also(random::nextBytes)

    /** PBKDF2-HMAC-SHA256. The caller should wipe [secret] afterwards. */
    fun pbkdf2(secret: CharArray, salt: ByteArray, iterations: Int, bits: Int = KEY_BITS): ByteArray {
        val spec = PBEKeySpec(secret, salt, iterations, bits)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    /** AES-256-GCM. Returns ciphertext||tag. [aad] is authenticated but not encrypted. */
    fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        c.updateAAD(aad)
        return c.doFinal(plaintext)
    }

    /** Throws javax.crypto.AEADBadTagException on wrong key or any tampering. */
    fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        c.updateAAD(aad)
        return c.doFinal(ciphertext)
    }
}
