package com.mdibrahim.expensetracker.ui.screens.settings

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.mdibrahim.expensetracker.data.backup.AutoBackupScheduler
import com.mdibrahim.expensetracker.data.backup.BackupManager
import com.mdibrahim.expensetracker.data.backup.BackupPreferences
import com.mdibrahim.expensetracker.data.backup.BackupStatus
import com.mdibrahim.expensetracker.data.backup.DriveAuth
import com.mdibrahim.expensetracker.data.backup.DriveAuthorizer
import com.mdibrahim.expensetracker.data.backup.InvalidBackupException
import com.mdibrahim.expensetracker.data.backup.RemoteBackup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val status: BackupStatus = BackupStatus(autoBackupEnabled = false, lastBackupAt = null, lastError = null),
    /** Non-null while an operation runs, e.g. "Backing up…". */
    val busyLabel: String? = null,
    val message: String? = null,
    /** The UI launches this consent screen, then calls [BackupViewModel.onConsentResult]. */
    val consentRequest: PendingIntent? = null,
    /** Non-null shows the restore picker. */
    val backups: List<RemoteBackup>? = null,
    /** Set after a successful restore; the UI restarts the app. */
    val restartRequired: Boolean = false,
)

private sealed interface DriveAction {
    data object Backup : DriveAction
    data object ListForRestore : DriveAction
    data object EnableAutoBackup : DriveAction
    data class Restore(val backup: RemoteBackup) : DriveAction
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val authorizer: DriveAuthorizer,
    private val backupManager: BackupManager,
    private val prefs: BackupPreferences,
    private val scheduler: AutoBackupScheduler,
) : ViewModel() {

    private val local = MutableStateFlow(BackupUiState())

    /** Waiting for the user to finish the consent screen. */
    private var actionAwaitingConsent: DriveAction? = null

    val uiState: StateFlow<BackupUiState> = combine(local, prefs.status) { state, status ->
        state.copy(status = status)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState(status = prefs.status.value))

    fun backupNow() = start(DriveAction.Backup)
    fun chooseBackupToRestore() = start(DriveAction.ListForRestore)
    fun restore(backup: RemoteBackup) {
        local.update { it.copy(backups = null) }
        start(DriveAction.Restore(backup))
    }

    fun setAutoBackup(enabled: Boolean) {
        if (enabled) {
            // Get the Drive grant now, while there's UI to show consent; the
            // background worker can only reuse an existing grant.
            start(DriveAction.EnableAutoBackup)
        } else {
            prefs.setAutoBackup(false)
            scheduler.setEnabled(false)
        }
    }

    fun dismissPicker() = local.update { it.copy(backups = null) }
    fun dismissMessage() = local.update { it.copy(message = null) }
    fun consentLaunched() = local.update { it.copy(consentRequest = null) }

    fun onConsentResult(data: Intent?) {
        val action = actionAwaitingConsent ?: return
        actionAwaitingConsent = null
        val token = authorizer.tokenFromConsentResult(data)
        if (token == null) {
            local.update { it.copy(busyLabel = null, message = "Google Drive access wasn't granted.") }
            return
        }
        viewModelScope.launch { run(action, token) }
    }

    private fun start(action: DriveAction) {
        if (local.value.busyLabel != null) return
        local.update { it.copy(busyLabel = labelFor(action), message = null) }
        viewModelScope.launch {
            try {
                when (val auth = authorizer.authorize()) {
                    is DriveAuth.Granted -> run(action, auth.accessToken)
                    is DriveAuth.NeedsConsent -> {
                        actionAwaitingConsent = action
                        local.update { it.copy(consentRequest = auth.pendingIntent) }
                    }
                }
            } catch (e: Exception) {
                fail(action, e)
            }
        }
    }

    private suspend fun run(action: DriveAction, token: String) {
        try {
            when (action) {
                DriveAction.Backup -> {
                    backupManager.backup(token)
                    done("Backed up to Google Drive.")
                }
                DriveAction.ListForRestore -> {
                    val backups = backupManager.listBackups(token)
                    if (backups.isEmpty()) {
                        done("No backups found in Google Drive yet.")
                    } else {
                        local.update { it.copy(busyLabel = null, backups = backups) }
                    }
                }
                DriveAction.EnableAutoBackup -> {
                    prefs.setAutoBackup(true)
                    scheduler.setEnabled(true)
                    done("Daily backup is on. It runs when the phone is online and the battery isn't low.")
                }
                is DriveAction.Restore -> {
                    backupManager.restore(token, action.backup)
                    local.update { it.copy(busyLabel = null, restartRequired = true) }
                }
            }
        } catch (e: Exception) {
            fail(action, e)
        }
    }

    private fun done(message: String) = local.update { it.copy(busyLabel = null, message = message) }

    private fun fail(action: DriveAction, e: Exception) {
        Log.e("Backup", "$action failed", e)
        val message = when {
            e is InvalidBackupException -> "Can't restore: ${e.message}"
            // DEVELOPER_ERROR: OAuth client in Cloud Console doesn't match this build.
            e is ApiException && e.statusCode == CommonStatusCodes.DEVELOPER_ERROR ->
                "Google sign-in isn't set up for this build. Check the Android OAuth client's " +
                    "package name and SHA-1 in Google Cloud Console."
            e is ApiException -> "Google sign-in failed (code ${e.statusCode})."
            else -> "Failed: ${e.message ?: e.javaClass.simpleName}"
        }
        if (action == DriveAction.Backup) prefs.recordFailure(message)
        done(message)
    }

    private fun labelFor(action: DriveAction) = when (action) {
        DriveAction.Backup -> "Backing up…"
        DriveAction.ListForRestore -> "Loading backups…"
        DriveAction.EnableAutoBackup -> "Connecting to Google Drive…"
        is DriveAction.Restore -> "Restoring…"
    }
}
