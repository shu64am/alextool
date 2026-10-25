package com.alexmodzofc.tool.quiver

import com.alexmodzofc.tool.ui.listscreen.ListSortKey
import com.alexmodzofc.tool.ui.listscreen.ListSortOrder

fun filterAndSortFilterLists(
    items: List<FilterList>,
    query: String,
    sortKey: ListSortKey,
    sortOrder: ListSortOrder
): List<FilterList> {
    val filtered = if (query.isBlank()) {
        items
    } else {
        val lower = query.trim().lowercase()
        items.filter { it.name.lowercase().contains(lower) }
    }
    return when (sortKey) {
        ListSortKey.TITLE -> if (sortOrder == ListSortOrder.ASCENDING)
            filtered.sortedBy { it.name.lowercase() }
        else
            filtered.sortedByDescending { it.name.lowercase() }
        // "Date downloaded" for filter lists; downloadedAt is 0L for never-downloaded lists,
        // which naturally sorts them together regardless of order.
        ListSortKey.DATE_ADDED -> if (sortOrder == ListSortOrder.ASCENDING)
            filtered.sortedBy { it.downloadedAt }
        else
            filtered.sortedByDescending { it.downloadedAt }
    }
}

fun sectionLetterForFilterList(entry: FilterList, sortKey: ListSortKey): String = when (sortKey) {
    ListSortKey.TITLE -> entry.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
    ListSortKey.DATE_ADDED -> "#"
}
