package com.mdibrahim.expensetracker.data.backup

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class BackupStatus(
    val autoBackupEnabled: Boolean,
    val lastBackupAt: Instant?,
    /** Set when the most recent backup attempt (manual or automatic) failed. */
    val lastError: String?,
)

/** Backup settings and last-result, in SharedPreferences, exposed as a StateFlow. */
@Singleton
class BackupPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)
    private val _status = MutableStateFlow(read())
    val status: StateFlow<BackupStatus> = _status.asStateFlow()

    fun setAutoBackup(enabled: Boolean) = write { putBoolean(KEY_AUTO, enabled) }

    fun recordSuccess(at: Instant) = write {
        putLong(KEY_LAST_AT, at.toEpochMilli())
        remove(KEY_ERROR)
    }

    fun recordFailure(message: String) = write { putString(KEY_ERROR, message) }

    private fun write(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit(commit = true, action = block)
        _status.value = read()
    }

    private fun read() = BackupStatus(
        autoBackupEnabled = prefs.getBoolean(KEY_AUTO, false),
        lastBackupAt = prefs.getLong(KEY_LAST_AT, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        lastError = prefs.getString(KEY_ERROR, null),
    )

    private companion object {
        const val KEY_AUTO = "auto_backup"
        const val KEY_LAST_AT = "last_backup_at"
        const val KEY_ERROR = "last_error"
    }
}
