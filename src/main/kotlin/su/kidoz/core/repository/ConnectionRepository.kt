package su.kidoz.core.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import su.kidoz.core.model.ConnectionConfig
import su.kidoz.core.model.DatabaseType
import su.kidoz.storage.AppDatabase

class ConnectionRepository(
    private val database: AppDatabase,
) {
    private val queries = database.appDatabaseQueries
    private val json = Json { ignoreUnknownKeys = true }

    fun getAllConnections(): Flow<List<ConnectionConfig>> =
        queries
            .getAllConnections()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { connections ->
                connections.map { it.toConnectionConfig() }
            }

    suspend fun getConnectionById(id: String): ConnectionConfig? =
        withContext(Dispatchers.IO) {
            queries.getConnectionById(id).executeAsOneOrNull()?.toConnectionConfig()
        }

    suspend fun saveConnection(config: ConnectionConfig) =
        withContext(Dispatchers.IO) {
            queries.insertConnection(
                id = config.id,
                name = config.name,
                type = config.type.name,
                host = config.host,
                port = config.port.toLong(),
                database_name = config.database,
                username = config.username,
                password = config.password,
                path = config.path,
                properties =
                    json.encodeToString(
                        kotlinx.serialization.serializer<Map<String, String>>(),
                        config.properties,
                    ),
                color = config.color,
                created_at = config.createdAt,
                updated_at = System.currentTimeMillis(),
            )
        }

    suspend fun deleteConnection(id: String) =
        withContext(Dispatchers.IO) {
            queries.deleteConnection(id)
        }

    private fun su.kidoz.storage.Connections.toConnectionConfig(): ConnectionConfig =
        ConnectionConfig(
            id = id,
            name = name,
            type = DatabaseType.valueOf(type),
            host = host,
            port = port.toInt(),
            database = database_name,
            username = username,
            password = password,
            path = path,
            properties =
                try {
                    json.decodeFromString<Map<String, String>>(properties)
                } catch (e: Exception) {
                    emptyMap()
                },
            color = color,
            createdAt = created_at,
            updatedAt = updated_at,
        )
}
