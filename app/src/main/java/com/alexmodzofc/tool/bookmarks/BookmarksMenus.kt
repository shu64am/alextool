package com.alexmodzofc.tool.bookmarks
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DateRange

import com.alexmodzofc.tool.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alexmodzofc.tool.ui.listscreen.ListMenuItem
import com.alexmodzofc.tool.ui.listscreen.ListSortOrder
import com.alexmodzofc.tool.ui.listscreen.PopupShape
import com.alexmodzofc.tool.ui.theme.LocalAlexToolColors

@Composable
fun BookmarksSortMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    sortKey: BookmarksSortKey,
    sortOrder: ListSortOrder,
    onSortByTitle: () -> Unit,
    onSortByLastVisit: () -> Unit,
    onSortByDateAdded: () -> Unit,
    onSortAscending: () -> Unit,
    onSortDescending: () -> Unit
) {
    val colors = LocalAlexToolColors.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = PopupShape,
        containerColor = colors.popupBackground,
        border = BorderStroke(1.dp, colors.popupStroke)
    ) {
        ListMenuItem(androidx.compose.material.icons.Icons.Filled.Abc, stringResource(R.string.history_sort_by_title), sortKey == BookmarksSortKey.TITLE) {
            onDismiss(); onSortByTitle()
        }
        ListMenuItem(androidx.compose.material.icons.Icons.Filled.DateRange, stringResource(R.string.history_sort_by_last_visit), sortKey == BookmarksSortKey.LAST_VISIT) {
            onDismiss(); onSortByLastVisit()
        }
        ListMenuItem(androidx.compose.material.icons.Icons.Filled.BookmarkBorder, stringResource(R.string.bookmarks_sort_by_date_added), sortKey == BookmarksSortKey.DATE_ADDED) {
            onDismiss(); onSortByDateAdded()
        }
        HorizontalDivider(color = colors.divider)
        ListMenuItem(androidx.compose.material.icons.Icons.Filled.ArrowUpward, stringResource(R.string.history_sort_ascending), sortOrder == ListSortOrder.ASCENDING) {
            onDismiss(); onSortAscending()
        }
        ListMenuItem(androidx.compose.material.icons.Icons.Filled.ArrowDownward, stringResource(R.string.history_sort_descending), sortOrder == ListSortOrder.DESCENDING) {
            onDismiss(); onSortDescending()
        }
    }
}
