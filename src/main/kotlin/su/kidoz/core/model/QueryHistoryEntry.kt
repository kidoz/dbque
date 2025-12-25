package su.kidoz.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class QueryHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val connectionId: String,
    val query: String,
    val executedAt: Long = System.currentTimeMillis(),
    val executionTimeMs: Long = 0,
    val rowCount: Int = 0,
    val successful: Boolean = true,
    val errorMessage: String? = null,
    val database: String? = null,
) {
    val formattedQuery: String
        get() = query.trim().replace(Regex("\\s+"), " ").take(200)
}
