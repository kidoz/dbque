package su.kidoz.database.driver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.kidoz.core.model.*
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
 *  - StarRocks has no InnoDB internals, so secondary-index statistics are unavailable,
 *  - StarRocks does not enforce foreign keys and exposes no conventional secondary indexes,
 *    and the MySQL connector's `getImportedKeys`/`getIndexInfo` issue `information_schema`
 *    queries StarRocks cannot resolve, so both are reported as empty rather than failing.
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

    // ==================== StarRocks Constraint & Index Introspection ====================

    // StarRocks is an OLAP engine that does not enforce foreign keys, and the MySQL
    // connector's getImportedKeys() runs an information_schema query referencing
    // KEY_COLUMN_USAGE.CONSTRAINT_NAME, which StarRocks cannot resolve. Try the inherited
    // path (forward-compatible if a future version supports it) and fall back to none.
    override suspend fun getForeignKeys(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): List<ForeignKeyInfo> =
        try {
            super.getForeignKeys(connection, table, schema, catalog)
        } catch (e: Exception) {
            logger.debug(e) { "Foreign-key metadata unavailable on StarRocks for $table" }
            emptyList()
        }

    // StarRocks has no conventional secondary indexes (it uses sort/prefix keys and
    // bitmap/bloom-filter indexes), and getIndexInfo() hits the same unsupported
    // information_schema path; degrade to an empty list instead of failing.
    override suspend fun getIndexes(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): List<IndexInfo> =
        try {
            super.getIndexes(connection, table, schema, catalog)
        } catch (e: Exception) {
            logger.debug(e) { "Index metadata unavailable on StarRocks for $table" }
            emptyList()
        }

    // ==================== StarRocks Index Statistics ====================

    override suspend fun getIndexStatistics(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): List<IndexStatistics> = emptyList() // StarRocks has no InnoDB secondary-index statistics
}
