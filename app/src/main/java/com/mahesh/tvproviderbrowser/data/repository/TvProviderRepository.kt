package com.mahesh.tvproviderbrowser.data.repository

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.BaseColumns
import com.mahesh.tvproviderbrowser.data.model.TableResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that reads data from the Android TvProvider content provider.
 *
 * All queries run on [Dispatchers.IO] so they never block the main thread.
 */
@Singleton
class TvProviderRepository @Inject constructor(
    private val contentResolver: ContentResolver,
) {
    companion object {
        /** Number of rows returned per page. */
        const val PAGE_SIZE = 50

        /** Known paths where TvProvider stores tv.db on various Android TV builds. */
        private val TV_DB_PATHS = listOf(
            "/data/data/com.android.providers.tv/databases/tv.db",
            "/data/user/0/com.android.providers.tv/databases/tv.db",
            "/data/user_de/0/com.android.providers.tv/databases/tv.db",
        )
    }

    /**
     * Queries a single page of data from the given [uri].
     *
     * @param uri  Content URI of the TvProvider table.
     * @param page Zero-based page index.
     * @return [TableResult] containing column names, a page of rows, and the total row count.
     */
    suspend fun queryTablePage(
        uri: Uri,
        page: Int,
        dbColumnOrder: List<String> = emptyList(),
    ): TableResult = withContext(Dispatchers.IO) {
        val rows = mutableListOf<List<String?>>()
        var columns = emptyList<String>()

        val offset = page * PAGE_SIZE
        val cursor = contentResolver.query(
            uri, null, null, null,
            "${BaseColumns._ID} ASC",
        )

        cursor?.use { c ->
            columns = (0 until c.columnCount).map { c.getColumnName(it) }

            // Client-side paging: skip to the offset row, then read PAGE_SIZE rows.
            if (c.count > offset && c.moveToPosition(offset)) {
                var count = 0
                do {
                    rows.add(readRow(c))
                    count++
                } while (c.moveToNext() && count < PAGE_SIZE)
            }
        }

        reorderByDbColumnOrder(
            columns = columns,
            rows = rows,
            dbColumnOrder = dbColumnOrder,
        )
    }

    suspend fun queryTotalCount(uri: Uri): Int = withContext(Dispatchers.IO) {
        contentResolver.query(uri, arrayOf(BaseColumns._ID), null, null, null)?.use { cursor ->
            cursor.count
        } ?: 0
    }

    /**
     * Estimates the data payload size (in bytes) of a single table at [uri].
     *
     * Scans every row and sums up byte lengths of each column value.
     * This is an **approximation** — it excludes SQLite overhead (page headers,
     * B-tree nodes, indexes, free space, WAL). The actual on-disk size per table
     * cannot be determined through a [ContentResolver].
     */
    suspend fun estimateTableStorageSize(uri: Uri): Long = withContext(Dispatchers.IO) {
        var totalBytes = 0L
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                for (i in 0 until cursor.columnCount) {
                    totalBytes += columnByteSize(cursor, i)
                }
            }
        }
        totalBytes
    }

    /**
     * Returns the actual `tv.db` file size in bytes, or `null` if the file is
     * not accessible (requires the device to be rooted or have relaxed SELinux).
     *
     * Also includes the WAL and journal files if present, since they contribute
     * to the total on-disk footprint.
     */
    suspend fun getTvDbFileSize(): Long? = withContext(Dispatchers.IO) {
        for (path in TV_DB_PATHS) {
            val dbFile = File(path)
            if (dbFile.exists() && dbFile.canRead()) {
                var size = dbFile.length()
                // Include WAL file size if present
                val walFile = File("$path-wal")
                if (walFile.exists() && walFile.canRead()) {
                    size += walFile.length()
                }
                // Include journal file size if present
                val journalFile = File("$path-journal")
                if (journalFile.exists() && journalFile.canRead()) {
                    size += journalFile.length()
                }
                return@withContext size
            }
        }
        null
    }

    // ── Private helpers ──────────────────────────────────────────────────

    /** Reads a single row from the cursor, handling each column type safely. */
    private fun readRow(cursor: Cursor): List<String?> = buildList {
        for (i in 0 until cursor.columnCount) {
            add(readColumn(cursor, i))
        }
    }

    /** Returns the approximate byte size of a single column value in the cursor. */
    private fun columnByteSize(cursor: Cursor, index: Int): Long = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_STRING  -> cursor.getString(index)?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L
        Cursor.FIELD_TYPE_INTEGER -> 8L   // SQLite stores integers in up to 8 bytes
        Cursor.FIELD_TYPE_FLOAT  -> 8L   // 64-bit IEEE float
        Cursor.FIELD_TYPE_BLOB   -> cursor.getBlob(index)?.size?.toLong() ?: 0L
        Cursor.FIELD_TYPE_NULL   -> 0L
        else                     -> 0L
    }

    /** Reads a single column value, converting BLOBs to a size description. */
    private fun readColumn(cursor: Cursor, index: Int): String? = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_STRING  -> cursor.getString(index)
        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index).toString()
        Cursor.FIELD_TYPE_FLOAT  -> cursor.getDouble(index).toString()
        Cursor.FIELD_TYPE_BLOB   -> {
            val blob = cursor.getBlob(index)
            "BLOB(${blob?.size ?: 0} bytes)"
        }
        Cursor.FIELD_TYPE_NULL   -> null
        else                     -> "?"
    }

    /** Reorders cursor columns to match db schema order and appends unknown columns at the end. */
    private fun reorderByDbColumnOrder(
        columns: List<String>,
        rows: List<List<String?>>,
        dbColumnOrder: List<String>,
    ): TableResult {
        if (columns.isEmpty() || dbColumnOrder.isEmpty()) {
            return TableResult(columns = columns, rows = rows)
        }

        val indexByLowerName = columns.mapIndexed { index, name -> name.lowercase() to index }.toMap()
        val orderedKnownColumns = dbColumnOrder.filter { requested ->
            indexByLowerName.containsKey(requested.lowercase())
        }
        val remainingColumns = columns.filterNot { existing ->
            dbColumnOrder.any { requested -> requested.equals(existing, ignoreCase = true) }
        }
        val orderedColumns = orderedKnownColumns + remainingColumns

        val reorderedRows = rows.map { row ->
            orderedColumns.map { columnName ->
                row.getOrNull(indexByLowerName[columnName.lowercase()] ?: -1)
            }
        }

        return TableResult(columns = orderedColumns, rows = reorderedRows)
    }
}


