package su.kidoz.feature.connection.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import su.kidoz.core.model.ConnectionConfig
import su.kidoz.core.model.DatabaseType
import su.kidoz.feature.connection.ConnectionEvent
import su.kidoz.feature.connection.ConnectionState
import su.kidoz.ui.theme.DBQueTheme

@Composable
fun ConnectionList(
    state: ConnectionState,
    onEvent: (ConnectionEvent) -> Unit,
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
                    "Connections",
                    style = MaterialTheme.typography.titleSmall,
                )
                IconButton(
                    onClick = { onEvent(ConnectionEvent.ShowNewConnectionDialog) },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "New Connection",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // Connection list
        if (state.connections.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Storage,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        "No connections",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { onEvent(ConnectionEvent.ShowNewConnectionDialog) }) {
                        Text("Add Connection")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.connections, key = { it.id }) { connection ->
                    ConnectionItem(
                        connection = connection,
                        isConnected = state.activeConnectionIds.contains(connection.id),
                        isSelected = state.selectedConnectionId == connection.id,
                        onEvent = onEvent,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionItem(
    connection: ConnectionConfig,
    isConnected: Boolean,
    isSelected: Boolean,
    onEvent: (ConnectionEvent) -> Unit,
) {
    val contextMenuItems =
        remember(connection.id, isConnected) {
            buildList {
                if (isConnected) {
                    add(ContextMenuItem("Disconnect") { onEvent(ConnectionEvent.Disconnect(connection.id)) })
                } else {
                    add(ContextMenuItem("Connect") { onEvent(ConnectionEvent.Connect(connection.id)) })
                }
                add(ContextMenuItem("Edit") { onEvent(ConnectionEvent.ShowEditConnectionDialog(connection.id)) })
                add(ContextMenuItem("Duplicate") { onEvent(ConnectionEvent.DuplicateConnection(connection.id)) })
                add(ContextMenuItem("Delete") { onEvent(ConnectionEvent.DeleteConnection(connection.id)) })
            }
        }

    ContextMenuArea(items = { contextMenuItems }) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isConnected) {
                            onEvent(ConnectionEvent.SelectConnection(connection.id))
                        } else {
                            onEvent(ConnectionEvent.Connect(connection.id))
                        }
                    }.then(
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Database icon
                Icon(
                    imageVector = getDatabaseIcon(connection.type),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint =
                        if (isConnected) {
                            DBQueTheme.extendedColors.success
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )

                // Connection info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = connection.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = getConnectionSubtitle(connection),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Status indicator
                if (isConnected) {
                    Icon(
                        Icons.Default.Circle,
                        contentDescription = "Connected",
                        modifier = Modifier.size(8.dp),
                        tint = DBQueTheme.extendedColors.success,
                    )
                }
            }
        }
    }
}

private fun getDatabaseIcon(type: DatabaseType) =
    when (type) {
        DatabaseType.POSTGRESQL -> Icons.Default.Storage
        DatabaseType.MYSQL -> Icons.Default.Storage
        DatabaseType.SQLITE -> Icons.AutoMirrored.Filled.InsertDriveFile
        DatabaseType.H2 -> Icons.Default.Memory
        DatabaseType.MONGODB -> Icons.Default.Cloud
        DatabaseType.ELASTICSEARCH -> Icons.Default.Search
    }

private fun getConnectionSubtitle(connection: ConnectionConfig): String =
    when (connection.type) {
        DatabaseType.SQLITE, DatabaseType.H2 -> connection.path
        DatabaseType.ELASTICSEARCH -> "${connection.host}:${connection.effectivePort}"
        else -> "${connection.host}:${connection.effectivePort}/${connection.database}"
    }
