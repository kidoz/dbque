package su.kidoz.feature.editor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import su.kidoz.feature.editor.EditorEvent
import su.kidoz.feature.editor.EditorState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTabs(
    state: EditorState,
    onEvent: (EditorEvent) -> Unit,
    onSaveQuery: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.tabs.forEach { tab ->
                TabItem(
                    title = tab.title,
                    isActive = tab.id == state.activeTabId,
                    isModified = tab.isModified,
                    onClick = { onEvent(EditorEvent.SelectTab(tab.id)) },
                    onClose = { onEvent(EditorEvent.CloseTab(tab.id)) },
                    canClose = state.tabs.size > 1,
                )
            }

            // New tab button
            IconButton(
                onClick = { onEvent(EditorEvent.NewTab) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "New Tab",
                    modifier = Modifier.size(16.dp),
                )
            }

            // Save Query button
            if (onSaveQuery != null) {
                Spacer(Modifier.weight(1f))
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text("Save current query") } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(
                        onClick = {
                            state.activeTab?.content?.let { query ->
                                if (query.isNotBlank()) {
                                    onSaveQuery(query)
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp),
                        enabled = state.activeTab?.content?.isNotBlank() == true,
                    ) {
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = "Save Query",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    title: String,
    isActive: Boolean,
    isModified: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    canClose: Boolean,
) {
    Surface(
        color =
            if (isActive) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        modifier =
            Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (isModified) "$title *" else title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (canClose) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(16.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close Tab",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
