package su.kidoz.feature.editor

import su.kidoz.feature.parser.validation.ValidationIssue
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
    val validationIssues: List<ValidationIssue> = emptyList(),
    val isValidating: Boolean = false,
    val selectedIssue: ValidationIssue? = null,
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

    /** Split result containing all queries and the current query at cursor */
    val splitResult: SplitResult
        get() = QuerySplitter.split(content, cursorPosition)

    /** The query at the current cursor position */
    val currentQuery: QueryRange?
        get() = splitResult.currentQuery?.takeIf { !it.isEmpty }

    /** All non-empty queries in this tab */
    val allQueries: List<QueryRange>
        get() = splitResult.nonEmptyQueries

    /** Number of non-empty queries in this tab */
    val queryCount: Int
        get() = splitResult.queryCount

    /** Index of the current query (1-based for display) */
    val currentQueryNumber: Int
        get() {
            val current = currentQuery ?: return 0
            return allQueries.indexOfFirst { it.start == current.start } + 1
        }

    companion object {
        const val MAX_UNDO_STACK_SIZE = 100
    }
}

data class EditorSnapshot(
    val content: String,
    val cursorPosition: Int,
)
