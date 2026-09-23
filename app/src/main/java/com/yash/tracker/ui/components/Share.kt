package com.yash.tracker.ui.components

import android.content.Context
import android.content.Intent

/**
 * Hands text to the system share sheet.
 *
 * Deliberately the chooser rather than a fixed target: where a day's food or a session goes —
 * a coach, a group chat, a note to yourself — is the user's business, and the sheet already
 * knows what they have installed.
 */
fun Context.shareText(text: String, subject: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    // A phone with nothing that accepts text is possible, and is not worth crashing over.
    runCatching { startActivity(Intent.createChooser(intent, subject)) }
}
