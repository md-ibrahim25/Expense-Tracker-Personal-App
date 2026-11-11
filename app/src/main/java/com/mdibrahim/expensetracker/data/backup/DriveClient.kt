package com.mdibrahim.expensetracker.data.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

class DriveApiException(val httpCode: Int, message: String) : IOException("Drive API $httpCode: $message")

/**
 * The handful of Drive v3 REST calls backup needs, over HttpURLConnection. A full
 * Google API client library would add megabytes for five requests.
 */
@Singleton
class DriveClient @Inject constructor() {

    suspend fun findOrCreateFolder(token: String): String = withContext(Dispatchers.IO) {
        val q = "mimeType=${BackupFiles.queryLiteral(BackupFiles.FOLDER_MIME)} and " +
            "name=${BackupFiles.queryLiteral(BackupFiles.FOLDER_NAME)} and trashed=false"
        val existing = JSONObject(get(token, "$FILES?q=${enc(q)}&fields=${enc("files(id)")}"))
            .getJSONArray("files")
        if (existing.length() > 0) return@withContext existing.getJSONObject(0).getString("id")

        val body = JSONObject()
            .put("name", BackupFiles.FOLDER_NAME)
            .put("mimeType", BackupFiles.FOLDER_MIME)
            .toString().toByteArray()
        JSONObject(send(token, "POST", "$FILES?fields=id", "application/json; charset=UTF-8", body))
            .getString("id")
    }

    suspend fun listBackups(token: String, folderId: String): List<RemoteBackup> = withContext(Dispatchers.IO) {
        val q = "${BackupFiles.queryLiteral(folderId)} in parents and trashed=false and " +
            "mimeType!=${BackupFiles.queryLiteral(BackupFiles.FOLDER_MIME)}"
        val url = "$FILES?q=${enc(q)}&fields=${enc("files($FILE_FIELDS)")}" +
            "&orderBy=${enc("modifiedTime desc")}&pageSize=100"
        val files = JSONObject(get(token, url)).getJSONArray("files")
        (0 until files.length()).map { parse(files.getJSONObject(it)) }
    }

    suspend fun upload(token: String, folderId: String, name: String, file: File): RemoteBackup =
        withContext(Dispatchers.IO) {
            val boundary = "backup-" + UUID.randomUUID()
            val body = BackupFiles.multipartBody(
                boundary,
                BackupFiles.metadataJson(name, folderId),
                file.readBytes(),
            )
            val url = "$UPLOAD?uploadType=multipart&fields=${enc(FILE_FIELDS)}"
            parse(JSONObject(send(token, "POST", url, "multipart/related; boundary=$boundary", body)))
        }

    suspend fun download(token: String, fileId: String, destination: File) = withContext(Dispatchers.IO) {
        val connection = open(token, "GET", "$FILES/$fileId?alt=media")
        try {
            checkOk(connection)
            connection.inputStream.use { input -> destination.outputStream().use { input.copyTo(it) } }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun delete(token: String, fileId: String) = withContext(Dispatchers.IO) {
        send(token, "DELETE", "$FILES/$fileId", contentType = null, body = null)
        Unit
    }

    private fun get(token: String, url: String): String = send(token, "GET", url, null, null)

    private fun send(token: String, method: String, url: String, contentType: String?, body: ByteArray?): String {
        val connection = open(token, method, url)
        try {
            if (body != null) {
                connection.doOutput = true
                contentType?.let { connection.setRequestProperty("Content-Type", it) }
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            checkOk(connection)
            return connection.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(token: String, method: String, url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token")
        }

    private fun checkOk(connection: HttpURLConnection) {
        val code = connection.responseCode
        if (code !in 200..299) {
            val detail = connection.errorStream?.use { it.readBytes().decodeToString() }.orEmpty()
            throw DriveApiException(code, detail.take(300))
        }
    }

    private fun parse(json: JSONObject) = RemoteBackup(
        id = json.getString("id"),
        name = json.getString("name"),
        modifiedAt = Instant.parse(json.getString("modifiedTime")),
        // Drive returns size as a string.
        sizeBytes = json.optString("size", "0").toLongOrNull() ?: 0,
    )

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val FILES = "https://www.googleapis.com/drive/v3/files"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
        const val FILE_FIELDS = "id,name,modifiedTime,size"
    }
}
