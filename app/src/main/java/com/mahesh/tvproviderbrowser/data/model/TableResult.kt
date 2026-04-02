package com.mahesh.tvproviderbrowser.data.model

/**
 * Holds the result of a paged query against a TvProvider table.
 *
 * @property columns    Ordered list of column names.
 * @property rows       Page of data — each row is a list matching [columns] order.
 * @property totalCount Total number of rows in the table (not just this page).
 */
data class TableResult(
    val columns: List<String>,
    val rows: List<List<String?>>,
    val totalCount: Int? = null,
)


