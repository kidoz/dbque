package su.kidoz.feature.connection.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import su.kidoz.core.model.DatabaseType
import su.kidoz.feature.connection.ConnectionDialogState
import su.kidoz.feature.connection.ConnectionEvent
import su.kidoz.feature.connection.TestResult
import su.kidoz.ui.theme.DBQueTheme
import javax.swing.JFileChooser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionDialog(
    state: ConnectionDialogState,
    onEvent: (ConnectionEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(ConnectionEvent.HideConnectionDialog) },
        title = {
            Text(if (state.isEditing) "Edit Connection" else "New Connection")
        },
        text = {
            Column(
                modifier = Modifier.width(450.dp).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Name
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { onEvent(ConnectionEvent.UpdateName(it)) },
                    label = { Text("Connection Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                // Database Type
                var typeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it },
                ) {
                    OutlinedTextField(
                        value = state.type.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Database Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false },
                    ) {
                        DatabaseType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    onEvent(ConnectionEvent.UpdateType(type))
                                    typeExpanded = false
                                },
                            )
                        }
                    }
                }

                // Fields based on database type
                when (state.type) {
                    DatabaseType.SQLITE, DatabaseType.H2 -> {
                        // Path field with file chooser
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = state.path,
                                onValueChange = { onEvent(ConnectionEvent.UpdatePath(it)) },
                                label = { Text("Database Path") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            IconButton(onClick = {
                                val chooser =
                                    JFileChooser().apply {
                                        fileSelectionMode = JFileChooser.FILES_ONLY
                                        dialogTitle = "Select Database File"
                                    }
                                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                    onEvent(ConnectionEvent.UpdatePath(chooser.selectedFile.absolutePath))
                                }
                            }) {
                                Icon(Icons.Default.FolderOpen, "Browse")
                            }
                        }
                    }
                    DatabaseType.MONGODB -> {
                        // MongoDB-specific fields
                        // Host and Port
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = state.host,
                                onValueChange = { onEvent(ConnectionEvent.UpdateHost(it)) },
                                label = { Text("Host") },
                                modifier = Modifier.weight(2f),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = state.port,
                                onValueChange = { onEvent(ConnectionEvent.UpdatePort(it.filter { c -> c.isDigit() })) },
                                label = { Text("Port") },
                                placeholder = { Text("27017") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                        }

                        // Database
                        OutlinedTextField(
                            value = state.database,
                            onValueChange = { onEvent(ConnectionEvent.UpdateDatabase(it)) },
                            label = { Text("Database") },
                            placeholder = { Text("admin") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )

                        // Username and Password
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = state.username,
                                onValueChange = { onEvent(ConnectionEvent.UpdateUsername(it)) },
                                label = { Text("Username") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = state.password,
                                onValueChange = { onEvent(ConnectionEvent.UpdatePassword(it)) },
                                label = { Text("Password") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                        }

                        // Auth Source (optional)
                        OutlinedTextField(
                            value = state.authSource,
                            onValueChange = { onEvent(ConnectionEvent.UpdateAuthSource(it)) },
                            label = { Text("Auth Database (optional)") },
                            placeholder = { Text("admin") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )

                        // SSL toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = state.useSsl,
                                onCheckedChange = { onEvent(ConnectionEvent.UpdateUseSsl(it)) },
                            )
                            Text("Use SSL/TLS")
                        }
                    }
                    DatabaseType.ELASTICSEARCH -> {
                        // Elasticsearch-specific fields
                        // Host and Port
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = state.host,
                                onValueChange = { onEvent(ConnectionEvent.UpdateHost(it)) },
                                label = { Text("Host") },
                                modifier = Modifier.weight(2f),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = state.port,
                                onValueChange = { onEvent(ConnectionEvent.UpdatePort(it.filter { c -> c.isDigit() })) },
                                label = { Text("Port") },
                                placeholder = { Text("9200") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                        }

                        // Username and Password (optional)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = state.username,
                                onValueChange = { onEvent(ConnectionEvent.UpdateUsername(it)) },
                                label = { Text("Username (optional)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = state.password,
                                onValueChange = { onEvent(ConnectionEvent.UpdatePassword(it)) },
                                label = { Text("Password (optional)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                        }

                        // SSL toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = state.useSsl,
                                onCheckedChange = { onEvent(ConnectionEvent.UpdateUseSsl(it)) },
                            )
                            Text("Use HTTPS")
                        }
                    }
                    else -> {
                        // Host and Port
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = state.host,
                                onValueChange = { onEvent(ConnectionEvent.UpdateHost(it)) },
                                label = { Text("Host") },
                                modifier = Modifier.weight(2f),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = state.port,
                                onValueChange = { onEvent(ConnectionEvent.UpdatePort(it.filter { c -> c.isDigit() })) },
                                label = { Text("Port") },
                                placeholder = { Text(state.type.defaultPort.toString()) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                        }

                        // Database
                        OutlinedTextField(
                            value = state.database,
                            onValueChange = { onEvent(ConnectionEvent.UpdateDatabase(it)) },
                            label = { Text("Database") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )

                        // Username and Password
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = state.username,
                                onValueChange = { onEvent(ConnectionEvent.UpdateUsername(it)) },
                                label = { Text("Username") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = state.password,
                                onValueChange = { onEvent(ConnectionEvent.UpdatePassword(it)) },
                                label = { Text("Password") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                        }
                    }
                }

                // Test Result
                state.testResult?.let { result ->
                    Surface(
                        color =
                            when (result) {
                                is TestResult.Success -> DBQueTheme.extendedColors.success.copy(alpha = 0.1f)
                                is TestResult.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                            },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector =
                                    when (result) {
                                        is TestResult.Success -> Icons.Default.CheckCircle
                                        is TestResult.Error -> Icons.Default.Error
                                    },
                                contentDescription = null,
                                tint =
                                    when (result) {
                                        is TestResult.Success -> DBQueTheme.extendedColors.success
                                        is TestResult.Error -> MaterialTheme.colorScheme.error
                                    },
                            )
                            Text(
                                text =
                                    when (result) {
                                        is TestResult.Success -> result.message
                                        is TestResult.Error -> result.message
                                    },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                // Error
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onEvent(ConnectionEvent.TestConnection) },
                    enabled = state.isValid && !state.isTesting,
                ) {
                    if (state.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Test")
                }
                Button(
                    onClick = { onEvent(ConnectionEvent.SaveConnection) },
                    enabled = state.isValid && !state.isTesting,
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ConnectionEvent.HideConnectionDialog) }) {
                Text("Cancel")
            }
        },
    )
}
