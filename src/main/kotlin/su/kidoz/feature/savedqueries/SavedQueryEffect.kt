package su.kidoz.feature.savedqueries

import su.kidoz.mvi.UiEffect

sealed interface SavedQueryEffect : UiEffect {
    data class InsertQuery(
        val query: String,
    ) : SavedQueryEffect

    data class CopiedToClipboard(
        val query: String,
    ) : SavedQueryEffect

    data class ShowError(
        val message: String,
    ) : SavedQueryEffect

    data class ShowMessage(
        val message: String,
    ) : SavedQueryEffect
}
