package su.kidoz.feature.results

import su.kidoz.core.model.QueryResult
import su.kidoz.feature.results.ui.EditingCell
import su.kidoz.mvi.UiState

data class ResultsState(
    val results: List<QueryResult> = emptyList(),
    val activeResultIndex: Int = 0,
    val sortColumn: Int? = null,
    val sortAscending: Boolean = true,
    val filterText: String = "",
    val selectedRows: Set<Int> = emptySet(),
    val selectedColumn: Int? = null,
    val isExporting: Boolean = false,
    val exportDialogVisible: Boolean = false,
    // Edit mode state
    val isEditMode: Boolean = false,
    val editingCell: EditingCell? = null,
    val pendingEdits: Map<Pair<Int, Int>, CellEdit> = emptyMap(),
    val isSaving: Boolean = false,
    val deleteConfirmationVisible: Boolean = false,
    val tableName: String? = null,
    val schemaName: String? = null,
    val primaryKeyColumns: List<Int> = emptyList(),
) : UiState {
    val hasPendingChanges: Boolean
        get() = pendingEdits.isNotEmpty()
    val activeResult: QueryResult?
        get() = results.getOrNull(activeResultIndex)

    val filteredRows: List<List<Any?>>
        get() {
            val result = activeResult ?: return emptyList()
            var rows = result.rows

            // Apply filter
            if (filterText.isNotBlank()) {
                rows =
                    rows.filter { row ->
                        row.any { cell ->
                            cell?.toString()?.contains(filterText, ignoreCase = true) == true
                        }
                    }
            }

            // Apply sorting
            if (sortColumn != null && sortColumn < result.columns.size) {
                rows =
                    rows.sortedWith(
                        compareBy(nullsLast()) { row ->
                            row.getOrNull(sortColumn)?.toString()
                        },
                    )
                if (!sortAscending) {
                    rows = rows.reversed()
                }
            }

            return rows
        }
}

enum class ExportFormat(
    val displayName: String,
    val extension: String,
) {
    CSV("CSV", "csv"),
    JSON("JSON", "json"),
    SQL_INSERT("SQL INSERT", "sql"),
    SQL_UPDATE("SQL UPDATE", "sql"),
}
