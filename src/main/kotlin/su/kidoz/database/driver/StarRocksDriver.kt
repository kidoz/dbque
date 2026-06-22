package su.kidoz.database.driver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.kidoz.core.model.DatabaseType
import su.kidoz.database.capabilities.*
import java.sql.Connection

/**
 * Driver for StarRocks (https://starrocks.io).
 *
 * StarRocks is wire-compatible with the MySQL protocol and ships no JDBC driver of its
 * own, so it reuses MySQL Connector/J on the FE query port (default 9030) and inherits
 * most metadata behaviour from [MySqlDriver]. Only the parts where StarRocks diverges
 * from MySQL are overridden here:
 *  - version detection uses `current_version()` (the MySQL-compat `VERSION()` reports a
 *    fake MySQL version, not the StarRocks one),
 *  - EXPLAIN has no `FORMAT=JSON/TREE`; StarRocks supports `EXPLAIN [ANALYZE|COSTS|VERBOSE]`,
 *  - StarRocks has no InnoDB internals, so secondary-index statistics are unavailable.
 */
class StarRocksDriver : MySqlDriver() {
    override val type: DatabaseType = DatabaseType.STARROCKS

    // ==================== StarRocks Version Detection ====================

    override suspend fun getVersion(connection: Connection): DatabaseVersion =
        withContext(Dispatchers.IO) {
            cachedVersion?.let { return@withContext it }

            try {
                connection.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT current_version()").use { rs ->
                        if (rs.next()) {
                            val version = DatabaseVersion.parseStarRocks(rs.getString(1))
                            cachedVersion = version
                            return@withContext version
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get StarRocks version, falling back to MySQL-compat VERSION()" }
            }

            super.getVersion(connection)
        }

    // ==================== StarRocks EXPLAIN ====================

    override fun buildExplainQuery(
        query: String,
        analyze: Boolean,
        format: ExplainFormat,
    ): String =
        when {
            analyze -> "EXPLAIN ANALYZE $query"

            // StarRocks lacks FORMAT=JSON/TREE; the cost-annotated plan is the closest analogue.
            format == ExplainFormat.JSON || format == ExplainFormat.TREE -> "EXPLAIN COSTS $query"

            else -> "EXPLAIN $query"
        }

    override suspend fun explainQuery(
        connection: Connection,
        query: String,
        analyze: Boolean,
        format: ExplainFormat,
    ): ExplainResult =
        // StarRocks only ever returns a textual plan, so coerce JSON/TREE requests to TEXT.
        super.explainQuery(connection, query, analyze, ExplainFormat.TEXT)

    // ==================== StarRocks Index Statistics ====================

    override suspend fun getIndexStatistics(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): List<IndexStatistics> = emptyList() // StarRocks has no InnoDB secondary-index statistics
}
