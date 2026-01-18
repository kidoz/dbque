package su.kidoz.feature.connection

import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import su.kidoz.core.repository.ConnectionRepository
import su.kidoz.database.ConnectionManager
import su.kidoz.mvi.MviViewModel

class ConnectionViewModel(
    private val connectionRepository: ConnectionRepository,
    private val connectionManager: ConnectionManager,
) : MviViewModel<ConnectionState, ConnectionEvent, ConnectionEffect>(ConnectionState()) {
    init {
        observeConnections()
        observeActiveConnections()
    }

    private fun observeConnections() {
        connectionRepository
            .getAllConnections()
            .onEach { connections ->
                updateState { copy(connections = connections) }
            }.launchIn(viewModelScope)
    }

    private fun observeActiveConnections() {
        connectionManager.connections
            .onEach { activeConnections ->
                updateState { copy(activeConnectionIds = activeConnections.keys) }
            }.launchIn(viewModelScope)

        connectionManager.activeConnectionId
            .onEach { activeId ->
                updateState { copy(selectedConnectionId = activeId) }
            }.launchIn(viewModelScope)
    }

    override fun onEvent(event: ConnectionEvent) {
        when (event) {
            is ConnectionEvent.ShowNewConnectionDialog -> showNewDialog()
            is ConnectionEvent.ShowEditConnectionDialog -> showEditDialog(event.connectionId)
            is ConnectionEvent.HideConnectionDialog -> hideDialog()
            is ConnectionEvent.UpdateName -> updateDialogField { copy(name = event.name) }
            is ConnectionEvent.UpdateType -> updateDialogField { copy(type = event.type, port = "") }
            is ConnectionEvent.UpdateHost -> updateDialogField { copy(host = event.host) }
            is ConnectionEvent.UpdatePort -> updateDialogField { copy(port = event.port) }
            is ConnectionEvent.UpdateDatabase -> updateDialogField { copy(database = event.database) }
            is ConnectionEvent.UpdateUsername -> updateDialogField { copy(username = event.username) }
            is ConnectionEvent.UpdatePassword -> updateDialogField { copy(password = event.password) }
            is ConnectionEvent.UpdatePath -> updateDialogField { copy(path = event.path) }
            is ConnectionEvent.UpdateAuthSource -> updateDialogField { copy(authSource = event.authSource) }
            is ConnectionEvent.UpdateUseSsl -> updateDialogField { copy(useSsl = event.useSsl) }
            is ConnectionEvent.UpdateSshEnabled -> updateDialogField { copy(sshEnabled = event.enabled) }
            is ConnectionEvent.UpdateSshHost -> updateDialogField { copy(sshHost = event.host) }
            is ConnectionEvent.UpdateSshPort -> updateDialogField { copy(sshPort = event.port) }
            is ConnectionEvent.UpdateSshUsername -> updateDialogField { copy(sshUsername = event.username) }
            is ConnectionEvent.UpdateSshPassword -> updateDialogField { copy(sshPassword = event.password) }
            is ConnectionEvent.UpdateSshPrivateKeyPath -> updateDialogField { copy(sshPrivateKeyPath = event.path) }
            is ConnectionEvent.UpdateSshPassphrase -> updateDialogField { copy(sshPassphrase = event.passphrase) }
            is ConnectionEvent.UpdateSshUseKeyAuth -> updateDialogField { copy(sshUseKeyAuth = event.useKeyAuth) }
            is ConnectionEvent.TestConnection -> testConnection()
            is ConnectionEvent.SaveConnection -> saveConnection()
            is ConnectionEvent.DeleteConnection -> deleteConnection(event.connectionId)
            is ConnectionEvent.Connect -> connect(event.connectionId)
            is ConnectionEvent.Disconnect -> disconnect(event.connectionId)
            is ConnectionEvent.SelectConnection -> selectConnection(event.connectionId)
            is ConnectionEvent.DuplicateConnection -> duplicateConnection(event.connectionId)
        }
    }

    private fun showNewDialog() {
        updateState {
            copy(dialogState = ConnectionDialogState())
        }
    }

    private fun showEditDialog(connectionId: String) {
        val connection = currentState.connections.find { it.id == connectionId } ?: return
        updateState {
            copy(
                dialogState =
                    ConnectionDialogState(
                        isEditing = true,
                        connectionId = connection.id,
                        name = connection.name,
                        type = connection.type,
                        host = connection.host,
                        port = if (connection.port > 0) connection.port.toString() else "",
                        database = connection.database,
                        username = connection.username,
                        password = connection.password,
                        path = connection.path,
                        authSource = connection.properties["authSource"] ?: "",
                        useSsl = connection.properties["ssl"] == "true",
                        sshEnabled = connection.sshConfig.enabled,
                        sshHost = connection.sshConfig.host,
                        sshPort = connection.sshConfig.port.toString(),
                        sshUsername = connection.sshConfig.username,
                        sshPassword = connection.sshConfig.password,
                        sshPrivateKeyPath = connection.sshConfig.privateKeyPath,
                        sshPassphrase = connection.sshConfig.passphrase,
                        sshUseKeyAuth = connection.sshConfig.useKeyAuth,
                    ),
            )
        }
    }

    private fun hideDialog() {
        updateState { copy(dialogState = null) }
    }

    private fun updateDialogField(update: ConnectionDialogState.() -> ConnectionDialogState) {
        updateState {
            copy(dialogState = dialogState?.update()?.copy(testResult = null, error = null))
        }
    }

    private fun testConnection() {
        val dialogState = currentState.dialogState ?: return
        if (!dialogState.isValid) return

        updateState { copy(dialogState = dialogState.copy(isTesting = true, testResult = null)) }

        viewModelScope.launch {
            val config = dialogState.toConnectionConfig()
            val result = connectionManager.testConnection(config)

            updateState {
                copy(
                    dialogState =
                        this.dialogState?.copy(
                            isTesting = false,
                            testResult =
                                result.fold(
                                    onSuccess = { TestResult.Success(it) },
                                    onFailure = { TestResult.Error(it.message ?: "Connection failed") },
                                ),
                        ),
                )
            }
        }
    }

    private fun saveConnection() {
        val dialogState = currentState.dialogState ?: return
        if (!dialogState.isValid) return

        viewModelScope.launch {
            try {
                val config = dialogState.toConnectionConfig()
                connectionRepository.saveConnection(config)
                hideDialog()
                sendEffect(ConnectionEffect.ShowSuccess("Connection saved"))
            } catch (e: Exception) {
                logger.error(e) { "Failed to save connection" }
                updateState {
                    copy(dialogState = dialogState.copy(error = e.message ?: "Failed to save"))
                }
            }
        }
    }

    private fun deleteConnection(connectionId: String) {
        viewModelScope.launch {
            try {
                // Disconnect if connected
                if (currentState.activeConnectionIds.contains(connectionId)) {
                    connectionManager.disconnect(connectionId)
                }
                connectionRepository.deleteConnection(connectionId)
                sendEffect(ConnectionEffect.ShowSuccess("Connection deleted"))
            } catch (e: Exception) {
                logger.error(e) { "Failed to delete connection" }
                sendEffect(ConnectionEffect.ShowError(e.message ?: "Failed to delete connection"))
            }
        }
    }

    private fun connect(connectionId: String) {
        val connection = currentState.connections.find { it.id == connectionId } ?: return

        viewModelScope.launch {
            updateState { copy(isLoading = true) }
            try {
                connectionManager.connect(connection).getOrThrow()
                sendEffect(ConnectionEffect.ConnectionEstablished(connectionId))
            } catch (e: Exception) {
                logger.error(e) { "Failed to connect" }
                sendEffect(ConnectionEffect.ShowError(e.message ?: "Failed to connect"))
            } finally {
                updateState { copy(isLoading = false) }
            }
        }
    }

    private fun disconnect(connectionId: String) {
        viewModelScope.launch {
            try {
                connectionManager.disconnect(connectionId)
                sendEffect(ConnectionEffect.ConnectionClosed(connectionId))
            } catch (e: Exception) {
                logger.error(e) { "Failed to disconnect" }
                sendEffect(ConnectionEffect.ShowError(e.message ?: "Failed to disconnect"))
            }
        }
    }

    private fun selectConnection(connectionId: String) {
        connectionManager.setActiveConnection(connectionId)
    }

    private fun duplicateConnection(connectionId: String) {
        val connection = currentState.connections.find { it.id == connectionId } ?: return
        updateState {
            copy(
                dialogState =
                    ConnectionDialogState(
                        isEditing = false,
                        name = "${connection.name} (copy)",
                        type = connection.type,
                        host = connection.host,
                        port = if (connection.port > 0) connection.port.toString() else "",
                        database = connection.database,
                        username = connection.username,
                        password = connection.password,
                        path = connection.path,
                        authSource = connection.properties["authSource"] ?: "",
                        useSsl = connection.properties["ssl"] == "true",
                        sshEnabled = connection.sshConfig.enabled,
                        sshHost = connection.sshConfig.host,
                        sshPort = connection.sshConfig.port.toString(),
                        sshUsername = connection.sshConfig.username,
                        sshPassword = connection.sshConfig.password,
                        sshPrivateKeyPath = connection.sshConfig.privateKeyPath,
                        sshPassphrase = connection.sshConfig.passphrase,
                        sshUseKeyAuth = connection.sshConfig.useKeyAuth,
                    ),
            )
        }
    }
}
