package su.kidoz.feature.connection

import su.kidoz.mvi.UiEffect

sealed interface ConnectionEffect : UiEffect {
    data class ShowError(
        val message: String,
    ) : ConnectionEffect

    data class ShowSuccess(
        val message: String,
    ) : ConnectionEffect

    data class ConnectionEstablished(
        val connectionId: String,
    ) : ConnectionEffect

    data class ConnectionClosed(
        val connectionId: String,
    ) : ConnectionEffect
}
