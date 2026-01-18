package su.kidoz.feature.connection

import su.kidoz.core.model.ConnectionConfig
import su.kidoz.core.model.DatabaseType
import su.kidoz.core.model.SshConfig
import su.kidoz.mvi.UiState

data class ConnectionState(
    val connections: List<ConnectionConfig> = emptyList(),
    val activeConnectionIds: Set<String> = emptySet(),
    val selectedConnectionId: String? = null,
    val isLoading: Boolean = false,
    val dialogState: ConnectionDialogState? = null,
) : UiState

data class ConnectionDialogState(
    val isEditing: Boolean = false,
    val connectionId: String? = null,
    val name: String = "",
    val type: DatabaseType = DatabaseType.POSTGRESQL,
    val host: String = "localhost",
    val port: String = "",
    val database: String = "",
    val username: String = "",
    val password: String = "",
    val path: String = "",
    // MongoDB-specific fields
    val authSource: String = "",
    val useSsl: Boolean = false,
    // SSH Tunnel fields
    val sshEnabled: Boolean = false,
    val sshHost: String = "",
    val sshPort: String = "22",
    val sshUsername: String = "",
    val sshPassword: String = "",
    val sshPrivateKeyPath: String = "",
    val sshPassphrase: String = "",
    val sshUseKeyAuth: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: TestResult? = null,
    val error: String? = null,
) {
    val isValid: Boolean
        get() =
            name.isNotBlank() &&
                isSshValid &&
                when (type) {
                    DatabaseType.SQLITE, DatabaseType.H2 -> path.isNotBlank()
                    DatabaseType.MONGODB -> host.isNotBlank() // MongoDB database is optional
                    DatabaseType.ELASTICSEARCH -> host.isNotBlank() // Elasticsearch only needs host
                    else -> host.isNotBlank() && database.isNotBlank()
                }

    val isSshValid: Boolean
        get() =
            !sshEnabled ||
                (
                    sshHost.isNotBlank() &&
                        sshUsername.isNotBlank() &&
                        (sshUseKeyAuth && sshPrivateKeyPath.isNotBlank() || !sshUseKeyAuth && sshPassword.isNotBlank())
                )

    fun toConnectionConfig(): ConnectionConfig {
        // Build properties map for database-specific options
        val properties = mutableMapOf<String, String>()
        if (type == DatabaseType.MONGODB) {
            if (authSource.isNotBlank()) {
                properties["authSource"] = authSource
            }
            if (useSsl) {
                properties["ssl"] = "true"
            }
        }
        if (type == DatabaseType.ELASTICSEARCH) {
            if (useSsl) {
                properties["ssl"] = "true"
            }
        }

        val sshConfig =
            SshConfig(
                enabled = sshEnabled,
                host = sshHost,
                port = sshPort.toIntOrNull() ?: 22,
                username = sshUsername,
                password = sshPassword,
                privateKeyPath = sshPrivateKeyPath,
                passphrase = sshPassphrase,
                useKeyAuth = sshUseKeyAuth,
            )

        return ConnectionConfig(
            id =
                connectionId ?: java.util.UUID
                    .randomUUID()
                    .toString(),
            name = name,
            type = type,
            host = host,
            port = port.toIntOrNull() ?: 0,
            database = database,
            username = username,
            password = password,
            path = path,
            properties = properties,
            sshConfig = sshConfig,
        )
    }
}

sealed class TestResult {
    data class Success(
        val message: String,
    ) : TestResult()

    data class Error(
        val message: String,
    ) : TestResult()
}
