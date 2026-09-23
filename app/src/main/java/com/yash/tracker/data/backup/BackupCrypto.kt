package com.yash.tracker.data.backup

import com.yash.tracker.domain.backup.BackupCode
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Why a backup could not be opened. Each one needs different words in front of the user. */
sealed class BackupFailure(message: String) : Exception(message) {
    class NotABackup : BackupFailure("That file isn't a Tracker backup.")
    class WrongCode : BackupFailure("That backup code doesn't open this file.")
    class Truncated : BackupFailure("That backup file is incomplete.")
    class UnsupportedVersion(val version: Int) :
        BackupFailure("That backup was written by a newer version of the app.")
}

/**
 * The `.ntbak` container, per TRD §8:
 *
 *     [ 8 bytes magic "NTBAK001" ][ 16 bytes salt ][ 12 bytes IV ][ AES-256-GCM ciphertext ]
 *
 * Streamed in chunks rather than held in memory: a year of meal photos is a large file, and
 * the phone should not need three copies of it in the heap to write one.
 */
object BackupCrypto {

    private val MAGIC = "NTBAK001".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val CHUNK = 64 * 1024

    fun seal(plaintext: InputStream, out: OutputStream, code: String, random: SecureRandom = SecureRandom()) {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)

        out.write(MAGIC)
        out.write(salt)
        out.write(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(code, salt), GCMParameterSpec(TAG_BITS, iv))
        }
        pump(plaintext, out, cipher)
        out.flush()
    }

    /**
     * Decrypts into [out]. The authentication tag is only checked by `doFinal`, so nothing
     * written here can be trusted until this returns — callers write to a temporary file and
     * promote it only on success.
     *
     * Deliberately not CipherInputStream: it has a long history of swallowing the bad-tag
     * exception, which would turn "wrong code" into "silently corrupt restore".
     */
    fun open(source: InputStream, out: OutputStream, code: String) {
        // Magic first: a file that is not a backup at all should say so, even when it is also
        // too short to hold a header.
        val magic = ByteArray(MAGIC.size)
        if (source.readAtMost(magic) != MAGIC.size || !magic.contentEquals(MAGIC)) {
            throw BackupFailure.NotABackup()
        }

        val header = source.readNBytesOrThrow(SALT_BYTES + IV_BYTES)
        val salt = header.copyOfRange(0, SALT_BYTES)
        val iv = header.copyOfRange(SALT_BYTES, header.size)

        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, deriveKey(code, salt), GCMParameterSpec(TAG_BITS, iv))
        }

        try {
            pump(source, out, cipher)
        } catch (e: AEADBadTagException) {
            throw BackupFailure.WrongCode().initCause(e)
        }
        out.flush()
    }

    private fun pump(source: InputStream, out: OutputStream, cipher: Cipher) {
        val buffer = ByteArray(CHUNK)
        while (true) {
            val read = source.read(buffer)
            if (read <= 0) break
            cipher.update(buffer, 0, read)?.let { out.write(it) }
        }
        cipher.doFinal()?.let { out.write(it) }
    }

    private fun deriveKey(code: String, salt: ByteArray): SecretKeySpec {
        val normalised = BackupCode.normalise(code)
        val spec = PBEKeySpec(normalised.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    /** Fills as much of [into] as the stream has, returning how many bytes that was. */
    private fun InputStream.readAtMost(into: ByteArray): Int {
        var filled = 0
        while (filled < into.size) {
            val read = read(into, filled, into.size - filled)
            if (read < 0) break
            filled += read
        }
        return filled
    }

    private fun InputStream.readNBytesOrThrow(n: Int): ByteArray {
        val bytes = ByteArray(n)
        var filled = 0
        while (filled < n) {
            val read = read(bytes, filled, n - filled)
            if (read < 0) throw BackupFailure.Truncated()
            filled += read
        }
        return bytes
    }

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KDF = "PBKDF2WithHmacSHA256"
}
