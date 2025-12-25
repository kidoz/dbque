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
import su.kidoz.database.ssh.SshConfig
import javax.swing.JFileChooser

@Composable
fun SshConfigSection(
    sshConfig: SshConfig,
    onConfigChange: (SshConfig) -> Unit,
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
                checked = sshConfig.enabled,
                onCheckedChange = { onConfigChange(sshConfig.copy(enabled = it)) },
            )
            Text("Use SSH Tunnel", style = MaterialTheme.typography.bodyMedium)
        }

        if (sshConfig.enabled) {
            // SSH Host and Port
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = sshConfig.host,
                    onValueChange = { onConfigChange(sshConfig.copy(host = it)) },
                    label = { Text("SSH Host") },
                    modifier = Modifier.weight(2f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = sshConfig.port.toString(),
                    onValueChange = {
                        val port = it.filter { c -> c.isDigit() }.toIntOrNull() ?: 22
                        onConfigChange(sshConfig.copy(port = port))
                    },
                    label = { Text("SSH Port") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            // SSH Username
            OutlinedTextField(
                value = sshConfig.username,
                onValueChange = { onConfigChange(sshConfig.copy(username = it)) },
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
                    checked = sshConfig.useKeyAuth,
                    onCheckedChange = { onConfigChange(sshConfig.copy(useKeyAuth = it)) },
                )
                Text("Use Private Key Authentication", style = MaterialTheme.typography.bodySmall)
            }

            if (sshConfig.useKeyAuth) {
                // Private key path
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = sshConfig.privateKeyPath,
                        onValueChange = { onConfigChange(sshConfig.copy(privateKeyPath = it)) },
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
                            onConfigChange(sshConfig.copy(privateKeyPath = chooser.selectedFile.absolutePath))
                        }
                    }) {
                        Icon(Icons.Default.FolderOpen, "Browse")
                    }
                }

                // Passphrase
                OutlinedTextField(
                    value = sshConfig.passphrase,
                    onValueChange = { onConfigChange(sshConfig.copy(passphrase = it)) },
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
                    value = sshConfig.password,
                    onValueChange = { onConfigChange(sshConfig.copy(password = it)) },
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
