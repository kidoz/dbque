package su.kidoz.feature.connection.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import su.kidoz.feature.connection.ConnectionDialogState
import su.kidoz.feature.connection.ConnectionEvent
import javax.swing.JFileChooser

@Composable
fun SshConfigSection(
    state: ConnectionDialogState,
    onEvent: (ConnectionEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPassword by remember { mutableStateOf(false) }
    var showPassphrase by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Enable SSH toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = state.sshEnabled,
                onCheckedChange = { onEvent(ConnectionEvent.UpdateSshEnabled(it)) },
            )
            Text("Use SSH Tunnel", style = MaterialTheme.typography.bodyMedium)
        }

        if (state.sshEnabled) {
            // SSH Host and Port
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.sshHost,
                    onValueChange = { onEvent(ConnectionEvent.UpdateSshHost(it)) },
                    label = { Text("SSH Host") },
                    modifier = Modifier.weight(2f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.sshPort,
                    onValueChange = { onEvent(ConnectionEvent.UpdateSshPort(it.filter { c -> c.isDigit() })) },
                    label = { Text("SSH Port") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            // SSH Username
            OutlinedTextField(
                value = state.sshUsername,
                onValueChange = { onEvent(ConnectionEvent.UpdateSshUsername(it)) },
                label = { Text("SSH Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // Authentication method toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = state.sshUseKeyAuth,
                    onCheckedChange = { onEvent(ConnectionEvent.UpdateSshUseKeyAuth(it)) },
                )
                Text("Use Private Key Authentication", style = MaterialTheme.typography.bodySmall)
            }

            if (state.sshUseKeyAuth) {
                // Private key path
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = state.sshPrivateKeyPath,
                        onValueChange = { onEvent(ConnectionEvent.UpdateSshPrivateKeyPath(it)) },
                        label = { Text("Private Key Path") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    IconButton(onClick = {
                        val chooser =
                            JFileChooser().apply {
                                fileSelectionMode = JFileChooser.FILES_ONLY
                                dialogTitle = "Select Private Key File"
                                currentDirectory = java.io.File(System.getProperty("user.home"), ".ssh")
                            }
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            onEvent(ConnectionEvent.UpdateSshPrivateKeyPath(chooser.selectedFile.absolutePath))
                        }
                    }) {
                        Icon(Icons.Default.FolderOpen, "Browse")
                    }
                }

                // Passphrase
                OutlinedTextField(
                    value = state.sshPassphrase,
                    onValueChange = { onEvent(ConnectionEvent.UpdateSshPassphrase(it)) },
                    label = { Text("Key Passphrase (if any)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassphrase = !showPassphrase }) {
                            Icon(
                                if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                "Toggle passphrase visibility",
                            )
                        }
                    },
                )
            } else {
                // SSH Password
                OutlinedTextField(
                    value = state.sshPassword,
                    onValueChange = { onEvent(ConnectionEvent.UpdateSshPassword(it)) },
                    label = { Text("SSH Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                "Toggle password visibility",
                            )
                        }
                    },
                )
            }
        }
    }
}
