package su.kidoz.feature.diagram

import su.kidoz.mvi.UiEffect

sealed interface DiagramEffect : UiEffect {
    data class ShowError(
        val message: String,
    ) : DiagramEffect

    data class ShowMessage(
        val message: String,
    ) : DiagramEffect

    data class InsertIntoEditor(
        val sql: String,
    ) : DiagramEffect
}
