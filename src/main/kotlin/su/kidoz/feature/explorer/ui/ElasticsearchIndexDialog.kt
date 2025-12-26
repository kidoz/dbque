package su.kidoz.feature.explorer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import su.kidoz.feature.explorer.ElasticsearchIndexDialogState
import su.kidoz.feature.explorer.ExplorerEvent
import su.kidoz.feature.explorer.IndexDialogMode

@Composable
fun ElasticsearchIndexDialog(
    state: ElasticsearchIndexDialogState,
    onEvent: (ExplorerEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!state.isProcessing) {
                onEvent(ExplorerEvent.HideIndexDialog)
            }
        },
        title = {
            Text(state.title)
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .width(600.dp)
                        .height(500.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Index name field
                OutlinedTextField(
                    value = state.indexName,
                    onValueChange = { onEvent(ExplorerEvent.UpdateIndexName(it.lowercase())) },
                    label = { Text("Index Name") },
                    enabled = state.mode == IndexDialogMode.CREATE && !state.isProcessing,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = state.indexName.isNotBlank() && !state.indexName.matches(Regex("[a-z0-9][a-z0-9_.-]*")),
                    supportingText = {
                        if (state.mode == IndexDialogMode.CREATE) {
                            Text(
                                "Lowercase letters, numbers, hyphens, underscores, and dots only",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                )

                // Label for JSON editor
                Text(
                    text =
                        when (state.mode) {
                            IndexDialogMode.CREATE -> "Index Definition (JSON)"
                            IndexDialogMode.EDIT_SETTINGS -> "Settings (JSON)"
                            IndexDialogMode.EDIT_MAPPINGS -> "Mappings (JSON)"
                        },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // JSON editor with scroll
                Surface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 1.dp,
                ) {
                    Box(modifier = Modifier.padding(8.dp)) {
                        if (state.isProcessing && state.definitionJson == "Loading...") {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            BasicTextField(
                                value = state.definitionJson,
                                onValueChange = { onEvent(ExplorerEvent.UpdateIndexDefinition(it)) },
                                enabled = !state.isProcessing,
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                textStyle =
                                    TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                            )
                        }
                    }
                }

                // Error display
                state.error?.let { error ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }

                // Help text
                if (state.mode != IndexDialogMode.CREATE) {
                    Text(
                        text =
                            when (state.mode) {
                                IndexDialogMode.EDIT_SETTINGS ->
                                    "Note: Only dynamic settings (replicas, refresh_interval, etc.) can be modified."
                                IndexDialogMode.EDIT_MAPPINGS ->
                                    "Note: You can add new fields but cannot modify existing field types."
                                else -> ""
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onEvent(ExplorerEvent.SaveIndex) },
                enabled = state.isValid && !state.isProcessing,
            ) {
                if (state.isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    when (state.mode) {
                        IndexDialogMode.CREATE -> "Create"
                        else -> "Save"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(ExplorerEvent.HideIndexDialog) },
                enabled = !state.isProcessing,
            ) {
                Text("Cancel")
            }
        },
    )
}
