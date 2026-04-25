package com.webdav.music.data

import com.webdav.music.data.local.PasswordEncryptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PasswordEncryptorTest {

    @Test
    fun `encrypt and decrypt should return original plaintext`() {
        val plaintext = "mySecretPassword123"
        val encrypted = PasswordEncryptor.encrypt(plaintext)
        val decrypted = PasswordEncryptor.decrypt(encrypted)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypt should produce different ciphertext for same plaintext`() {
        val plaintext = "mySecretPassword123"
        val encrypted1 = PasswordEncryptor.encrypt(plaintext)
        val encrypted2 = PasswordEncryptor.encrypt(plaintext)
        assertNotEquals(encrypted1, encrypted2)
    }

    @Test
    fun `encrypt and decrypt with empty string`() {
        val plaintext = ""
        val encrypted = PasswordEncryptor.encrypt(plaintext)
        val decrypted = PasswordEncryptor.decrypt(encrypted)
        assertEquals(plaintext, decrypted)
    }
}
