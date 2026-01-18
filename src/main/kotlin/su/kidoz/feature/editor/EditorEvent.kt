package su.kidoz.feature.editor

import su.kidoz.feature.editor.quickfix.QuickFix
import su.kidoz.feature.parser.validation.ValidationIssue
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

    /** Execute only the query where the cursor is currently positioned */
    data object ExecuteCurrentQuery : EditorEvent

    /** Execute all queries in the tab sequentially */
    data object ExecuteAllQueries : EditorEvent

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

    // Validation events

    /** Internal event when validation completes */
    data class ValidationCompleted(
        val tabId: String,
        val issues: List<ValidationIssue>,
    ) : EditorEvent

    /** Navigate cursor to the position of a validation issue */
    data class NavigateToIssue(
        val issue: ValidationIssue,
    ) : EditorEvent

    /** Select an issue to show quick-fixes */
    data class SelectIssue(
        val issue: ValidationIssue?,
    ) : EditorEvent

    // Quick-fix events

    /** Show available quick-fixes for an issue */
    data class ShowQuickFixes(
        val issue: ValidationIssue,
    ) : EditorEvent

    /** Apply a quick-fix to the current tab */
    data class ApplyQuickFix(
        val quickFix: QuickFix,
    ) : EditorEvent
}
