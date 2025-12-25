package su.kidoz.feature.savedqueries

import su.kidoz.mvi.UiEvent

sealed interface SavedQueryEvent : UiEvent {
    // Dialog events
    data object ShowNewQueryDialog : SavedQueryEvent

    data class ShowEditQueryDialog(
        val queryId: String,
    ) : SavedQueryEvent

    data class ShowSaveCurrentQueryDialog(
        val query: String,
    ) : SavedQueryEvent

    data object HideDialog : SavedQueryEvent

    // Dialog field updates
    data class UpdateName(
        val name: String,
    ) : SavedQueryEvent

    data class UpdateQuery(
        val query: String,
    ) : SavedQueryEvent

    data class UpdateDescription(
        val description: String,
    ) : SavedQueryEvent

    data class UpdateFolder(
        val folder: String,
    ) : SavedQueryEvent

    // Actions
    data object SaveQuery : SavedQueryEvent

    data class DeleteQuery(
        val queryId: String,
    ) : SavedQueryEvent

    data class UseQuery(
        val queryId: String,
    ) : SavedQueryEvent

    data class CopyQuery(
        val queryId: String,
    ) : SavedQueryEvent

    data class SelectQuery(
        val queryId: String?,
    ) : SavedQueryEvent

    // Folder management
    data class ToggleFolder(
        val folder: String,
    ) : SavedQueryEvent

    data class Search(
        val text: String,
    ) : SavedQueryEvent

    data object Refresh : SavedQueryEvent
}
