package com.mdibrahim.expensetracker.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import com.mdibrahim.expensetracker.core.database.ExpenseDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

class InvalidBackupException(message: String) : IOException(message)

/**
 * Taking a consistent copy of the live database, and swapping a backup in.
 *
 * Room runs SQLite in WAL mode, so recent writes may still be in the -wal file
 * rather than the main .db file. Copying the .db file alone could silently drop
 * them, which is why the snapshot goes through SQLite rather than a plain file copy.
 */
@Singleton
class LocalDatabaseBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ExpenseDatabase,
) {
    private val dbFile: File get() = context.getDatabasePath(ExpenseDatabase.NAME)

    /** Writes a self-contained, consistent copy of the database to [destination]. */
    fun snapshotTo(destination: File) {
        destination.delete()
        val db = database.openHelper.writableDatabase
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // SQLite 3.27+ (Android 11+): one statement, transactionally consistent,
            // includes WAL contents, and compacts the copy.
            db.execSQL("VACUUM INTO ?", arrayOf(destination.absolutePath))
        } else {
            // Older SQLite: fold the WAL into the main file, then copy it while holding
            // the write lock so nothing can change it mid-copy.
            db.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
            database.runInTransaction { dbFile.copyTo(destination, overwrite = true) }
        }
    }

    /**
     * Checks that [file] is an intact database from this app at a schema version
     * this build can open. Room has no downgrade path, so a backup from a newer
     * app version would crash on launch -- it's rejected here instead.
     */
    fun validate(file: File) {
        val db = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            throw InvalidBackupException("Not a database file")
        }
        db.use {
            val check = it.rawQuery("PRAGMA quick_check", null).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            if (check != "ok") throw InvalidBackupException("Backup file is corrupted")
            val hasTransactions = it.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name='transactions'", null,
            ).use { c -> c.moveToFirst() }
            if (!hasTransactions) throw InvalidBackupException("Not an Expense Tracker backup")
            when {
                it.version < 1 -> throw InvalidBackupException("Not an Expense Tracker backup")
                it.version > ExpenseDatabase.VERSION -> throw InvalidBackupException(
                    "Backup is from a newer version of the app. Update the app, then restore.",
                )
            }
        }
    }

    /**
     * Replaces the live database with [validatedBackup], keeping the current data as
     * a local safety copy first. Closes the database: the app MUST restart
     * afterwards, because the Room instance is a process-wide singleton.
     */
    fun replaceWith(validatedBackup: File) {
        snapshotTo(File(context.filesDir, PRE_RESTORE_COPY))
        database.close()
        listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
        validatedBackup.copyTo(dbFile, overwrite = true)
    }

    companion object {
        /** The data that was on the phone right before the last restore. */
        const val PRE_RESTORE_COPY = "pre-restore-backup.db"
    }
}
