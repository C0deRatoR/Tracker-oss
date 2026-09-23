package com.yash.tracker.data.backup

import com.yash.tracker.domain.backup.BackupCode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupCryptoTest {

    private val code = "abandon ability able about above absent"
    private val payload = "the whole diary, twice over. ".repeat(5000).toByteArray()

    private fun seal(bytes: ByteArray, withCode: String = code): ByteArray =
        ByteArrayOutputStream().also {
            BackupCrypto.seal(ByteArrayInputStream(bytes), it, withCode)
        }.toByteArray()

    private fun open(file: ByteArray, withCode: String = code): ByteArray =
        ByteArrayOutputStream().also {
            BackupCrypto.open(ByteArrayInputStream(file), it, withCode)
        }.toByteArray()

    @Test
    fun `a sealed backup opens with its code`() {
        assertArrayEquals(payload, open(seal(payload)))
    }

    @Test
    fun `the file starts with the magic bytes and is not the plaintext`() {
        val sealed = seal(payload)

        assertEquals("NTBAK001", String(sealed.copyOfRange(0, 8)))
        assertFalse("the plaintext must not survive", sealed.contentEquals(payload))
        assertEquals("magic, salt, IV, ciphertext and tag", 8 + 16 + 12 + payload.size + 16, sealed.size)
    }

    @Test
    fun `the wrong code is refused rather than returning rubbish`() {
        val sealed = seal(payload)

        assertThrows(BackupFailure.WrongCode::class.java) {
            open(sealed, withCode = "abandon ability able about above absorb")
        }
    }

    @Test
    fun `capitals and stray spacing still open the file`() {
        val sealed = seal(payload, withCode = "abandon ability able about above absent")

        assertArrayEquals(payload, open(sealed, withCode = "  Abandon  ABILITY able\nabout above absent \n"))
    }

    @Test
    fun `a file that is not a backup is named as such`() {
        assertThrows(BackupFailure.NotABackup::class.java) {
            open("this is a photo of a dog".toByteArray())
        }
    }

    @Test
    fun `a truncated header is not read as a wrong code`() {
        // The right magic, then a header that stops halfway through the salt.
        val cut = "NTBAK001".toByteArray() + ByteArray(5)

        assertThrows(BackupFailure.Truncated::class.java) { open(cut) }
    }

    @Test
    fun `a file too short to even hold the magic is not a backup`() {
        assertThrows(BackupFailure.NotABackup::class.java) { open("NTBAK0".toByteArray()) }
    }

    @Test
    fun `a tampered ciphertext is rejected`() {
        val sealed = seal(payload)
        sealed[sealed.size - 30] = (sealed[sealed.size - 30] + 1).toByte()

        assertThrows(BackupFailure.WrongCode::class.java) { open(sealed) }
    }

    @Test
    fun `two seals of the same payload differ, because the salt and IV are fresh`() {
        assertFalse(seal(payload).contentEquals(seal(payload)))
    }

    @Test
    fun `an empty payload still round-trips`() {
        assertArrayEquals(ByteArray(0), open(seal(ByteArray(0))))
    }

    @Test
    fun `a generated code is six words from the list`() {
        val wordlist = (1..BackupCode.WORDLIST_SIZE).map { "word$it" }
        val generated = BackupCode.generate(wordlist)

        assertEquals(6, BackupCode.words(generated).size)
        assertEquals(null, BackupCode.validate(generated, wordlist.toSet()))
    }
}
