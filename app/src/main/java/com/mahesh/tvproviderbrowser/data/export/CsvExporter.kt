package com.mahesh.tvproviderbrowser.data.export

import com.mahesh.tvproviderbrowser.data.model.TableResult
import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * Writes [TableResult] data as RFC 4180 compliant CSV to an [OutputStream].
 */
object CsvExporter {

    /**
     * Writes the table data as CSV to the given [outputStream].
     * Streams row-by-row so memory usage stays flat even for large tables.
     */
    fun write(result: TableResult, outputStream: OutputStream) {
        OutputStreamWriter(outputStream, Charsets.UTF_8).buffered().use { writer ->
            // Header row
            writer.write(result.columns.joinToString(",") { escapeCsv(it) })
            writer.newLine()

            // Data rows
            for (row in result.rows) {
                writer.write(row.joinToString(",") { escapeCsv(it) })
                writer.newLine()
            }
        }
    }

    /**
     * Escapes a CSV field per RFC 4180:
     * - null → empty string
     * - Fields containing comma, quote, or newline are wrapped in double quotes
     * - Double quotes inside are escaped as ""
     */
    private fun escapeCsv(value: String?): String {
        if (value == null) return ""
        return if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}

