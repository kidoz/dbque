package su.kidoz.feature.queryplan

import su.kidoz.mvi.UiEvent

sealed interface QueryPlanEvent : UiEvent {
    data class AnalyzeQuery(
        val query: String,
        val analyze: Boolean = false,
    ) : QueryPlanEvent

    data class SelectNode(
        val nodeId: String?,
    ) : QueryPlanEvent

    data class SetViewMode(
        val mode: PlanViewMode,
    ) : QueryPlanEvent

    data object Clear : QueryPlanEvent
}
