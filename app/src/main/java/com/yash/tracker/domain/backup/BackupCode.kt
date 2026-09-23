package com.yash.tracker.domain.backup

import java.security.SecureRandom

/**
 * The six words that unlock a backup file.
 *
 * Words rather than a password because this has to survive being copied onto paper a year ago
 * and typed back in under stress. Six words from 2,048 is 66 bits, and PBKDF2 at 210,000
 * iterations is what stands between that and someone who has taken the file.
 *
 * The list is BIP-39 English: 2,048 words, no two sharing their first four letters, chosen to
 * be unambiguous when handwritten.
 */
object BackupCode {

    const val WORD_COUNT = 6
    const val WORDLIST_SIZE = 2048

    fun generate(wordlist: List<String>, random: SecureRandom = SecureRandom()): String {
        require(wordlist.size == WORDLIST_SIZE) {
            "expected $WORDLIST_SIZE words, got ${wordlist.size}"
        }
        return (1..WORD_COUNT).joinToString(" ") { wordlist[random.nextInt(wordlist.size)] }
    }

    /**
     * What the key is actually derived from. Whatever the user types — extra spaces, capitals
     * from a keyboard's autocapitalise, a trailing newline pasted from a note — has to reduce
     * to the same string the backup was sealed with, or a correct code reads as a wrong one.
     */
    fun normalise(code: String): String =
        code.trim().lowercase().split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")

    fun words(code: String): List<String> = normalise(code).split(" ").filter { it.isNotEmpty() }

    /**
     * Whether this could be the right code, checked before spending a second on PBKDF2 so an
     * obvious typo comes back as "that word isn't in the list" rather than "wrong code".
     */
    fun validate(code: String, wordlist: Set<String>): CodeProblem? {
        val words = words(code)
        return when {
            words.size != WORD_COUNT -> CodeProblem.WrongLength(words.size)
            else -> words.firstOrNull { it !in wordlist }?.let { CodeProblem.UnknownWord(it) }
        }
    }

    private val WHITESPACE = Regex("\\s+")
}

sealed interface CodeProblem {
    data class WrongLength(val got: Int) : CodeProblem
    data class UnknownWord(val word: String) : CodeProblem
}
