package su.kidoz.feature.history

import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import su.kidoz.core.repository.QueryHistoryRepository
import su.kidoz.database.ConnectionManager
import su.kidoz.mvi.MviViewModel
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class HistoryViewModel(
    private val queryHistoryRepository: QueryHistoryRepository,
    private val connectionManager: ConnectionManager,
) : MviViewModel<HistoryState, HistoryEvent, HistoryEffect>(HistoryState()) {
    init {
        observeHistory()
    }

    private fun observeHistory() {
        connectionManager.activeConnectionId
            .onEach { connectionId ->
                loadHistory(connectionId)
            }.launchIn(viewModelScope)
    }

    private fun loadHistory(connectionId: String?) {
        viewModelScope.launch {
            updateState { copy(isLoading = true) }
            try {
                val historyFlow =
                    if (connectionId != null) {
                        queryHistoryRepository.getHistoryForConnection(connectionId)
                    } else {
                        queryHistoryRepository.getAllHistory()
                    }

                historyFlow
                    .onEach { entries ->
                        updateState { copy(entries = entries, isLoading = false) }
                    }.launchIn(viewModelScope)
            } catch (e: Exception) {
                logger.error(e) { "Failed to load history" }
                updateState { copy(isLoading = false) }
                sendEffect(HistoryEffect.ShowError(e.message ?: "Failed to load history"))
            }
        }
    }

    override fun onEvent(event: HistoryEvent) {
        when (event) {
            is HistoryEvent.Search -> search(event.text)
            is HistoryEvent.ClearSearch -> clearSearch()
            is HistoryEvent.Refresh -> refresh()
            is HistoryEvent.SelectEntry -> selectEntry(event.entryId)
            is HistoryEvent.DeleteEntry -> deleteEntry(event.entryId)
            is HistoryEvent.ClearHistory -> clearHistory()
            is HistoryEvent.UseQuery -> useQuery(event.query)
            is HistoryEvent.CopyQuery -> copyQuery(event.query)
        }
    }

    private fun search(text: String) {
        updateState { copy(searchText = text) }
    }

    private fun clearSearch() {
        updateState { copy(searchText = "") }
    }

    private fun refresh() {
        loadHistory(connectionManager.activeConnectionId.value)
    }

    private fun selectEntry(entryId: String) {
        updateState { copy(selectedEntryId = entryId) }
    }

    private fun deleteEntry(entryId: String) {
        viewModelScope.launch {
            try {
                queryHistoryRepository.deleteHistoryEntry(entryId)
                sendEffect(HistoryEffect.ShowMessage("Entry deleted"))
            } catch (e: Exception) {
                logger.error(e) { "Failed to delete entry" }
                sendEffect(HistoryEffect.ShowError(e.message ?: "Failed to delete entry"))
            }
        }
    }

    private fun clearHistory() {
        viewModelScope.launch {
            try {
                val connectionId = connectionManager.activeConnectionId.value
                if (connectionId != null) {
                    queryHistoryRepository.clearHistoryForConnection(connectionId)
                } else {
                    queryHistoryRepository.clearAllHistory()
                }
                sendEffect(HistoryEffect.ShowMessage("History cleared"))
            } catch (e: Exception) {
                logger.error(e) { "Failed to clear history" }
                sendEffect(HistoryEffect.ShowError(e.message ?: "Failed to clear history"))
            }
        }
    }

    private fun useQuery(query: String) {
        sendEffect(HistoryEffect.InsertQuery(query))
    }

    private fun copyQuery(query: String) {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(query), null)
            sendEffect(HistoryEffect.CopiedToClipboard(query))
        } catch (e: Exception) {
            logger.error(e) { "Failed to copy to clipboard" }
            sendEffect(HistoryEffect.ShowError("Failed to copy to clipboard"))
        }
    }
}
