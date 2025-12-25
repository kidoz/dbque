package su.kidoz.database.driver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.kidoz.core.model.*
import su.kidoz.database.capabilities.*
import java.sql.Connection

class SqliteDriver : AbstractDatabaseDriver() {
    override val type: DatabaseType = DatabaseType.SQLITE

    override suspend fun getDatabases(connection: Connection): List<DatabaseInfo> {
        // SQLite has only one database per file
        return listOf(DatabaseInfo(name = "main"))
    }

    override suspend fun getSchemas(
        connection: Connection,
        database: String?,
    ): List<SchemaInfo> {
        // SQLite doesn't have schemas
        return listOf(SchemaInfo(name = "main"))
    }

    override suspend fun generateCreateTableDdl(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): String =
        withContext(Dispatchers.IO) {
            // SQLite stores the original CREATE statement
            connection.createStatement().use { stmt ->
                stmt.executeQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='$table'").use { rs ->
                    if (rs.next()) {
                        rs.getString(1) + ";"
                    } else {
                        super.generateCreateTableDdl(connection, table, schema, catalog)
                    }
                }
            }
        }

    override fun escapeIdentifier(identifier: String): String = "\"$identifier\""

    override fun getDefaultSchema(connection: Connection): String? = "main"

    override suspend fun generateSelectStatement(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
        limit: Int,
    ): String {
        val tableName = escapeIdentifier(table)
        return "SELECT * FROM $tableName LIMIT $limit;"
    }

    override fun supportsSchemas(): Boolean = false

    override fun supportsCatalogs(): Boolean = false

    override fun getOptimizationProperties(): Map<String, String> = ConnectionOptimizations.forSqlite()

    // ==================== SQLite-Specific Version Detection ====================

    override suspend fun getVersion(connection: Connection): DatabaseVersion =
        withContext(Dispatchers.IO) {
            cachedVersion?.let { return@withContext it }

            try {
                connection.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT sqlite_version()").use { rs ->
                        if (rs.next()) {
                            val version = DatabaseVersion.parseSqlite(rs.getString(1))
                            cachedVersion = version
                            return@withContext version
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get SQLite version" }
            }

            super.getVersion(connection)
        }

    // ==================== SQLite Connection Optimization ====================

    /**
     * Apply SQLite PRAGMA optimizations to the connection.
     * Call this after establishing a connection.
     */
    suspend fun applyPragmaOptimizations(connection: Connection) =
        withContext(Dispatchers.IO) {
            ConnectionOptimizations.sqlitePragmas.forEach { (pragma, value) ->
                try {
                    connection.createStatement().use { stmt ->
                        stmt.execute("PRAGMA $pragma = $value")
                    }
                } catch (e: Exception) {
                    logger.debug(e) { "Could not set PRAGMA $pragma = $value" }
                }
            }
        }

    // ==================== SQLite Table Statistics ====================

    override suspend fun getTableStatistics(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): TableStatistics? =
        withContext(Dispatchers.IO) {
            try {
                // Try to get stats from sqlite_stat1 if ANALYZE has been run
                var estimatedRows: Long? = null

                connection.createStatement().use { stmt ->
                    try {
                        stmt.executeQuery("SELECT stat FROM sqlite_stat1 WHERE tbl = '$table' AND idx IS NULL").use { rs ->
                            if (rs.next()) {
                                val stat = rs.getString(1)
                                // First number is estimated row count
                                estimatedRows = stat.split(" ").firstOrNull()?.toLongOrNull()
                            }
                        }
                    } catch (e: Exception) {
                        // sqlite_stat1 might not exist if ANALYZE hasn't been run
                    }
                }

                // Get page count for size estimation
                var pageCount: Long = 0
                var pageSize: Long = 4096

                connection.createStatement().use { stmt ->
                    stmt.executeQuery("PRAGMA page_size").use { rs ->
                        if (rs.next()) pageSize = rs.getLong(1)
                    }
                }

                connection.createStatement().use { stmt ->
                    stmt.executeQuery("PRAGMA page_count").use { rs ->
                        if (rs.next()) pageCount = rs.getLong(1)
                    }
                }

                // If we don't have stats, fall back to COUNT (but warn it might be slow)
                if (estimatedRows == null) {
                    connection.createStatement().use { stmt ->
                        stmt.executeQuery("SELECT COUNT(*) FROM ${escapeIdentifier(table)}").use { rs ->
                            if (rs.next()) estimatedRows = rs.getLong(1)
                        }
                    }
                }

                TableStatistics(
                    tableName = table,
                    schema = "main",
                    estimatedRowCount = estimatedRows ?: 0,
                    totalSizeBytes = pageCount * pageSize,
                    dataSizeBytes = null,
                    indexSizeBytes = null,
                    lastVacuum = null,
                    lastAnalyze = null,
                    deadTuples = null,
                    autoIncrementValue = null,
                    tableCollation = null,
                )
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get SQLite table statistics for $table" }
                super.getTableStatistics(connection, table, schema, catalog)
            }
        }

    // ==================== SQLite Tables with Stats ====================

    override suspend fun getTablesWithStats(
        connection: Connection,
        schema: String?,
        catalog: String?,
    ): List<TableWithStats> =
        withContext(Dispatchers.IO) {
            try {
                val tables = mutableListOf<TableWithStats>()

                // Get all tables and views from sqlite_master
                connection.createStatement().use { stmt ->
                    stmt
                        .executeQuery(
                            """
                            SELECT name, type
                            FROM sqlite_master
                            WHERE type IN ('table', 'view')
                              AND name NOT LIKE 'sqlite_%'
                            ORDER BY name
                            """.trimIndent(),
                        ).use { rs ->
                            while (rs.next()) {
                                val name = rs.getString("name")
                                val type = rs.getString("type").uppercase()

                                // Try to get row count from stats
                                var rowCount: Long? = null
                                try {
                                    connection.createStatement().use { countStmt ->
                                        countStmt
                                            .executeQuery(
                                                "SELECT stat FROM sqlite_stat1 WHERE tbl = '$name' AND idx IS NULL",
                                            ).use { countRs ->
                                                if (countRs.next()) {
                                                    rowCount =
                                                        countRs
                                                            .getString(1)
                                                            .split(" ")
                                                            .firstOrNull()
                                                            ?.toLongOrNull()
                                                }
                                            }
                                    }
                                } catch (e: Exception) {
                                    // Ignore - stats not available
                                }

                                tables.add(
                                    TableWithStats(
                                        name = name,
                                        schema = "main",
                                        catalog = null,
                                        type = type,
                                        estimatedRowCount = rowCount,
                                        sizeBytes = null,
                                        comment = null,
                                    ),
                                )
                            }
                        }
                }

                tables
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get SQLite tables with stats" }
                super.getTablesWithStats(connection, schema, catalog)
            }
        }

    // ==================== SQLite Server Status ====================

    override suspend fun getServerStatus(connection: Connection): ServerStatus? =
        withContext(Dispatchers.IO) {
            try {
                val version = getVersion(connection)
                var databaseSize: Long = 0

                // Calculate database size
                connection.createStatement().use { stmt ->
                    var pageCount: Long = 0
                    var pageSize: Long = 4096

                    stmt.executeQuery("PRAGMA page_count").use { rs ->
                        if (rs.next()) pageCount = rs.getLong(1)
                    }

                    stmt.executeQuery("PRAGMA page_size").use { rs ->
                        if (rs.next()) pageSize = rs.getLong(1)
                    }

                    databaseSize = pageCount * pageSize
                }

                // Get WAL mode status
                val additionalMetrics = mutableMapOf<String, Any>()
                connection.createStatement().use { stmt ->
                    stmt.executeQuery("PRAGMA journal_mode").use { rs ->
                        if (rs.next()) additionalMetrics["journal_mode"] = rs.getString(1)
                    }
                }

                ServerStatus(
                    version = version,
                    uptime = null,
                    activeConnections = 1,
                    maxConnections = null,
                    activeTransactions = null,
                    cacheHitRatio = null,
                    databaseSizeBytes = databaseSize,
                    replicationLag = null,
                    isReplica = false,
                    additionalMetrics = additionalMetrics,
                )
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get SQLite server status" }
                null
            }
        }

    // ==================== SQLite EXPLAIN ====================

    override fun buildExplainQuery(
        query: String,
        analyze: Boolean,
        format: ExplainFormat,
    ): String {
        // SQLite uses EXPLAIN QUERY PLAN for query planning info
        return "EXPLAIN QUERY PLAN $query"
    }

    override suspend fun explainQuery(
        connection: Connection,
        query: String,
        analyze: Boolean,
        format: ExplainFormat,
    ): ExplainResult =
        withContext(Dispatchers.IO) {
            val output = StringBuilder()

            try {
                connection.createStatement().use { stmt ->
                    stmt.executeQuery("EXPLAIN QUERY PLAN $query").use { rs ->
                        while (rs.next()) {
                            val id = rs.getInt("id")
                            val parent = rs.getInt("parent")
                            val detail = rs.getString("detail")
                            output.appendLine("$id|$parent|$detail")
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to explain query" }
                return@withContext ExplainResult(
                    rawOutput = "Error: ${e.message}",
                    format = ExplainFormat.TEXT,
                    totalCost = null,
                    actualTime = null,
                    planningTime = null,
                    executionTime = null,
                )
            }

            val rawOutput = output.toString()

            ExplainResult(
                rawOutput = rawOutput,
                format = ExplainFormat.TEXT,
                totalCost = null,
                actualTime = null,
                planningTime = null,
                executionTime = null,
                hasSeqScan =
                    rawOutput.contains("SCAN", ignoreCase = true) &&
                        !rawOutput.contains("USING INDEX", ignoreCase = true),
                hasIndexScan =
                    rawOutput.contains("USING INDEX", ignoreCase = true) ||
                        rawOutput.contains("USING COVERING INDEX", ignoreCase = true),
                hasSortOperation =
                    rawOutput.contains("ORDER BY", ignoreCase = true) ||
                        rawOutput.contains("TEMP B-TREE", ignoreCase = true),
                hasHashJoin = false, // SQLite doesn't use hash joins
                hasNestedLoop = rawOutput.contains("NESTED", ignoreCase = true),
                warnings = extractSqliteWarnings(rawOutput),
            )
        }

    private fun extractSqliteWarnings(output: String): List<String> {
        val warnings = mutableListOf<String>()
        if (output.contains("SCAN") && !output.contains("USING INDEX")) {
            warnings.add("Full table scan - consider adding an index")
        }
        if (output.contains("TEMP B-TREE FOR ORDER BY")) {
            warnings.add("Sorting requires temp B-tree - consider adding an index for ORDER BY columns")
        }
        if (output.contains("TEMP B-TREE FOR GROUP BY")) {
            warnings.add("Grouping requires temp B-tree - consider adding an index for GROUP BY columns")
        }
        if (output.contains("USE TEMP B-TREE FOR DISTINCT")) {
            warnings.add("DISTINCT requires temp B-tree - query may be slow for large datasets")
        }
        return warnings
    }

    // ==================== SQLite Native DDL ====================

    override suspend fun getNativeCreateTableDdl(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): String =
        withContext(Dispatchers.IO) {
            try {
                val ddl = StringBuilder()

                // Get CREATE TABLE
                connection.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='$table'").use { rs ->
                        if (rs.next()) {
                            ddl.appendLine(rs.getString(1) + ";")
                        }
                    }
                }

                // Get CREATE INDEX statements
                connection.createStatement().use { stmt ->
                    val query =
                        "SELECT sql FROM sqlite_master WHERE type='index' " +
                            "AND tbl_name='$table' AND sql IS NOT NULL"
                    stmt.executeQuery(query).use { rs ->
                        while (rs.next()) {
                            ddl.appendLine()
                            ddl.appendLine(rs.getString(1) + ";")
                        }
                    }
                }

                // Get CREATE TRIGGER statements
                connection.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT sql FROM sqlite_master WHERE type='trigger' AND tbl_name='$table'").use { rs ->
                        while (rs.next()) {
                            rs.getString(1)?.let {
                                ddl.appendLine()
                                ddl.appendLine("$it;")
                            }
                        }
                    }
                }

                ddl.toString().ifEmpty {
                    generateCreateTableDdl(connection, table, schema, catalog)
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get native CREATE TABLE for $table" }
                generateCreateTableDdl(connection, table, schema, catalog)
            }
        }
}
