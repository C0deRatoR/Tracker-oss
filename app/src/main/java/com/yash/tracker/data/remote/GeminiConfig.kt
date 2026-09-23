package com.yash.tracker.data.remote

/**
 * The only things a call needs from Settings.
 *
 * Narrowing it to this also gives tests a seam: the real implementation reads DataStore,
 * which does not exist off-device.
 */
interface GeminiConfig {
    suspend fun model(): String

    /**
     * Whether a reading may be backed by a web search. Off means a request that would have
     * grounded is sent plain instead — cheaper, and the only lever the user has over what a
     * single call costs.
     */
    suspend fun isGroundingEnabled(): Boolean
}
