package su.kidoz.feature.editor

import su.kidoz.core.model.QueryResult
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
}
