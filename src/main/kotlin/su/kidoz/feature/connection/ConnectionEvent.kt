package su.kidoz.feature.connection

import su.kidoz.core.model.DatabaseType
import su.kidoz.mvi.UiEvent

sealed interface ConnectionEvent : UiEvent {
    // Dialog events
    data object ShowNewConnectionDialog : ConnectionEvent

    data class ShowEditConnectionDialog(
        val connectionId: String,
    ) : ConnectionEvent

    data object HideConnectionDialog : ConnectionEvent

    // Dialog field updates
    data class UpdateName(
        val name: String,
    ) : ConnectionEvent

    data class UpdateType(
        val type: DatabaseType,
    ) : ConnectionEvent

    data class UpdateHost(
        val host: String,
    ) : ConnectionEvent

    data class UpdatePort(
        val port: String,
    ) : ConnectionEvent

    data class UpdateDatabase(
        val database: String,
    ) : ConnectionEvent

    data class UpdateUsername(
        val username: String,
    ) : ConnectionEvent

    data class UpdatePassword(
        val password: String,
    ) : ConnectionEvent

    data class UpdatePath(
        val path: String,
    ) : ConnectionEvent

    // MongoDB-specific
    data class UpdateAuthSource(
        val authSource: String,
    ) : ConnectionEvent

    data class UpdateUseSsl(
        val useSsl: Boolean,
    ) : ConnectionEvent

    // Actions
    data object TestConnection : ConnectionEvent

    data object SaveConnection : ConnectionEvent

    data class DeleteConnection(
        val connectionId: String,
    ) : ConnectionEvent

    data class Connect(
        val connectionId: String,
    ) : ConnectionEvent

    data class Disconnect(
        val connectionId: String,
    ) : ConnectionEvent

    data class SelectConnection(
        val connectionId: String,
    ) : ConnectionEvent

    data class DuplicateConnection(
        val connectionId: String,
    ) : ConnectionEvent
}
