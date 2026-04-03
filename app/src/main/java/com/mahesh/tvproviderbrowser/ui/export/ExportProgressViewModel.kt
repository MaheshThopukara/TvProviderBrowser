package com.mahesh.tvproviderbrowser.ui.export

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahesh.tvproviderbrowser.data.export.CsvAllTablesExporter
import com.mahesh.tvproviderbrowser.data.export.CsvExporter
import com.mahesh.tvproviderbrowser.data.export.ExportFileWriter
import com.mahesh.tvproviderbrowser.data.model.TableResult
import com.mahesh.tvproviderbrowser.data.model.TvTable
import com.mahesh.tvproviderbrowser.data.model.TvTables
import com.mahesh.tvproviderbrowser.data.repository.TvProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** UI states for the export progress screen. */
sealed interface ExportState {
    data object ChoosingLocation : ExportState
    data class Exporting(val message: String) : ExportState
    data class Success(val filePath: String, val rowCount: Int) : ExportState
    data class Error(val message: String) : ExportState
}

@HiltViewModel
class ExportProgressViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TvProviderRepository,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    val exportMode: String = savedStateHandle["exportMode"] ?: "all"
    private val tableId: String = savedStateHandle["tableId"] ?: ""

    val table: TvTable? = if (exportMode == "single") TvTables.findById(tableId) else null

    private val _state = MutableStateFlow<ExportState>(ExportState.ChoosingLocation)
    val state: StateFlow<ExportState> = _state.asStateFlow()

    val fileName: String = run {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        if (exportMode == "single") {
            "${tableId}_export_$timestamp.csv"
        } else {
            "tvprovider_all_tables_$timestamp.csv"
        }
    }

    /** Called when the user picks a storage location. */
    fun startExport(locationType: String, filePath: String?) {
        if (_state.value is ExportState.Exporting) return

        viewModelScope.launch {
            try {
                _state.value = ExportState.Exporting("Preparing…")

                val (outputStream, displayPath) = withContext(Dispatchers.IO) {
                    when (locationType) {
                        ExportLocationPickerFragment.TYPE_FILE ->
                            ExportFileWriter.createFileAtPath(File(filePath!!), fileName)
                        else ->
                            ExportFileWriter.createDownloadFile(appContext, fileName)
                    }
                }

                val rowCount = if (exportMode == "single") {
                    exportSingleTable(outputStream)
                } else {
                    exportAllTables(outputStream)
                }

                _state.value = ExportState.Success(displayPath, rowCount)
            } catch (e: Exception) {
                _state.value = ExportState.Error(e.message ?: "Export failed")
            }
        }
    }

    /** Resets to location chooser so the user can try again. */
    fun retry() {
        _state.value = ExportState.ChoosingLocation
    }

    // ── Single-table export ──────────────────────────────────────────────

    private suspend fun exportSingleTable(outputStream: OutputStream): Int {
        val t = table ?: throw IllegalStateException("Table not found: $tableId")
        _state.value = ExportState.Exporting("Reading ${t.name}…")

        val result = withContext(Dispatchers.IO) {
            repository.queryAllRows(uri = t.uri, dbColumnOrder = t.dbColumnOrder)
        }

        _state.value = ExportState.Exporting("Writing ${result.rows.size} rows…")
        withContext(Dispatchers.IO) {
            outputStream.use { CsvExporter.write(result, it) }
        }
        return result.rows.size
    }

    // ── All-tables export ────────────────────────────────────────────────

    private suspend fun exportAllTables(outputStream: OutputStream): Int {
        val allData = mutableMapOf<TvTable, TableResult>()
        var totalRows = 0

        for (table in TvTables.all) {
            _state.value = ExportState.Exporting("Reading ${table.name}…")
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    repository.queryAllRows(uri = table.uri, dbColumnOrder = table.dbColumnOrder)
                }.getOrNull() ?: TableResult(columns = emptyList(), rows = emptyList())
            }
            allData[table] = result
            totalRows += result.rows.size
        }

        _state.value = ExportState.Exporting("Writing $totalRows rows…")
        withContext(Dispatchers.IO) {
            outputStream.use { CsvAllTablesExporter.write(allData, it) }
        }
        return totalRows
    }
}


