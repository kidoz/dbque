package su.kidoz.feature.explorer

import su.kidoz.mvi.UiEffect

sealed interface ExplorerEffect : UiEffect {
    data class CopiedToClipboard(
        val text: String,
    ) : ExplorerEffect

    data class InsertIntoEditor(
        val sql: String,
    ) : ExplorerEffect

    data class ShowError(
        val message: String,
    ) : ExplorerEffect
}
