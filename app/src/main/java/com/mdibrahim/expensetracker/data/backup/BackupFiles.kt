package com.mdibrahim.expensetracker.data.backup

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A backup file in Google Drive. */
data class RemoteBackup(
    val id: String,
    val name: String,
    val modifiedAt: Instant,
    val sizeBytes: Long,
)

/**
 * Naming, retention and request-body rules for Drive backups. Pure Kotlin so it's
 * unit-testable on the JVM; the Android/HTTP side lives in [DriveClient].
 */
object BackupFiles {
    const val FOLDER_NAME = "Expense Tracker Backups"
    const val FOLDER_MIME = "application/vnd.google-apps.folder"
    const val FILE_MIME = "application/octet-stream"

    /**
     * Backups are append-only and the oldest are pruned beyond this count. Never
     * overwriting means a backup taken after something went wrong can't destroy the
     * last good one.
     */
    const val KEEP = 10

    private val STAMP: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneOffset.UTC)

    /** e.g. "expense-tracker-2026-11-11_14-03-09Z.db" -- sorts chronologically by name. */
    fun fileName(at: Instant): String = "expense-tracker-${STAMP.format(at)}Z.db"

    /** The backups to delete so that only the newest [keep] remain. */
    fun toPrune(backups: List<RemoteBackup>, keep: Int = KEEP): List<RemoteBackup> =
        backups.sortedByDescending { it.modifiedAt }.drop(keep)

    /** Drive file metadata. Names and ids we send never contain quotes or backslashes. */
    fun metadataJson(name: String, parentId: String): String {
        require(listOf(name, parentId).none { '"' in it || '\\' in it }) { "unsafe name or id" }
        return """{"name":"$name","parents":["$parentId"]}"""
    }

    /** multipart/related body for Drive's uploadType=multipart: metadata part + file part. */
    fun multipartBody(boundary: String, metadataJson: String, content: ByteArray): ByteArray {
        val head = "--$boundary\r\n" +
            "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
            "$metadataJson\r\n" +
            "--$boundary\r\n" +
            "Content-Type: $FILE_MIME\r\n\r\n"
        val tail = "\r\n--$boundary--\r\n"
        return head.toByteArray() + content + tail.toByteArray()
    }

    /** Drive search-query string literal: backslash and single quote are escaped. */
    fun queryLiteral(value: String): String =
        "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"
}
