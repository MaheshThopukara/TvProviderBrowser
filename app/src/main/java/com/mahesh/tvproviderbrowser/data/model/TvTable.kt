package com.mahesh.tvproviderbrowser.data.model

import android.net.Uri

/**
 * Represents a table exposed by the TvProvider content provider.
 *
 * @property id             Stable identifier used as a navigation argument.
 * @property name           Human-readable table name.
 * @property icon           Emoji icon shown in the UI.
 * @property uri            Content URI to query via [android.content.ContentResolver].
 * @property dbColumnOrder  Column names in expected TvProvider schema order.
 */
data class TvTable(
    val id: String,
    val name: String,
    val icon: String,
    val description: String = "",
    val uri: Uri,
    val dbColumnOrder: List<String> = emptyList(),
)
