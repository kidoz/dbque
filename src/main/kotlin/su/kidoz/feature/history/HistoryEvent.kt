package su.kidoz.feature.history

import su.kidoz.mvi.UiEvent

sealed interface HistoryEvent : UiEvent {
    data class Search(
        val text: String,
    ) : HistoryEvent

    data object ClearSearch : HistoryEvent

    data object Refresh : HistoryEvent

    data class SelectEntry(
        val entryId: String,
    ) : HistoryEvent

    data class DeleteEntry(
        val entryId: String,
    ) : HistoryEvent

    data object ClearHistory : HistoryEvent

    data class UseQuery(
        val query: String,
    ) : HistoryEvent

    data class CopyQuery(
        val query: String,
    ) : HistoryEvent
}
