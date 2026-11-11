package com.mdibrahim.expensetracker.ui.screens.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdibrahim.expensetracker.data.backup.RemoteBackup
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val WHEN_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
private fun Instant.display(): String = atZone(ZoneId.systemDefault()).format(WHEN_FORMAT)

@Composable
fun BackupSection(viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmRestore by remember { mutableStateOf<RemoteBackup?>(null) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onConsentResult(result.data) }

    LaunchedEffect(state.consentRequest) {
        state.consentRequest?.let {
            consentLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build())
            viewModel.consentLaunched()
        }
    }
    LaunchedEffect(state.restartRequired) {
        if (state.restartRequired) restartApp(context)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Google Drive backup", style = MaterialTheme.typography.titleMedium)
            Text(
                "Backups go to an \"Expense Tracker Backups\" folder in your Google Drive. " +
                    "The app can only see files it created there, nothing else in your Drive.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Last backup: ${state.status.lastBackupAt?.display() ?: "never"}")
            state.status.lastError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Back up daily")
                    Text(
                        "Keeps the 10 most recent backups.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.status.autoBackupEnabled,
                    onCheckedChange = viewModel::setAutoBackup,
                    enabled = state.busyLabel == null,
                )
            }

            state.busyLabel?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::backupNow, enabled = state.busyLabel == null) {
                    Text("Back up now")
                }
                OutlinedButton(onClick = viewModel::chooseBackupToRestore, enabled = state.busyLabel == null) {
                    Text("Restore")
                }
            }

            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }

    state.backups?.let { backups ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPicker,
            title = { Text("Choose a backup") },
            text = {
                Column {
                    backups.forEach { backup ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { confirmRestore = backup; viewModel.dismissPicker() }
                                .padding(vertical = 12.dp),
                        ) {
                            Text(backup.modifiedAt.display())
                            Text(
                                "${backup.sizeBytes / 1024} KB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = viewModel::dismissPicker) { Text("Cancel") } },
        )
    }

    confirmRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "All data on this phone will be replaced with the backup from " +
                        "${backup.modifiedAt.display()}. The current data is kept as a local " +
                        "safety copy first. The app will restart.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = null
                    viewModel.restore(backup)
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } },
        )
    }
}

/**
 * After a restore the Room singleton is closed, so the process has to start fresh.
 * Relaunch the app's entry activity and end this process.
 */
private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
