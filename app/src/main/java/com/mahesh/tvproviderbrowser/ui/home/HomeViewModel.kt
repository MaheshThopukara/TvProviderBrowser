package com.mahesh.tvproviderbrowser.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahesh.tvproviderbrowser.data.model.TvTables
import com.mahesh.tvproviderbrowser.data.repository.TvProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state exposed by [HomeViewModel] to the home screen.
 */
data class HomeUiState(
    val tableCount: Int = 0,
    val totalColumns: Int = 0,
    val dbSizeText: String = "…",
    val isDbSizeLoading: Boolean = false,
)

/**
 * ViewModel for the Home screen.
 *
 * Manages table statistics and database size computation,
 * surviving configuration changes and view recreation.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: TvProviderRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            tableCount = TvTables.all.size,
            totalColumns = TvTables.all.sumOf { it.dbColumnOrder.size },
        ),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** Index of the last table card the user opened (survives view recreation). */
    var lastOpenedTableIndex: Int = -1

    /** True after the first time the home screen has been shown. */
    var hasShownHomeBefore: Boolean = false

    /** Whether DB size has already been loaded — skip redundant work on re-navigation. */
    private var dbSizeLoaded = false

    fun loadTotalDbSize() {
        if (dbSizeLoaded) return

        _uiState.value = _uiState.value.copy(isDbSizeLoading = true)

        viewModelScope.launch {
            // Try reading the actual tv.db file size first
            val realSize = runCatching { repository.getTvDbFileSize() }.getOrNull()
            if (realSize != null) {
                val text = formatStorageSize(realSize)
                _uiState.value = _uiState.value.copy(dbSizeText = text, isDbSizeLoading = false)
                dbSizeLoaded = true
                return@launch
            }

            // Fall back to estimating by scanning all table data
            var totalBytes = 0L
            for (table in TvTables.all) {
                totalBytes += runCatching {
                    repository.estimateTableStorageSize(table.uri)
                }.getOrDefault(0L)
            }
            val text = "≈ ${formatStorageSize(totalBytes)}"
            _uiState.value = _uiState.value.copy(dbSizeText = text, isDbSizeLoading = false)
            dbSizeLoaded = true
        }
    }

    companion object {
        fun formatStorageSize(bytes: Long): String = when {
            bytes < 1_024L -> "$bytes B"
            bytes < 1_048_576L -> "%.1f KB".format(bytes / 1_024.0)
            bytes < 1_073_741_824L -> "%.1f MB".format(bytes / 1_048_576.0)
            else -> "%.2f GB".format(bytes / 1_073_741_824.0)
        }
    }
}

