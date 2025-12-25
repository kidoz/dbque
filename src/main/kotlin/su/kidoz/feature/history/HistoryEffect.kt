package su.kidoz.feature.history

import su.kidoz.mvi.UiEffect

sealed interface HistoryEffect : UiEffect {
    data class InsertQuery(
        val query: String,
    ) : HistoryEffect

    data class CopiedToClipboard(
        val query: String,
    ) : HistoryEffect

    data class ShowError(
        val message: String,
    ) : HistoryEffect

    data class ShowMessage(
        val message: String,
    ) : HistoryEffect
}
