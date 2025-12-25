package su.kidoz.feature.history

import su.kidoz.core.model.QueryHistoryEntry
import su.kidoz.mvi.UiState

data class HistoryState(
    val entries: List<QueryHistoryEntry> = emptyList(),
    val searchText: String = "",
    val isLoading: Boolean = false,
    val selectedEntryId: String? = null,
) : UiState {
    val filteredEntries: List<QueryHistoryEntry>
        get() =
            if (searchText.isBlank()) {
                entries
            } else {
                entries.filter { entry ->
                    entry.query.contains(searchText, ignoreCase = true)
                }
            }
}
