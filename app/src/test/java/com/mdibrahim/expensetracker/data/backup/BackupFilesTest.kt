package com.mdibrahim.expensetracker.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BackupFilesTest {

    private fun backup(id: String, at: String) = RemoteBackup(id, "$id.db", Instant.parse(at), 1)

    @Test
    fun `file name is a sortable UTC timestamp`() {
        assertEquals(
            "expense-tracker-2026-11-11_14-03-09Z.db",
            BackupFiles.fileName(Instant.parse("2026-11-11T14:03:09.500Z")),
        )
    }

    @Test
    fun `pruning keeps the newest and returns the rest`() {
        val backups = listOf(
            backup("b", "2026-11-02T00:00:00Z"),
            backup("d", "2026-11-04T00:00:00Z"),
            backup("a", "2026-11-01T00:00:00Z"),
            backup("c", "2026-11-03T00:00:00Z"),
        )
        assertEquals(listOf("b", "a"), BackupFiles.toPrune(backups, keep = 2).map { it.id })
    }

    @Test
    fun `nothing is pruned at or under the limit`() {
        val backups = listOf(backup("a", "2026-11-01T00:00:00Z"))
        assertTrue(BackupFiles.toPrune(backups, keep = 1).isEmpty())
        assertTrue(BackupFiles.toPrune(emptyList()).isEmpty())
    }

    @Test
    fun `metadata json`() {
        assertEquals(
            """{"name":"x.db","parents":["folder123"]}""",
            BackupFiles.metadataJson("x.db", "folder123"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `metadata json refuses quotes`() {
        BackupFiles.metadataJson("""x".db""", "folder")
    }

    @Test
    fun `multipart body frames metadata and content`() {
        val body = String(BackupFiles.multipartBody("B", "{}", byteArrayOf(65, 66)))
        assertEquals(
            "--B\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n{}\r\n" +
                "--B\r\nContent-Type: application/octet-stream\r\n\r\nAB\r\n--B--\r\n",
            body,
        )
    }

    @Test
    fun `query literal escapes quotes`() {
        assertEquals("""'Tom\'s'""", BackupFiles.queryLiteral("Tom's"))
    }
}
