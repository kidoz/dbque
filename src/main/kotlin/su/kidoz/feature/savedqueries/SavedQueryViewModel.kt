package su.kidoz.feature.savedqueries

import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import su.kidoz.database.ConnectionManager
import su.kidoz.mvi.MviViewModel
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class SavedQueryViewModel(
    private val savedQueryRepository: SavedQueryRepository,
    private val connectionManager: ConnectionManager,
) : MviViewModel<SavedQueryState, SavedQueryEvent, SavedQueryEffect>(SavedQueryState()) {
    init {
        observeQueries()
    }

    private fun observeQueries() {
        savedQueryRepository
            .getAllSavedQueries()
            .onEach { queries ->
                val folders = queries.mapNotNull { it.folder }.distinct().sorted()
                updateState {
                    copy(
                        queries = queries,
                        folders = folders,
                        isLoading = false,
                    )
                }
            }.launchIn(viewModelScope)
    }

    override fun onEvent(event: SavedQueryEvent) {
        when (event) {
            is SavedQueryEvent.ShowNewQueryDialog -> showNewDialog()
            is SavedQueryEvent.ShowEditQueryDialog -> showEditDialog(event.queryId)
            is SavedQueryEvent.ShowSaveCurrentQueryDialog -> showSaveCurrentDialog(event.query)
            is SavedQueryEvent.HideDialog -> hideDialog()
            is SavedQueryEvent.UpdateName -> updateDialogField { copy(name = event.name) }
            is SavedQueryEvent.UpdateQuery -> updateDialogField { copy(query = event.query) }
            is SavedQueryEvent.UpdateDescription -> updateDialogField { copy(description = event.description) }
            is SavedQueryEvent.UpdateFolder -> updateDialogField { copy(folder = event.folder) }
            is SavedQueryEvent.SaveQuery -> saveQuery()
            is SavedQueryEvent.DeleteQuery -> deleteQuery(event.queryId)
            is SavedQueryEvent.UseQuery -> useQuery(event.queryId)
            is SavedQueryEvent.CopyQuery -> copyQuery(event.queryId)
            is SavedQueryEvent.SelectQuery -> selectQuery(event.queryId)
            is SavedQueryEvent.ToggleFolder -> toggleFolder(event.folder)
            is SavedQueryEvent.Search -> search(event.text)
            is SavedQueryEvent.Refresh -> {} // Automatic via Flow
        }
    }

    private fun showNewDialog() {
        updateState {
            copy(dialogState = SavedQueryDialogState())
        }
    }

    private fun showEditDialog(queryId: String) {
        val query = currentState.queries.find { it.id == queryId } ?: return
        updateState {
            copy(
                dialogState =
                    SavedQueryDialogState(
                        isEditing = true,
                        queryId = query.id,
                        name = query.name,
                        query = query.query,
                        description = query.description ?: "",
                        folder = query.folder ?: "",
                    ),
            )
        }
    }

    private fun showSaveCurrentDialog(query: String) {
        updateState {
            copy(
                dialogState =
                    SavedQueryDialogState(
                        query = query,
                    ),
            )
        }
    }

    private fun hideDialog() {
        updateState { copy(dialogState = null) }
    }

    private fun updateDialogField(update: SavedQueryDialogState.() -> SavedQueryDialogState) {
        updateState {
            copy(dialogState = dialogState?.update()?.copy(error = null))
        }
    }

    private fun saveQuery() {
        val dialogState = currentState.dialogState ?: return
        if (!dialogState.isValid) return

        viewModelScope.launch {
            try {
                val connectionId = connectionManager.activeConnectionId.value
                val savedQuery = dialogState.toSavedQuery(connectionId)
                savedQueryRepository.saveQuery(savedQuery)
                hideDialog()
                sendEffect(SavedQueryEffect.ShowMessage("Query saved"))
            } catch (e: Exception) {
                logger.error(e) { "Failed to save query" }
                updateState {
                    copy(dialogState = dialogState.copy(error = e.message ?: "Failed to save"))
                }
            }
        }
    }

    private fun deleteQuery(queryId: String) {
        viewModelScope.launch {
            try {
                savedQueryRepository.deleteQuery(queryId)
                sendEffect(SavedQueryEffect.ShowMessage("Query deleted"))
            } catch (e: Exception) {
                logger.error(e) { "Failed to delete query" }
                sendEffect(SavedQueryEffect.ShowError(e.message ?: "Failed to delete query"))
            }
        }
    }

    private fun useQuery(queryId: String) {
        val query = currentState.queries.find { it.id == queryId } ?: return
        sendEffect(SavedQueryEffect.InsertQuery(query.query))
    }

    private fun copyQuery(queryId: String) {
        val query = currentState.queries.find { it.id == queryId } ?: return
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(query.query), null)
            sendEffect(SavedQueryEffect.CopiedToClipboard(query.query))
        } catch (e: Exception) {
            logger.error(e) { "Failed to copy to clipboard" }
            sendEffect(SavedQueryEffect.ShowError("Failed to copy to clipboard"))
        }
    }

    private fun selectQuery(queryId: String?) {
        updateState { copy(selectedQueryId = queryId) }
    }

    private fun toggleFolder(folder: String) {
        updateState {
            copy(
                expandedFolders =
                    if (expandedFolders.contains(folder)) {
                        expandedFolders - folder
                    } else {
                        expandedFolders + folder
                    },
            )
        }
    }

    private fun search(text: String) {
        updateState { copy(searchText = text) }
    }
}
