package com.mahesh.tvproviderbrowser.data.export

import com.mahesh.tvproviderbrowser.data.model.TableResult
import com.mahesh.tvproviderbrowser.data.model.TvTable
import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * Writes multiple [TvTable] results into a single CSV [OutputStream].
 *
 * Each table is separated by a header row:
 * ```
 * === TABLE: Channels (150 rows) ===
 * _id,package_name,display_name,...
 * 1,com.example,Channel 1,...
 * ...
 * ```
 */
object CsvAllTablesExporter {

    fun write(allData: Map<TvTable, TableResult>, outputStream: OutputStream) {
        OutputStreamWriter(outputStream, Charsets.UTF_8).buffered().use { writer ->
            var first = true
            for ((table, result) in allData) {
                if (!first) {
                    writer.newLine()
                }
                first = false

                // Table separator header
                writer.write("=== TABLE: ${table.name} (${result.rows.size} rows) ===")
                writer.newLine()

                // Column header
                if (result.columns.isNotEmpty()) {
                    writer.write(result.columns.joinToString(",") { escapeCsv(it) })
                    writer.newLine()
                }

                // Data rows
                for (row in result.rows) {
                    writer.write(row.joinToString(",") { escapeCsv(it) })
                    writer.newLine()
                }
            }
        }
    }

    private fun escapeCsv(value: String?): String {
        if (value == null) return ""
        return if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}

