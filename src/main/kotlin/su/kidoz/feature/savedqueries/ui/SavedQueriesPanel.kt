package su.kidoz.feature.savedqueries.ui

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import su.kidoz.feature.savedqueries.*

@Composable
fun SavedQueriesPanel(
    state: SavedQueryState,
    onEvent: (SavedQueryEvent) -> Unit,
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
                    "Saved Queries",
                    style = MaterialTheme.typography.titleSmall,
                )
                IconButton(
                    onClick = { onEvent(SavedQueryEvent.ShowNewQueryDialog) },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "New Query",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // Search
        OutlinedTextField(
            value = state.searchText,
            onValueChange = { onEvent(SavedQueryEvent.Search(it)) },
            placeholder = { Text("Search saved queries...") },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            singleLine = true,
            trailingIcon = {
                if (state.searchText.isNotEmpty()) {
                    IconButton(onClick = { onEvent(SavedQueryEvent.Search("")) }) {
                        Icon(Icons.Default.Clear, "Clear search")
                    }
                }
            },
        )

        // Query list
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (state.filteredQueries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        if (state.searchText.isNotEmpty()) {
                            "No matching queries"
                        } else {
                            "No saved queries"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Queries without folder
                val unfolderedQueries = state.queriesByFolder[null] ?: emptyList()
                items(unfolderedQueries, key = { it.id }) { query ->
                    SavedQueryItem(
                        query = query,
                        isSelected = state.selectedQueryId == query.id,
                        onEvent = onEvent,
                    )
                }

                // Folders
                state.folders.forEach { folder ->
                    val folderQueries = state.queriesByFolder[folder] ?: emptyList()
                    if (folderQueries.isNotEmpty()) {
                        item(key = "folder:$folder") {
                            FolderItem(
                                folder = folder,
                                isExpanded = state.expandedFolders.contains(folder),
                                queryCount = folderQueries.size,
                                onToggle = { onEvent(SavedQueryEvent.ToggleFolder(folder)) },
                            )
                        }

                        if (state.expandedFolders.contains(folder)) {
                            items(folderQueries, key = { it.id }) { query ->
                                SavedQueryItem(
                                    query = query,
                                    isSelected = state.selectedQueryId == query.id,
                                    onEvent = onEvent,
                                    indent = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog
    state.dialogState?.let { dialogState ->
        SavedQueryDialog(
            state = dialogState,
            folders = state.folders,
            onEvent = onEvent,
        )
    }
}

@Composable
private fun FolderItem(
    folder: String,
    isExpanded: Boolean,
    queryCount: Int,
    onToggle: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = folder,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = queryCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SavedQueryItem(
    query: SavedQuery,
    isSelected: Boolean,
    onEvent: (SavedQueryEvent) -> Unit,
    indent: Boolean = false,
) {
    val contextMenuItems =
        remember(query.id) {
            listOf(
                ContextMenuItem("Use Query") { onEvent(SavedQueryEvent.UseQuery(query.id)) },
                ContextMenuItem("Copy Query") { onEvent(SavedQueryEvent.CopyQuery(query.id)) },
                ContextMenuItem("Edit") { onEvent(SavedQueryEvent.ShowEditQueryDialog(query.id)) },
                ContextMenuItem("Delete") { onEvent(SavedQueryEvent.DeleteQuery(query.id)) },
            )
        }

    ContextMenuArea(items = { contextMenuItems }) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onEvent(SavedQueryEvent.UseQuery(query.id)) }
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
                modifier =
                    Modifier
                        .padding(
                            start = if (indent) 32.dp else 12.dp,
                            end = 12.dp,
                            top = 8.dp,
                            bottom = 8.dp,
                        ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Default.Code,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = query.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = query.query.take(100).replace("\n", " "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedQueryDialog(
    state: SavedQueryDialogState,
    folders: List<String>,
    onEvent: (SavedQueryEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(SavedQueryEvent.HideDialog) },
        title = {
            Text(if (state.isEditing) "Edit Query" else "Save Query")
        },
        text = {
            Column(
                modifier = Modifier.width(450.dp).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { onEvent(SavedQueryEvent.UpdateName(it)) },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                // Folder dropdown or text field
                var folderExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = folderExpanded,
                    onExpandedChange = { folderExpanded = it },
                ) {
                    OutlinedTextField(
                        value = state.folder,
                        onValueChange = { onEvent(SavedQueryEvent.UpdateFolder(it)) },
                        label = { Text("Folder (optional)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = folderExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
                    )
                    if (folders.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = folderExpanded,
                            onDismissRequest = { folderExpanded = false },
                        ) {
                            folders.forEach { folder ->
                                DropdownMenuItem(
                                    text = { Text(folder) },
                                    onClick = {
                                        onEvent(SavedQueryEvent.UpdateFolder(folder))
                                        folderExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = state.description,
                    onValueChange = { onEvent(SavedQueryEvent.UpdateDescription(it)) },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3,
                )

                OutlinedTextField(
                    value = state.query,
                    onValueChange = { onEvent(SavedQueryEvent.UpdateQuery(it)) },
                    label = { Text("SQL Query") },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    minLines = 5,
                )

                state.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onEvent(SavedQueryEvent.SaveQuery) },
                enabled = state.isValid,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(SavedQueryEvent.HideDialog) }) {
                Text("Cancel")
            }
        },
    )
}
