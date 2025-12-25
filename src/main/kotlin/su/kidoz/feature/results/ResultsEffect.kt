package su.kidoz.feature.results

import su.kidoz.mvi.UiEffect

sealed interface ResultsEffect : UiEffect {
    data class CopiedToClipboard(
        val rowCount: Int,
    ) : ResultsEffect

    data class ExportCompleted(
        val filePath: String,
    ) : ResultsEffect

    data class ShowError(
        val message: String,
    ) : ResultsEffect

    data class ShowMessage(
        val message: String,
    ) : ResultsEffect
}
