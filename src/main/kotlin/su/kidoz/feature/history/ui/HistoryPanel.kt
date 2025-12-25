package su.kidoz.feature.history.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import su.kidoz.core.model.QueryHistoryEntry
import su.kidoz.feature.history.HistoryEvent
import su.kidoz.feature.history.HistoryState
import su.kidoz.ui.theme.DBQueTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryPanel(
    state: HistoryState,
    onEvent: (HistoryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Header
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Query History",
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { onEvent(HistoryEvent.Refresh) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = { onEvent(HistoryEvent.ClearHistory) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Clear History",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // Search
        OutlinedTextField(
            value = state.searchText,
            onValueChange = { onEvent(HistoryEvent.Search(it)) },
            placeholder = { Text("Search history...") },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            singleLine = true,
            trailingIcon = {
                if (state.searchText.isNotEmpty()) {
                    IconButton(onClick = { onEvent(HistoryEvent.ClearSearch) }) {
                        Icon(Icons.Default.Clear, "Clear search")
                    }
                }
            },
        )

        // History list
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (state.filteredEntries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (state.searchText.isNotEmpty()) {
                        "No matching queries found"
                    } else {
                        "No query history"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.filteredEntries, key = { it.id }) { entry ->
                    HistoryItem(
                        entry = entry,
                        isSelected = state.selectedEntryId == entry.id,
                        onEvent = onEvent,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    entry: QueryHistoryEntry,
    isSelected: Boolean,
    onEvent: (HistoryEvent) -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val contextMenuItems =
        remember(entry) {
            listOf(
                ContextMenuItem("Use Query") { onEvent(HistoryEvent.UseQuery(entry.query)) },
                ContextMenuItem("Copy Query") { onEvent(HistoryEvent.CopyQuery(entry.query)) },
                ContextMenuItem("Delete") { onEvent(HistoryEvent.DeleteEntry(entry.id)) },
            )
        }

    ContextMenuArea(items = { contextMenuItems }) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onEvent(HistoryEvent.UseQuery(entry.query)) }
                    .then(
                        if (isSelected) {
                            Modifier.background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            )
                        } else {
                            Modifier
                        },
                    ),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // Status icon
                Icon(
                    imageVector = if (entry.successful) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint =
                        if (entry.successful) {
                            DBQueTheme.extendedColors.success
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                )

                // Query info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.formattedQuery,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = dateFormat.format(Date(entry.executedAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (entry.successful) {
                            Text(
                                text = "${entry.rowCount} rows",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${entry.executionTimeMs}ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = entry.errorMessage?.take(50) ?: "Error",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
