package su.kidoz.feature.results.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import su.kidoz.feature.results.ResultsEvent
import su.kidoz.feature.results.ResultsState

@Composable
fun DataGrid(
    state: ResultsState,
    onEvent: (ResultsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val result = state.activeResult
    if (result == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No results to display",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    if (!result.isResultSet) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "${result.affectedRows} row(s) affected",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Execution time: ${result.executionTimeMs}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val rows = state.filteredRows
    val horizontalScroll = rememberScrollState()
    val listState = rememberLazyListState()

    Column(modifier = modifier) {
        // Header row
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScroll)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            // Row number column
            Box(
                modifier =
                    Modifier
                        .width(50.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "#",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Column headers
            result.columns.forEachIndexed { index, column ->
                HeaderCell(
                    label = column.label,
                    typeName = column.typeName,
                    isSorted = state.sortColumn == index,
                    sortAscending = state.sortAscending,
                    isSelected = state.selectedColumn == index,
                    onClick = { onEvent(ResultsEvent.SortByColumn(index)) },
                    onSelect = { onEvent(ResultsEvent.SelectColumn(index)) },
                )
            }
        }

        HorizontalDivider()

        // Data rows
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(rows, key = { index, _ -> index }) { index, row ->
                val isSelected = state.selectedRows.contains(index)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(horizontalScroll)
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    index % 2 == 0 -> MaterialTheme.colorScheme.surface
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                },
                            ).clickable {
                                onEvent(ResultsEvent.SelectRow(index, false))
                            },
                ) {
                    // Row number
                    Box(
                        modifier =
                            Modifier
                                .width(50.dp)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            (index + 1).toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Data cells
                    row.forEachIndexed { colIndex, value ->
                        DataCell(
                            value = value,
                            isNullValue = value == null,
                            isSelected = state.selectedColumn == colIndex,
                        )
                    }
                }
            }
        }

        // Status bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "${rows.size} rows",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.selectedRows.isNotEmpty()) {
                    Text(
                        "${state.selectedRows.size} selected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    "${result.executionTimeMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HeaderCell(
    label: String,
    typeName: String,
    isSorted: Boolean,
    sortAscending: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onSelect: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .width(150.dp)
                .clickable(onClick = onClick)
                .background(
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        Color.Transparent
                    },
                ).padding(horizontal = 8.dp, vertical = 4.dp)
                .pointerHoverIcon(PointerIcon.Hand),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    typeName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isSorted) {
                Icon(
                    if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun DataCell(
    value: Any?,
    isNullValue: Boolean,
    isSelected: Boolean,
) {
    val displayValue =
        when {
            isNullValue -> "NULL"
            value is ByteArray -> "[BINARY]"
            else -> value.toString()
        }

    Box(
        modifier =
            Modifier
                .width(150.dp)
                .background(
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    } else {
                        Color.Transparent
                    },
                ).padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            displayValue,
            style = MaterialTheme.typography.bodySmall,
            color =
                if (isNullValue) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
