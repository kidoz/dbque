package su.kidoz.feature.queryplan

import su.kidoz.mvi.UiEffect

sealed interface QueryPlanEffect : UiEffect {
    data class ShowError(
        val message: String,
    ) : QueryPlanEffect
}
