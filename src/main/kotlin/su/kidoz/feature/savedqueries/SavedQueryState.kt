package su.kidoz.feature.savedqueries

import su.kidoz.mvi.UiState
import java.util.UUID

data class SavedQueryState(
    val queries: List<SavedQuery> = emptyList(),
    val folders: List<String> = emptyList(),
    val selectedQueryId: String? = null,
    val expandedFolders: Set<String> = emptySet(),
    val searchText: String = "",
    val dialogState: SavedQueryDialogState? = null,
    val isLoading: Boolean = false,
) : UiState {
    val filteredQueries: List<SavedQuery>
        get() =
            if (searchText.isBlank()) {
                queries
            } else {
                queries.filter {
                    it.name.contains(searchText, ignoreCase = true) ||
                        it.query.contains(searchText, ignoreCase = true)
                }
            }

    val queriesByFolder: Map<String?, List<SavedQuery>>
        get() = filteredQueries.groupBy { it.folder }
}

data class SavedQuery(
    val id: String = UUID.randomUUID().toString(),
    val connectionId: String? = null,
    val name: String,
    val query: String,
    val description: String? = null,
    val folder: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class SavedQueryDialogState(
    val isEditing: Boolean = false,
    val queryId: String? = null,
    val name: String = "",
    val query: String = "",
    val description: String = "",
    val folder: String = "",
    val error: String? = null,
) {
    val isValid: Boolean
        get() = name.isNotBlank() && query.isNotBlank()

    fun toSavedQuery(connectionId: String?): SavedQuery =
        SavedQuery(
            id = queryId ?: UUID.randomUUID().toString(),
            connectionId = connectionId,
            name = name,
            query = query,
            description = description.ifBlank { null },
            folder = folder.ifBlank { null },
        )
}
