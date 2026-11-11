package com.mdibrahim.expensetracker.data.backup

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup and restore against Google Drive. Takes an access token rather than
 * authorising itself, because only the UI can show the consent screen; the
 * background worker can only use an existing grant.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val drive: DriveClient,
    private val local: LocalDatabaseBackup,
    private val prefs: BackupPreferences,
) {
    // A manual backup and the daily worker must never interleave on the same files.
    private val lock = Mutex()

    suspend fun backup(token: String): RemoteBackup = lock.withLock {
        withContext(Dispatchers.IO) {
            val snapshot = File(context.cacheDir, "backup-upload.db")
            try {
                local.snapshotTo(snapshot)
                val now = Instant.now()
                val folderId = drive.findOrCreateFolder(token)
                val uploaded = drive.upload(token, folderId, BackupFiles.fileName(now), snapshot)
                prefs.recordSuccess(now)
                prune(token, folderId)
                uploaded
            } finally {
                snapshot.delete()
            }
        }
    }

    suspend fun listBackups(token: String): List<RemoteBackup> =
        drive.listBackups(token, drive.findOrCreateFolder(token)).sortedByDescending { it.modifiedAt }

    /** Downloads, validates, then swaps in [backup]. The app must restart after this returns. */
    suspend fun restore(token: String, backup: RemoteBackup) = lock.withLock {
        withContext(Dispatchers.IO) {
            val download = File(context.cacheDir, "backup-restore.db")
            try {
                drive.download(token, backup.id, download)
                // Validate before touching anything: a bad file must leave current data intact.
                local.validate(download)
                local.replaceWith(download)
            } finally {
                download.delete()
            }
        }
    }

    /** Best effort: a failed prune leaves an extra old backup, which is harmless. */
    private suspend fun prune(token: String, folderId: String) {
        runCatching {
            BackupFiles.toPrune(drive.listBackups(token, folderId)).forEach { drive.delete(token, it.id) }
        }.onFailure { Log.w("BackupManager", "Pruning old backups failed", it) }
    }
}
