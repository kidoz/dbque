package su.kidoz.feature.results

import su.kidoz.core.model.QueryResult
import su.kidoz.mvi.UiEvent

sealed interface ResultsEvent : UiEvent {
    data class SetResults(
        val results: List<QueryResult>,
    ) : ResultsEvent

    data object ClearResults : ResultsEvent

    data class SelectResultTab(
        val index: Int,
    ) : ResultsEvent

    // Sorting and filtering
    data class SortByColumn(
        val columnIndex: Int,
    ) : ResultsEvent

    data class SetFilter(
        val text: String,
    ) : ResultsEvent

    data object ClearFilter : ResultsEvent

    // Selection
    data class SelectRow(
        val rowIndex: Int,
        val addToSelection: Boolean,
    ) : ResultsEvent

    data class SelectRows(
        val startRow: Int,
        val endRow: Int,
    ) : ResultsEvent

    data object SelectAllRows : ResultsEvent

    data object ClearSelection : ResultsEvent

    data class SelectColumn(
        val columnIndex: Int,
    ) : ResultsEvent

    // Copy/Export
    data object CopySelectedCells : ResultsEvent

    data object CopySelectedRows : ResultsEvent

    data object CopyColumnValues : ResultsEvent

    data object ShowExportDialog : ResultsEvent

    data object HideExportDialog : ResultsEvent

    data class Export(
        val format: ExportFormat,
        val selectedOnly: Boolean,
    ) : ResultsEvent
}
