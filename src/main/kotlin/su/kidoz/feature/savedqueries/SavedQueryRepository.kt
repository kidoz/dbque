package su.kidoz.feature.savedqueries

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import su.kidoz.storage.AppDatabase

class SavedQueryRepository(
    private val database: AppDatabase,
) {
    private val queries = database.appDatabaseQueries

    fun getAllSavedQueries(): Flow<List<SavedQuery>> =
        queries
            .getAllSavedQueries()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { savedQueries ->
                savedQueries.map { it.toSavedQuery() }
            }

    fun getSavedQueriesForConnection(connectionId: String?): Flow<List<SavedQuery>> =
        queries
            .getSavedQueriesForConnection(connectionId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { savedQueries ->
                savedQueries.map { it.toSavedQuery() }
            }

    suspend fun getSavedQueryById(id: String): SavedQuery? =
        withContext(Dispatchers.IO) {
            queries.getSavedQueryById(id).executeAsOneOrNull()?.toSavedQuery()
        }

    suspend fun saveQuery(query: SavedQuery) =
        withContext(Dispatchers.IO) {
            queries.insertSavedQuery(
                id = query.id,
                connection_id = query.connectionId,
                name = query.name,
                query = query.query,
                description = query.description,
                folder = query.folder,
                created_at = query.createdAt,
                updated_at = System.currentTimeMillis(),
            )
        }

    suspend fun deleteQuery(id: String) =
        withContext(Dispatchers.IO) {
            queries.deleteSavedQuery(id)
        }

    private fun su.kidoz.storage.Saved_queries.toSavedQuery(): SavedQuery =
        SavedQuery(
            id = id,
            connectionId = connection_id,
            name = name,
            query = query,
            description = description,
            folder = folder,
            createdAt = created_at,
            updatedAt = updated_at,
        )
}
