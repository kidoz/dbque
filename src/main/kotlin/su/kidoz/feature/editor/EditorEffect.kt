package su.kidoz.feature.editor

import su.kidoz.core.model.QueryResult
import su.kidoz.feature.editor.quickfix.QuickFix
import su.kidoz.feature.parser.validation.ValidationIssue
import su.kidoz.mvi.UiEffect

sealed interface EditorEffect : UiEffect {
    data class QueryExecuted(
        val results: List<QueryResult>,
    ) : EditorEffect

    data class QueryError(
        val message: String,
    ) : EditorEffect

    data class ShowMessage(
        val message: String,
    ) : EditorEffect

    data class QuickFixesAvailable(
        val issue: ValidationIssue,
        val fixes: List<QuickFix>,
    ) : EditorEffect
}
