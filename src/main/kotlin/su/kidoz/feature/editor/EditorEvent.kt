package su.kidoz.feature.editor

import su.kidoz.mvi.UiEvent

sealed interface EditorEvent : UiEvent {
    // Tab management
    data object NewTab : EditorEvent

    data class CloseTab(
        val tabId: String,
    ) : EditorEvent

    data class SelectTab(
        val tabId: String,
    ) : EditorEvent

    data class RenameTab(
        val tabId: String,
        val newTitle: String,
    ) : EditorEvent

    // Content editing
    data class UpdateContent(
        val content: String,
    ) : EditorEvent

    data class UpdateCursor(
        val position: Int,
    ) : EditorEvent

    data class UpdateSelection(
        val start: Int,
        val end: Int,
    ) : EditorEvent

    data class InsertText(
        val text: String,
    ) : EditorEvent

    // Execution
    data object ExecuteQuery : EditorEvent

    data object ExecuteSelectedQuery : EditorEvent

    data object CancelExecution : EditorEvent

    // Actions
    data object Format : EditorEvent

    data object Undo : EditorEvent

    data object Redo : EditorEvent

    data class FindReplace(
        val find: String,
        val replace: String,
        val replaceAll: Boolean,
    ) : EditorEvent
}
