package su.kidoz.core.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import su.kidoz.core.model.QueryHistoryEntry
import su.kidoz.storage.AppDatabase

class QueryHistoryRepository(
    private val database: AppDatabase,
) {
    private val queries = database.appDatabaseQueries

    fun getHistoryForConnection(
        connectionId: String,
        limit: Int = 100,
    ): Flow<List<QueryHistoryEntry>> =
        queries
            .getQueryHistory(connectionId, limit.toLong())
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { history ->
                history.map { it.toQueryHistoryEntry() }
            }

    fun getAllHistory(limit: Int = 100): Flow<List<QueryHistoryEntry>> =
        queries
            .getAllQueryHistory(limit.toLong())
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { history ->
                history.map { it.toQueryHistoryEntry() }
            }

    fun searchHistory(
        searchTerm: String,
        limit: Int = 100,
    ): Flow<List<QueryHistoryEntry>> =
        queries
            .searchQueryHistory("%$searchTerm%", limit.toLong())
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { history ->
                history.map { it.toQueryHistoryEntry() }
            }

    suspend fun addHistoryEntry(entry: QueryHistoryEntry) =
        withContext(Dispatchers.IO) {
            queries.insertQueryHistory(
                id = entry.id,
                connection_id = entry.connectionId,
                query = entry.query,
                executed_at = entry.executedAt,
                execution_time_ms = entry.executionTimeMs,
                row_count = entry.rowCount.toLong(),
                successful = if (entry.successful) 1L else 0L,
                error_message = entry.errorMessage,
                database_name = entry.database,
            )
        }

    suspend fun deleteHistoryEntry(id: String) =
        withContext(Dispatchers.IO) {
            queries.deleteQueryHistory(id)
        }

    suspend fun clearHistoryForConnection(connectionId: String) =
        withContext(Dispatchers.IO) {
            queries.clearQueryHistoryForConnection(connectionId)
        }

    suspend fun clearAllHistory() =
        withContext(Dispatchers.IO) {
            queries.clearAllQueryHistory()
        }

    private fun su.kidoz.storage.Query_history.toQueryHistoryEntry(): QueryHistoryEntry =
        QueryHistoryEntry(
            id = id,
            connectionId = connection_id,
            query = query,
            executedAt = executed_at,
            executionTimeMs = execution_time_ms,
            rowCount = row_count.toInt(),
            successful = successful == 1L,
            errorMessage = error_message,
            database = database_name,
        )
}
