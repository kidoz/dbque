package su.kidoz.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ConnectionConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: DatabaseType,
    val host: String = "localhost",
    val port: Int = 0,
    val database: String = "",
    val username: String = "",
    val password: String = "",
    val path: String = "",
    val properties: Map<String, String> = emptyMap(),
    val color: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val effectivePort: Int
        get() = if (port == 0) type.defaultPort else port

    fun buildJdbcUrl(): String =
        when (type) {
            DatabaseType.SQLITE -> type.buildUrl(path = path)
            DatabaseType.H2 -> {
                val mode =
                    if (path.startsWith("tcp://") || path.startsWith("ssl://")) {
                        ""
                    } else {
                        "file"
                    }
                type.buildUrl(path = path, mode = mode)
            }
            else ->
                type.buildUrl(
                    host = host,
                    port = effectivePort,
                    database = database,
                )
        }

    fun toDisplayString(): String =
        when (type) {
            DatabaseType.SQLITE -> "$name (SQLite: $path)"
            DatabaseType.H2 -> "$name (H2: $path)"
            else -> "$name (${type.displayName}: $host:$effectivePort/$database)"
        }
}
