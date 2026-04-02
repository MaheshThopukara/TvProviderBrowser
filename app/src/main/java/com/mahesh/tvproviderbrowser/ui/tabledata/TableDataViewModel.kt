package com.mahesh.tvproviderbrowser.ui.tabledata

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahesh.tvproviderbrowser.data.model.TableResult
import com.mahesh.tvproviderbrowser.data.model.TvTable
import com.mahesh.tvproviderbrowser.data.model.TvTables
import com.mahesh.tvproviderbrowser.data.repository.TvProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Represents the possible states of a table data page load.
 */
sealed interface PageState {
    data object Loading : PageState
    data class Success(
        val result: TableResult,
        val totalCount: Int?,
        val sizeBytes: Long?,
        val page: Int,
        val hasNextPage: Boolean,
        val isInitialLoad: Boolean,
        val isForward: Boolean,
    ) : PageState
    data class Error(val message: String) : PageState
}

/**
 * ViewModel for the Table Data screen.
 *
 * Owns all pagination logic, data loading, and storage size estimation.
 * The Fragment only observes [pageState] and renders accordingly.
 */
@HiltViewModel
class TableDataViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TvProviderRepository,
) : ViewModel() {

    val table: TvTable = run {
        val tableId: String = savedStateHandle["tableId"] ?: ""
        TvTables.findById(tableId) ?: TvTables.all.first()
    }

    private val _pageState = MutableStateFlow<PageState>(PageState.Loading)
    val pageState: StateFlow<PageState> = _pageState.asStateFlow()

    var currentPage: Int = 0
        private set

    private var cachedSizeBytes: Long? = null

    fun loadPage(targetPage: Int) {
        val isForward = targetPage > currentPage
        val isInitialLoad = currentPage == 0 && targetPage == 0
        currentPage = targetPage

        _pageState.value = PageState.Loading

        viewModelScope.launch {
            try {
                val result = repository.queryTablePage(
                    uri = table.uri,
                    page = currentPage,
                    dbColumnOrder = table.dbColumnOrder,
                )
                val total = runCatching { repository.queryTotalCount(table.uri) }.getOrNull()

                // Only compute storage size once (it doesn't change between pages)
                if (cachedSizeBytes == null) {
                    cachedSizeBytes = runCatching {
                        repository.estimateTableStorageSize(table.uri)
                    }.getOrNull()
                }

                val pageSize = TvProviderRepository.PAGE_SIZE
                val rangeEnd = currentPage * pageSize + result.rows.size
                val hasNextPage = result.rows.size == pageSize &&
                    (total == null || rangeEnd < total)

                _pageState.value = PageState.Success(
                    result = result,
                    totalCount = total,
                    sizeBytes = cachedSizeBytes,
                    page = currentPage,
                    hasNextPage = hasNextPage,
                    isInitialLoad = isInitialLoad,
                    isForward = isForward,
                )
            } catch (e: Exception) {
                _pageState.value = PageState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun nextPage() {
        val state = _pageState.value
        if (state is PageState.Success && state.hasNextPage) {
            loadPage(currentPage + 1)
        }
    }

    fun previousPage() {
        if (currentPage > 0) {
            loadPage(currentPage - 1)
        }
    }

    companion object {
        fun formatStorageSize(bytes: Long?): String {
            if (bytes == null) return "—"
            return when {
                bytes < 1_024L -> "$bytes B"
                bytes < 1_048_576L -> "%.1f KB".format(bytes / 1_024.0)
                bytes < 1_073_741_824L -> "%.1f MB".format(bytes / 1_048_576.0)
                else -> "%.2f GB".format(bytes / 1_073_741_824.0)
            }
        }
    }
}

