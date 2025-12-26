package su.kidoz.feature.editor

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import su.kidoz.core.model.QueryExecution
import su.kidoz.core.model.QueryExecutionResult
import su.kidoz.core.model.QueryHistoryEntry
import su.kidoz.core.model.QueryResult
import su.kidoz.core.repository.QueryHistoryRepository
import su.kidoz.database.ConnectionManager
import su.kidoz.database.executor.QueryExecutor
import su.kidoz.mvi.MviViewModel

class EditorViewModel(
    private val connectionManager: ConnectionManager,
    private val queryExecutor: QueryExecutor,
    private val queryHistoryRepository: QueryHistoryRepository,
) : MviViewModel<EditorState, EditorEvent, EditorEffect>(EditorState()) {
    private var executionJob: Job? = null
    private var tabCounter = 1

    override fun onEvent(event: EditorEvent) {
        when (event) {
            is EditorEvent.NewTab -> newTab()
            is EditorEvent.CloseTab -> closeTab(event.tabId)
            is EditorEvent.SelectTab -> selectTab(event.tabId)
            is EditorEvent.RenameTab -> renameTab(event.tabId, event.newTitle)
            is EditorEvent.UpdateContent -> updateContent(event.content)
            is EditorEvent.UpdateCursor -> updateCursor(event.position)
            is EditorEvent.UpdateSelection -> updateSelection(event.start, event.end)
            is EditorEvent.InsertText -> insertText(event.text)
            is EditorEvent.ExecuteQuery -> executeQuery()
            is EditorEvent.ExecuteSelectedQuery -> executeSelectedQuery()
            is EditorEvent.ExecuteCurrentQuery -> executeCurrentQuery()
            is EditorEvent.ExecuteAllQueries -> executeAllQueries()
            is EditorEvent.CancelExecution -> cancelExecution()
            is EditorEvent.Format -> formatSql()
            is EditorEvent.Undo -> undo()
            is EditorEvent.Redo -> redo()
            is EditorEvent.FindReplace -> findReplace(event.find, event.replace, event.replaceAll)
        }
    }

    private fun newTab() {
        tabCounter++
        val newTab =
            EditorTab(
                title = "Query $tabCounter",
                connectionId = connectionManager.activeConnectionId.value,
            )
        updateState {
            copy(
                tabs = tabs + newTab,
                activeTabId = newTab.id,
            )
        }
    }

    private fun closeTab(tabId: String) {
        val tabs = currentState.tabs
        if (tabs.size <= 1) return

        val tabIndex = tabs.indexOfFirst { it.id == tabId }
        val newTabs = tabs.filter { it.id != tabId }
        val newActiveId =
            if (currentState.activeTabId == tabId) {
                newTabs.getOrNull(maxOf(0, tabIndex - 1))?.id ?: newTabs.first().id
            } else {
                currentState.activeTabId
            }

        updateState {
            copy(tabs = newTabs, activeTabId = newActiveId)
        }
    }

    private fun selectTab(tabId: String) {
        updateState { copy(activeTabId = tabId) }
    }

    private fun renameTab(
        tabId: String,
        newTitle: String,
    ) {
        updateState {
            copy(
                tabs =
                    tabs.map { tab ->
                        if (tab.id == tabId) tab.copy(title = newTitle) else tab
                    },
            )
        }
    }

    private fun updateContent(content: String) {
        updateState {
            copy(
                tabs =
                    tabs.map { tab ->
                        if (tab.id == activeTabId) {
                            // Save current state to undo stack before updating
                            val snapshot = EditorSnapshot(tab.content, tab.cursorPosition)
                            val newUndoStack = (tab.undoStack + snapshot).takeLast(EditorTab.MAX_UNDO_STACK_SIZE)
                            tab.copy(
                                content = content,
                                isModified = true,
                                undoStack = newUndoStack,
                                redoStack = emptyList(), // Clear redo stack on new change
                            )
                        } else {
                            tab
                        }
                    },
            )
        }
    }

    private fun undo() {
        val activeTab = currentState.activeTab ?: return
        if (!activeTab.canUndo) return

        val snapshot = activeTab.undoStack.last()
        val currentSnapshot = EditorSnapshot(activeTab.content, activeTab.cursorPosition)

        updateState {
            copy(
                tabs =
                    tabs.map { tab ->
                        if (tab.id == activeTabId) {
                            tab.copy(
                                content = snapshot.content,
                                cursorPosition = snapshot.cursorPosition,
                                undoStack = tab.undoStack.dropLast(1),
                                redoStack = tab.redoStack + currentSnapshot,
                            )
                        } else {
                            tab
                        }
                    },
            )
        }
    }

    private fun redo() {
        val activeTab = currentState.activeTab ?: return
        if (!activeTab.canRedo) return

        val snapshot = activeTab.redoStack.last()
        val currentSnapshot = EditorSnapshot(activeTab.content, activeTab.cursorPosition)

        updateState {
            copy(
                tabs =
                    tabs.map { tab ->
                        if (tab.id == activeTabId) {
                            tab.copy(
                                content = snapshot.content,
                                cursorPosition = snapshot.cursorPosition,
                                undoStack = tab.undoStack + currentSnapshot,
                                redoStack = tab.redoStack.dropLast(1),
                            )
                        } else {
                            tab
                        }
                    },
            )
        }
    }

    private fun updateCursor(position: Int) {
        updateState {
            copy(
                tabs =
                    tabs.map { tab ->
                        if (tab.id == activeTabId) {
                            tab.copy(cursorPosition = position)
                        } else {
                            tab
                        }
                    },
            )
        }
    }

    private fun updateSelection(
        start: Int,
        end: Int,
    ) {
        updateState {
            copy(
                tabs =
                    tabs.map { tab ->
                        if (tab.id == activeTabId) {
                            tab.copy(selectionStart = start, selectionEnd = end)
                        } else {
                            tab
                        }
                    },
            )
        }
    }

    fun insertText(text: String) {
        val activeTab = currentState.activeTab ?: return
        val newContent =
            buildString {
                append(activeTab.content.substring(0, activeTab.cursorPosition))
                append(text)
                append(activeTab.content.substring(activeTab.cursorPosition))
            }
        val newPosition = activeTab.cursorPosition + text.length

        updateState {
            copy(
                tabs =
                    tabs.map { tab ->
                        if (tab.id == activeTabId) {
                            tab.copy(
                                content = newContent,
                                cursorPosition = newPosition,
                                isModified = true,
                            )
                        } else {
                            tab
                        }
                    },
            )
        }
    }

    private fun executeQuery() {
        val activeTab = currentState.activeTab ?: return
        val query = activeTab.content.trim()
        if (query.isEmpty()) return
        executeQueryInternal(query)
    }

    private fun executeSelectedQuery() {
        val activeTab = currentState.activeTab ?: return
        val query = activeTab.queryToExecute.trim()
        if (query.isEmpty()) return
        executeQueryInternal(query)
    }

    private fun executeCurrentQuery() {
        val activeTab = currentState.activeTab ?: return
        val currentQuery = activeTab.currentQuery
        if (currentQuery == null || currentQuery.isEmpty) {
            sendEffect(EditorEffect.ShowMessage("No query at cursor position"))
            return
        }
        executeQueryInternal(currentQuery.query)
    }

    private fun executeAllQueries() {
        val activeTab = currentState.activeTab ?: return
        val queries = activeTab.allQueries
        if (queries.isEmpty()) {
            sendEffect(EditorEffect.ShowMessage("No queries to execute"))
            return
        }
        executeQueriesSequentially(queries.map { it.query })
    }

    private fun executeQueriesSequentially(queries: List<String>) {
        val activeConnection = connectionManager.activeConnection
        if (activeConnection == null) {
            sendEffect(EditorEffect.QueryError("No active connection"))
            return
        }

        executionJob?.cancel()
        updateState { copy(isExecuting = true) }

        executionJob =
            viewModelScope.launch {
                val allResults = mutableListOf<QueryResult>()
                var hasError = false

                try {
                    val connection = activeConnection.getConnection()
                    val startTime = System.currentTimeMillis()

                    for ((index, query) in queries.withIndex()) {
                        if (!currentState.isExecuting) break // Check if cancelled

                        val execution = QueryExecution(query = query)
                        when (val result = queryExecutor.execute(connection, execution)) {
                            is QueryExecutionResult.Success -> {
                                allResults.add(result.result)
                                saveToHistory(
                                    activeConnection.config.id,
                                    query,
                                    result.result.executionTimeMs,
                                    result.result.rowCount,
                                    true,
                                    null,
                                )
                            }
                            is QueryExecutionResult.MultiResult -> {
                                allResults.addAll(result.results)
                                val totalRows = result.results.sumOf { it.rowCount }
                                saveToHistory(
                                    activeConnection.config.id,
                                    query,
                                    result.results.sumOf { it.executionTimeMs },
                                    totalRows,
                                    true,
                                    null,
                                )
                            }
                            is QueryExecutionResult.Error -> {
                                saveToHistory(activeConnection.config.id, query, 0, 0, false, result.message)
                                sendEffect(
                                    EditorEffect.QueryError(
                                        "Query ${index + 1}/${queries.size} failed: ${result.message}",
                                    ),
                                )
                                hasError = true
                                break
                            }
                        }
                    }

                    if (!hasError && allResults.isNotEmpty()) {
                        val totalTime = System.currentTimeMillis() - startTime
                        sendEffect(EditorEffect.QueryExecuted(allResults))
                        sendEffect(
                            EditorEffect.ShowMessage(
                                "Executed ${allResults.size} queries in ${totalTime}ms",
                            ),
                        )
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Query execution failed" }
                    sendEffect(EditorEffect.QueryError(e.message ?: "Query execution failed"))
                } finally {
                    updateState { copy(isExecuting = false) }
                }
            }
    }

    private fun executeQueryInternal(query: String) {
        val activeConnection = connectionManager.activeConnection
        if (activeConnection == null) {
            sendEffect(EditorEffect.QueryError("No active connection"))
            return
        }

        executionJob?.cancel()
        updateState { copy(isExecuting = true) }

        executionJob =
            viewModelScope.launch {
                try {
                    val connection = activeConnection.getConnection()
                    val execution = QueryExecution(query = query)
                    val startTime = System.currentTimeMillis()

                    when (val result = queryExecutor.execute(connection, execution)) {
                        is QueryExecutionResult.Success -> {
                            saveToHistory(
                                activeConnection.config.id,
                                query,
                                result.result.executionTimeMs,
                                result.result.rowCount,
                                true,
                                null,
                            )
                            sendEffect(EditorEffect.QueryExecuted(listOf(result.result)))
                        }
                        is QueryExecutionResult.MultiResult -> {
                            val totalRows = result.results.sumOf { it.rowCount }
                            val totalTime = System.currentTimeMillis() - startTime
                            saveToHistory(activeConnection.config.id, query, totalTime, totalRows, true, null)
                            sendEffect(EditorEffect.QueryExecuted(result.results))
                        }
                        is QueryExecutionResult.Error -> {
                            saveToHistory(activeConnection.config.id, query, 0, 0, false, result.message)
                            sendEffect(EditorEffect.QueryError(result.message))
                        }
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Query execution failed" }
                    sendEffect(EditorEffect.QueryError(e.message ?: "Query execution failed"))
                } finally {
                    updateState { copy(isExecuting = false) }
                }
            }
    }

    private suspend fun saveToHistory(
        connectionId: String,
        query: String,
        executionTimeMs: Long,
        rowCount: Int,
        successful: Boolean,
        errorMessage: String?,
    ) {
        try {
            queryHistoryRepository.addHistoryEntry(
                QueryHistoryEntry(
                    connectionId = connectionId,
                    query = query,
                    executionTimeMs = executionTimeMs,
                    rowCount = rowCount,
                    successful = successful,
                    errorMessage = errorMessage,
                ),
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to save query history" }
        }
    }

    private fun cancelExecution() {
        executionJob?.cancel()
        updateState { copy(isExecuting = false) }
        sendEffect(EditorEffect.ShowMessage("Query cancelled"))
    }

    private fun formatSql() {
        // Basic SQL formatting - could be enhanced with a proper SQL parser
        val activeTab = currentState.activeTab ?: return
        val formatted = formatSqlSimple(activeTab.content)
        updateState {
            copy(
                tabs =
                    tabs.map { tab ->
                        if (tab.id == activeTabId) {
                            tab.copy(content = formatted, isModified = true)
                        } else {
                            tab
                        }
                    },
            )
        }
    }

    private fun formatSqlSimple(sql: String): String {
        val keywords =
            listOf(
                "SELECT",
                "FROM",
                "WHERE",
                "AND",
                "OR",
                "ORDER BY",
                "GROUP BY",
                "HAVING",
                "JOIN",
                "LEFT JOIN",
                "RIGHT JOIN",
                "INNER JOIN",
                "OUTER JOIN",
                "ON",
                "INSERT INTO",
                "VALUES",
                "UPDATE",
                "SET",
                "DELETE FROM",
                "CREATE TABLE",
                "ALTER TABLE",
                "DROP TABLE",
                "LIMIT",
                "OFFSET",
            )

        var result = sql.trim()
        keywords.forEach { keyword ->
            result =
                result.replace(
                    Regex("(?i)\\b$keyword\\b"),
                    "\n$keyword",
                )
        }
        return result.trim().replace(Regex("\n+"), "\n")
    }

    private fun findReplace(
        find: String,
        replace: String,
        replaceAll: Boolean,
    ) {
        val activeTab = currentState.activeTab ?: return
        val newContent =
            if (replaceAll) {
                activeTab.content.replace(find, replace)
            } else {
                activeTab.content.replaceFirst(find, replace)
            }
        updateState {
            copy(
                tabs =
                    tabs.map { tab ->
                        if (tab.id == activeTabId) {
                            tab.copy(content = newContent, isModified = true)
                        } else {
                            tab
                        }
                    },
            )
        }
    }
}
