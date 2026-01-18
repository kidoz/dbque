package su.kidoz.feature.results.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import su.kidoz.core.model.QueryResult
import su.kidoz.feature.results.CellEdit

data class EditingCell(
    val rowIndex: Int,
    val columnIndex: Int,
    val originalValue: Any?,
    val currentValue: String,
)

@Composable
fun EditableDataGrid(
    result: QueryResult,
    editedCells: Map<Pair<Int, Int>, CellEdit>,
    editingCell: EditingCell?,
    selectedRows: Set<Int>,
    selectedColumn: Int?,
    sortColumn: Int?,
    sortAscending: Boolean,
    onStartEdit: (rowIndex: Int, columnIndex: Int, value: Any?) -> Unit,
    onUpdateEdit: (value: String) -> Unit,
    onCommitEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSelectRow: (rowIndex: Int, addToSelection: Boolean) -> Unit,
    @Suppress("UNUSED_PARAMETER") onSelectColumn: (columnIndex: Int) -> Unit,
    onSortColumn: (columnIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                Box(
                    modifier =
                        Modifier
                            .width(150.dp)
                            .clickable { onSortColumn(index) }
                            .background(
                                if (selectedColumn == index) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                } else {
                                    Color.Transparent
                                },
                            ).padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                column.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                column.typeName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (sortColumn == index) {
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
        }

        HorizontalDivider()

        // Data rows
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(result.rows, key = { index, _ -> index }) { rowIndex, row ->
                val isSelected = selectedRows.contains(rowIndex)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(horizontalScroll)
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    rowIndex % 2 == 0 -> MaterialTheme.colorScheme.surface
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                },
                            ).clickable { onSelectRow(rowIndex, false) },
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
                            (rowIndex + 1).toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Data cells
                    row.forEachIndexed { colIndex, value ->
                        val cellKey = rowIndex to colIndex
                        val isEditing = editingCell?.rowIndex == rowIndex && editingCell.columnIndex == colIndex
                        val hasEdit = editedCells.containsKey(cellKey)

                        EditableCell(
                            value = value,
                            editedValue = editedCells[cellKey]?.newValue,
                            isEditing = isEditing,
                            editingValue = if (isEditing) editingCell.currentValue else "",
                            hasEdit = hasEdit,
                            isSelected = selectedColumn == colIndex,
                            onStartEdit = { onStartEdit(rowIndex, colIndex, value) },
                            onUpdateEdit = onUpdateEdit,
                            onCommitEdit = onCommitEdit,
                            onCancelEdit = onCancelEdit,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditableCell(
    value: Any?,
    editedValue: Any?,
    isEditing: Boolean,
    editingValue: String,
    hasEdit: Boolean,
    isSelected: Boolean,
    onStartEdit: () -> Unit,
    onUpdateEdit: (String) -> Unit,
    onCommitEdit: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val displayValue = editedValue ?: value
    val isNullValue = displayValue == null

    Box(
        modifier =
            Modifier
                .width(150.dp)
                .background(
                    when {
                        hasEdit -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        else -> Color.Transparent
                    },
                ).clickable(enabled = !isEditing) { onStartEdit() }
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        if (isEditing) {
            BasicTextField(
                value = editingValue,
                onValueChange = onUpdateEdit,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            when {
                                keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter -> {
                                    onCommitEdit()
                                    true
                                }
                                keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape -> {
                                    onCancelEdit()
                                    true
                                }
                                else -> false
                            }
                        },
                textStyle =
                    MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box(
                        modifier =
                            Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                                .padding(4.dp),
                    ) {
                        innerTextField()
                    }
                },
            )

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        } else {
            Text(
                text =
                    when {
                        isNullValue -> "NULL"
                        displayValue is ByteArray -> "[BINARY]"
                        else -> displayValue.toString()
                    },
                style = MaterialTheme.typography.bodySmall,
                color =
                    when {
                        hasEdit -> MaterialTheme.colorScheme.tertiary
                        isNullValue -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
