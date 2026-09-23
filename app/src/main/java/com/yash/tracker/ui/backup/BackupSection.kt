package com.yash.tracker.ui.backup

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.tracker.ui.components.ActionTone
import com.yash.tracker.ui.components.Eyebrow
import com.yash.tracker.ui.components.IconPlate
import com.yash.tracker.ui.components.LuxTextField
import com.yash.tracker.ui.components.Pill
import com.yash.tracker.ui.components.PillButton
import com.yash.tracker.ui.components.PillTone
import com.yash.tracker.ui.components.SmallAction
import com.yash.tracker.ui.components.SoftCard
import com.yash.tracker.ui.components.TextAction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

private val BACKUP_DATE = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")

/**
 * Export, restore, and the six words that stand between a backup file and whoever finds it.
 *
 * The file picker is the system's own, for both directions: no storage permission, and the
 * file can go straight to Drive rather than sitting in Downloads waiting to be shared.
 */
@Composable
fun BackupSection(viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val exportTo = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> uri?.let(viewModel::export) }

    val restoreFrom = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::openRestore) }

    if (state.restoreOpen) RestoreDialog(state, viewModel)
    if (state.restored) RestartDialog()

    Column {
        Pill(
            text = state.lastBackupAt?.let { "Last backup ${it.ago()}" } ?: "Never backed up",
            tone = if (state.lastBackupAt.isStale()) PillTone.Danger else PillTone.Quiet,
            leadingDot = true,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            state.lastBackupAt?.let { it.formatted() }
                ?: "Everything is on this phone and nowhere else.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction(
                text = if (state.stage == BackupStage.WORKING) "Working…" else "Export backup",
                icon = Icons.Outlined.FileUpload,
                onClick = { exportTo.launch(viewModel.suggestedFileName()) },
                tone = ActionTone.Ink,
                enabled = state.stage != BackupStage.WORKING,
            )
            SmallAction(
                text = "Restore",
                icon = Icons.Outlined.FileDownload,
                onClick = { restoreFrom.launch(arrayOf("*/*")) },
                enabled = state.stage != BackupStage.WORKING,
            )
        }

        state.message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(16.dp))
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Eyebrow("Backup code")
                Spacer(Modifier.height(8.dp))
                Text(
                    "A backup file is useless without these six words, and so are you. " +
                        "Write them down somewhere that is not this phone — the phone is the " +
                        "thing the backup exists to survive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                if (state.codeVisible) {
                    SoftCard(
                        Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            state.code,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                TextAction(
                    if (state.codeVisible) "Hide" else "Show my backup code",
                    onClick = viewModel::toggleCode,
                )
            }
        }
    }
}

/**
 * The dashboard nag. TRD §8 schedules a weekly WorkManager check for this, but the banner only
 * ever appears on a screen the app is already drawing — so the check happens when the
 * dashboard opens, and WorkManager stays unused. No notification in v1, per the same section.
 */
@Composable
fun BackupReminder(
    onOpenBackup: () -> Unit,
    viewModel: BackupReminderViewModel = hiltViewModel(),
) {
    val overdue by viewModel.overdue.collectAsStateWithLifecycle()
    if (!overdue) return
    BackupReminderCard(onOpenBackup, viewModel::dismiss)
}

/**
 * The card itself, with no opinion about whether it should be on screen. A caller inside a
 * spaced list has to know that before it lays anything out, or it leaves a gap where the
 * banner would have been.
 */
@Composable
fun BackupReminderCard(onOpenBackup: () -> Unit, onDismiss: () -> Unit) {
    SoftCard(
        Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconPlate(Icons.Outlined.CloudOff, size = 42.dp, tone = PillTone.Solid)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "No recent backup",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    "Everything is on this phone and nowhere else.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction("Back up now", onClick = onOpenBackup, tone = ActionTone.Ink)
                    TextAction(
                        "Not now",
                        onClick = onDismiss,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun RestoreDialog(state: BackupUiState, viewModel: BackupViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::closeRestore,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Text("Restore from this file?", style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column {
                Text(
                    "This replaces everything currently on this phone — every meal, weigh-in " +
                        "and workout — with what is in the file. There is no undo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                LuxTextField(
                    value = state.restoreCode,
                    onValueChange = viewModel::setRestoreCode,
                    label = "Backup code",
                    supporting = "The six words from when the backup was made",
                )
                state.error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            PillButton(
                text = if (state.stage == BackupStage.WORKING) "Restoring…" else "Replace everything",
                onClick = viewModel::confirmRestore,
                enabled = state.stage != BackupStage.WORKING && state.restoreCode.isNotBlank(),
                height = 44.dp,
            )
        },
        dismissButton = {
            Box(Modifier.padding(end = 4.dp)) {
                TextAction("Cancel", onClick = viewModel::closeRestore)
            }
        },
    )
}

/**
 * Room caches what it has already read, and a restore writes underneath it through raw SQL —
 * so the app has to start again before anything on screen can be trusted.
 */
@Composable
private fun RestartDialog() {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = {},
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("Restored", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Text(
                "The app needs to start again to load the restored data.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            PillButton(
                text = "Restart",
                onClick = {
                    val intent = context.packageManager
                        .getLaunchIntentForPackage(context.packageName)
                        ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    (context as? Activity)?.finish()
                    Runtime.getRuntime().exit(0)
                },
                height = 44.dp,
            )
        },
    )
}

private fun Long?.isStale(): Boolean =
    this == null || TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - this) >= STALE_DAYS

private fun Long.ago(): String =
    when (val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - this)) {
        0L -> "today"
        1L -> "yesterday"
        else -> "$days days ago"
    }

private fun Long.formatted(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(BACKUP_DATE)

const val STALE_DAYS = 14L
