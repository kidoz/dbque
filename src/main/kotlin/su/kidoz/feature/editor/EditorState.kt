package su.kidoz.feature.editor

import su.kidoz.mvi.UiState
import java.util.UUID

data class EditorState(
    val tabs: List<EditorTab> = listOf(EditorTab()),
    val activeTabId: String = tabs.firstOrNull()?.id ?: "",
    val isExecuting: Boolean = false,
) : UiState {
    val activeTab: EditorTab?
        get() = tabs.find { it.id == activeTabId }
}

data class EditorTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Query 1",
    val content: String = "",
    val cursorPosition: Int = 0,
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0,
    val isModified: Boolean = false,
    val connectionId: String? = null,
    val undoStack: List<EditorSnapshot> = emptyList(),
    val redoStack: List<EditorSnapshot> = emptyList(),
) {
    val selectedText: String
        get() =
            if (selectionStart != selectionEnd) {
                content.substring(
                    minOf(selectionStart, selectionEnd),
                    maxOf(selectionStart, selectionEnd),
                )
            } else {
                ""
            }

    val queryToExecute: String
        get() = selectedText.ifEmpty { content }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    companion object {
        const val MAX_UNDO_STACK_SIZE = 100
    }
}

data class EditorSnapshot(
    val content: String,
    val cursorPosition: Int,
)
