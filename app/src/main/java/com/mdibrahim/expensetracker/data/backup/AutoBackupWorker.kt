package com.mdibrahim.expensetracker.data.backup

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Daily backup in the background. Uses a Hilt EntryPoint (like SmsReceiver) so no
 * hilt-work dependency or custom WorkManager initialisation is needed.
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun authorizer(): DriveAuthorizer
        fun backupManager(): BackupManager
        fun preferences(): BackupPreferences
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        return try {
            when (val auth = deps.authorizer().authorize()) {
                is DriveAuth.Granted -> {
                    deps.backupManager().backup(auth.accessToken)
                    Result.success()
                }
                is DriveAuth.NeedsConsent -> {
                    // Can't show a consent screen from the background.
                    deps.preferences().recordFailure("Google Drive access needs renewing — tap Back up now.")
                    Result.failure()
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Auto-backup failed; will retry", e)
            deps.preferences().recordFailure("Auto-backup failed: ${e.message}")
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Auto-backup failed", e)
            deps.preferences().recordFailure("Auto-backup failed: ${e.message}")
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "AutoBackupWorker"
        private const val WORK_NAME = "daily-drive-backup"

        fun schedule(context: Context, enabled: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (!enabled) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            // KEEP: re-enabling doesn't reset the schedule of an already-queued job.
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** Injectable wrapper so ViewModels don't need to hold a Context. */
class AutoBackupScheduler @javax.inject.Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    fun setEnabled(enabled: Boolean) = AutoBackupWorker.schedule(context, enabled)
}
