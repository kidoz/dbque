package su.kidoz.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import su.kidoz.feature.settings.*

@Composable
fun SettingsDialog(
    state: SettingsState,
    onEvent: (SettingsEvent) -> Unit,
) {
    if (!state.dialogVisible) return

    Dialog(
        onDismissRequest = { onEvent(SettingsEvent.HideDialog) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.width(700.dp).height(500.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
        ) {
            Column {
                // Header
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("Settings", style = MaterialTheme.typography.titleLarge)
                    }
                }

                Row(modifier = Modifier.weight(1f)) {
                    // Sidebar with tabs
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.width(180.dp).fillMaxHeight(),
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            SettingsTab.entries.forEach { tab ->
                                SettingsTabItem(
                                    tab = tab,
                                    isSelected = state.activeTab == tab,
                                    onClick = { onEvent(SettingsEvent.SelectTab(tab)) },
                                )
                            }
                        }
                    }

                    // Content
                    Column(
                        modifier = Modifier.weight(1f).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        when (state.activeTab) {
                            SettingsTab.EDITOR -> EditorSettings(state, onEvent)
                            SettingsTab.RESULTS -> ResultsSettings(state, onEvent)
                            SettingsTab.CONNECTION -> ConnectionSettings(state, onEvent)
                            SettingsTab.APPEARANCE -> AppearanceSettings(state, onEvent)
                        }
                    }
                }

                // Footer
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = { onEvent(SettingsEvent.ResetToDefaults) }) {
                        Text("Reset to Defaults")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { onEvent(SettingsEvent.HideDialog) }) {
                        Text("Cancel")
                    }
                    Button(onClick = { onEvent(SettingsEvent.SaveSettings) }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTabItem(
    tab: SettingsTab,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val icon: ImageVector =
        when (tab) {
            SettingsTab.EDITOR -> Icons.Default.Code
            SettingsTab.RESULTS -> Icons.Default.TableChart
            SettingsTab.CONNECTION -> Icons.Default.Storage
            SettingsTab.APPEARANCE -> Icons.Default.Palette
        }

    val label =
        when (tab) {
            SettingsTab.EDITOR -> "Editor"
            SettingsTab.RESULTS -> "Results"
            SettingsTab.CONNECTION -> "Connection"
            SettingsTab.APPEARANCE -> "Appearance"
        }

    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EditorSettings(
    state: SettingsState,
    onEvent: (SettingsEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Editor Settings", style = MaterialTheme.typography.titleMedium)

        // Font Size
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Font Size")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = { if (state.fontSize > 8) onEvent(SettingsEvent.UpdateFontSize(state.fontSize - 1)) },
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease")
                }
                Text("${state.fontSize}px", style = MaterialTheme.typography.bodyMedium)
                IconButton(
                    onClick = { if (state.fontSize < 32) onEvent(SettingsEvent.UpdateFontSize(state.fontSize + 1)) },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase")
                }
            }
        }

        // Tab Size
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Tab Size")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(2, 4, 8).forEach { size ->
                    FilterChip(
                        selected = state.tabSize == size,
                        onClick = { onEvent(SettingsEvent.UpdateTabSize(size)) },
                        label = { Text("$size") },
                    )
                }
            }
        }

        HorizontalDivider()

        // Toggle options
        SettingsToggle(
            label = "Word Wrap",
            description = "Wrap long lines to fit in the editor",
            checked = state.wordWrap,
            onCheckedChange = { onEvent(SettingsEvent.UpdateWordWrap(it)) },
        )

        SettingsToggle(
            label = "Line Numbers",
            description = "Show line numbers in the gutter",
            checked = state.lineNumbers,
            onCheckedChange = { onEvent(SettingsEvent.UpdateLineNumbers(it)) },
        )

        SettingsToggle(
            label = "Highlight Current Line",
            description = "Highlight the current line in the editor",
            checked = state.highlightCurrentLine,
            onCheckedChange = { onEvent(SettingsEvent.UpdateHighlightCurrentLine(it)) },
        )

        SettingsToggle(
            label = "Auto-Complete",
            description = "Show auto-complete suggestions while typing",
            checked = state.autoComplete,
            onCheckedChange = { onEvent(SettingsEvent.UpdateAutoComplete(it)) },
        )
    }
}

@Composable
private fun ResultsSettings(
    state: SettingsState,
    onEvent: (SettingsEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Results Settings", style = MaterialTheme.typography.titleMedium)

        // Max Result Rows
        OutlinedTextField(
            value = state.maxResultRows.toString(),
            onValueChange = {
                it.filter { c -> c.isDigit() }.toIntOrNull()?.let { rows ->
                    if (rows in 1..100000) onEvent(SettingsEvent.UpdateMaxResultRows(rows))
                }
            },
            label = { Text("Maximum Result Rows") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("Maximum number of rows to fetch (1-100000)") },
        )

        // NULL Display Text
        OutlinedTextField(
            value = state.nullDisplayText,
            onValueChange = { onEvent(SettingsEvent.UpdateNullDisplayText(it)) },
            label = { Text("NULL Display Text") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("Text to display for NULL values") },
        )

        // Date/Time Format
        OutlinedTextField(
            value = state.dateTimeFormat,
            onValueChange = { onEvent(SettingsEvent.UpdateDateTimeFormat(it)) },
            label = { Text("Date/Time Format") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("Format pattern for date/time values (e.g., yyyy-MM-dd HH:mm:ss)") },
        )
    }
}

@Composable
private fun ConnectionSettings(
    state: SettingsState,
    onEvent: (SettingsEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Connection Settings", style = MaterialTheme.typography.titleMedium)

        // Connection Timeout
        OutlinedTextField(
            value = state.connectionTimeout.toString(),
            onValueChange = {
                it.filter { c -> c.isDigit() }.toIntOrNull()?.let { timeout ->
                    if (timeout in 1..300) onEvent(SettingsEvent.UpdateConnectionTimeout(timeout))
                }
            },
            label = { Text("Connection Timeout (seconds)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("Time to wait for a connection to be established (1-300)") },
        )

        // Query Timeout
        OutlinedTextField(
            value = state.queryTimeout.toString(),
            onValueChange = {
                it.filter { c -> c.isDigit() }.toIntOrNull()?.let { timeout ->
                    if (timeout in 1..3600) onEvent(SettingsEvent.UpdateQueryTimeout(timeout))
                }
            },
            label = { Text("Query Timeout (seconds)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("Time to wait for a query to complete (1-3600)") },
        )
    }
}

@Composable
private fun AppearanceSettings(
    state: SettingsState,
    onEvent: (SettingsEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Appearance Settings", style = MaterialTheme.typography.titleMedium)

        // Theme
        Text("Theme", style = MaterialTheme.typography.bodyMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeMode.entries.forEach { theme ->
                FilterChip(
                    selected = state.theme == theme,
                    onClick = { onEvent(SettingsEvent.UpdateTheme(theme)) },
                    label = { Text(theme.displayName) },
                    leadingIcon = {
                        if (state.theme == theme) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
