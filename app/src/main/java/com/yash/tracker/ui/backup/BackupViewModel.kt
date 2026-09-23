package com.yash.tracker.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.tracker.data.backup.BackupFailure
import com.yash.tracker.data.backup.BackupRepository
import com.yash.tracker.domain.backup.BackupCode
import com.yash.tracker.domain.backup.CodeProblem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BackupStage { IDLE, WORKING, DONE }

data class BackupUiState(
    val code: String = "",
    val codeVisible: Boolean = false,
    val lastBackupAt: Long? = null,
    val stage: BackupStage = BackupStage.IDLE,
    val message: String? = null,
    val error: String? = null,
    val restoreOpen: Boolean = false,
    val restoreUri: Uri? = null,
    val restoreCode: String = "",
    val restored: Boolean = false,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backups: BackupRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state = _state.asStateFlow()

    private var wordlist: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            wordlist = backups.wordlist().toSet()
            _state.update {
                it.copy(code = backups.backupCode(), lastBackupAt = backups.lastBackupAt())
            }
        }
    }

    fun toggleCode() = _state.update { it.copy(codeVisible = !it.codeVisible) }

    fun suggestedFileName() = backups.suggestedFileName()

    fun export(target: Uri) {
        _state.update { it.copy(stage = BackupStage.WORKING, message = null, error = null) }

        viewModelScope.launch {
            runCatching { backups.export(target) }
                .onSuccess { summary ->
                    _state.update {
                        it.copy(
                            stage = BackupStage.DONE,
                            lastBackupAt = backups.lastBackupAt(),
                            message = "Saved ${summary.bytes / 1024} KB, " +
                                "${summary.photos} ${if (summary.photos == 1) "photo" else "photos"} included.",
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(stage = BackupStage.IDLE, error = failure.readable())
                    }
                }
        }
    }

    fun openRestore(source: Uri) = _state.update {
        it.copy(restoreOpen = true, restoreUri = source, restoreCode = "", error = null, message = null)
    }

    fun closeRestore() = _state.update { it.copy(restoreOpen = false, restoreUri = null, restoreCode = "") }

    fun setRestoreCode(code: String) = _state.update { it.copy(restoreCode = code, error = null) }

    fun confirmRestore() {
        val source = _state.value.restoreUri ?: return
        val code = _state.value.restoreCode

        // Checked against the wordlist before spending a second on key derivation, so an
        // obvious typo comes back naming the word rather than as "wrong code".
        BackupCode.validate(code, wordlist)?.let { problem ->
            _state.update { it.copy(error = problem.readable()) }
            return
        }

        _state.update { it.copy(stage = BackupStage.WORKING, error = null) }

        viewModelScope.launch {
            runCatching { backups.restore(source, code) }
                .onSuccess { summary ->
                    _state.update {
                        it.copy(
                            stage = BackupStage.DONE,
                            restoreOpen = false,
                            restored = true,
                            message = "Restored ${summary.photos} " +
                                "${if (summary.photos == 1) "photo" else "photos"} and the whole diary.",
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update { it.copy(stage = BackupStage.IDLE, error = failure.readable()) }
                }
        }
    }

    fun clearMessages() = _state.update { it.copy(message = null, error = null) }
}

/**
 * Kept apart from [BackupViewModel] so the dashboard reads one timestamp rather than loading a
 * wordlist and minting a backup code it has no use for.
 */
@HiltViewModel
class BackupReminderViewModel @Inject constructor(
    backups: BackupRepository,
) : ViewModel() {

    private val _overdue = MutableStateFlow(false)
    val overdue = _overdue.asStateFlow()

    init {
        viewModelScope.launch {
            val last = backups.lastBackupAt()
            _overdue.value = last == null ||
                System.currentTimeMillis() - last >= STALE_DAYS * MILLIS_PER_DAY
        }
    }

    /** Dismissed for this run only. It is a reminder; it should come back. */
    fun dismiss() {
        _overdue.value = false
    }

    private companion object {
        const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
    }
}

private fun CodeProblem.readable(): String = when (this) {
    is CodeProblem.WrongLength ->
        "A backup code is ${BackupCode.WORD_COUNT} words — that one has $got."
    is CodeProblem.UnknownWord -> "\"$word\" isn't one of the backup words. Check the spelling."
}

/** Never show a stack trace or a raw SQL error to someone trying to get their diary back. */
private fun Throwable.readable(): String = when (this) {
    is BackupFailure -> message ?: "That backup could not be opened."
    else -> "Backup failed: ${message ?: this::class.java.simpleName}"
}
