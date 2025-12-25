package su.kidoz.feature.results.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import su.kidoz.feature.results.ExportFormat
import su.kidoz.feature.results.ResultsEvent
import su.kidoz.feature.results.ResultsState

@Composable
fun ResultsPanel(
    state: ResultsState,
    onEvent: (ResultsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Toolbar
        ResultsToolbar(state = state, onEvent = onEvent)

        // Result tabs if multiple results
        if (state.results.size > 1) {
            ResultTabs(state = state, onEvent = onEvent)
        }

        // Data grid
        DataGrid(
            state = state,
            onEvent = onEvent,
            modifier = Modifier.weight(1f),
        )
    }

    // Export dialog
    if (state.exportDialogVisible) {
        ExportDialog(
            onExport = { format, selectedOnly ->
                onEvent(ResultsEvent.Export(format, selectedOnly))
            },
            onDismiss = { onEvent(ResultsEvent.HideExportDialog) },
            hasSelection = state.selectedRows.isNotEmpty(),
        )
    }
}

@Composable
private fun ResultsToolbar(
    state: ResultsState,
    onEvent: (ResultsEvent) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Filter
            OutlinedTextField(
                value = state.filterText,
                onValueChange = { onEvent(ResultsEvent.SetFilter(it)) },
                placeholder = { Text("Filter...") },
                modifier = Modifier.width(200.dp).height(36.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                trailingIcon = {
                    if (state.filterText.isNotEmpty()) {
                        IconButton(
                            onClick = { onEvent(ResultsEvent.ClearFilter) },
                            modifier = Modifier.size(16.dp),
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear filter",
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                },
            )

            Spacer(Modifier.weight(1f))

            // Copy buttons
            TooltipIconButton(
                icon = Icons.Default.ContentCopy,
                tooltip = "Copy selected rows",
                onClick = { onEvent(ResultsEvent.CopySelectedRows) },
                enabled = state.selectedRows.isNotEmpty(),
            )

            // Export button
            TooltipIconButton(
                icon = Icons.Default.FileDownload,
                tooltip = "Export results",
                onClick = { onEvent(ResultsEvent.ShowExportDialog) },
                enabled = state.results.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun ResultTabs(
    state: ResultsState,
    onEvent: (ResultsEvent) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        state.results.forEachIndexed { index, result ->
            val isActive = index == state.activeResultIndex
            Surface(
                color =
                    if (isActive) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                modifier = Modifier.clickable { onEvent(ResultsEvent.SelectResultTab(index)) },
            ) {
                Text(
                    text = "Result ${index + 1} (${result.rowCount} rows)",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TooltipIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tooltip: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                icon,
                contentDescription = tooltip,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ExportDialog(
    onExport: (ExportFormat, Boolean) -> Unit,
    onDismiss: () -> Unit,
    hasSelection: Boolean,
) {
    var selectedFormat by remember { mutableStateOf(ExportFormat.CSV) }
    var exportSelectedOnly by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Results") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Select export format:", style = MaterialTheme.typography.bodyMedium)

                ExportFormat.entries.forEach { format ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { selectedFormat = format }
                                .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedFormat == format,
                            onClick = { selectedFormat = format },
                        )
                        Text(
                            format.displayName,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                if (hasSelection) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = exportSelectedOnly,
                            onCheckedChange = { exportSelectedOnly = it },
                        )
                        Text(
                            "Export selected rows only",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onExport(selectedFormat, exportSelectedOnly) }) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
