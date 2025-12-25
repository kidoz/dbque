package su.kidoz.database.driver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.kidoz.core.model.DatabaseType
import su.kidoz.database.capabilities.*
import java.sql.Connection

class PostgresDriver : AbstractDatabaseDriver() {
    override val type: DatabaseType = DatabaseType.POSTGRESQL

    override fun getDefaultSchema(connection: Connection): String? =
        try {
            connection.createStatement().use { stmt ->
                stmt.executeQuery("SELECT current_schema()").use { rs ->
                    if (rs.next()) rs.getString(1) else null
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get default schema" }
            "public"
        }

    override suspend fun generateSelectStatement(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
        limit: Int,
    ): String {
        val fullName = buildTableName(table, schema)
        return "SELECT * FROM $fullName LIMIT $limit;"
    }

    override fun supportsSchemas(): Boolean = true

    override fun supportsCatalogs(): Boolean = true

    override fun getOptimizationProperties(): Map<String, String> = ConnectionOptimizations.forPostgres()

    // ==================== PostgreSQL-Specific Version Detection ====================

    override suspend fun getVersion(connection: Connection): DatabaseVersion =
        withContext(Dispatchers.IO) {
            cachedVersion?.let { return@withContext it }

            try {
                connection.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT version()").use { rs ->
                        if (rs.next()) {
                            val version = DatabaseVersion.parsePostgres(rs.getString(1))
                            cachedVersion = version
                            return@withContext version
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get PostgreSQL version, falling back to metadata" }
            }

            // Fallback to JDBC metadata
            super.getVersion(connection)
        }

    // ==================== PostgreSQL Table Statistics ====================

    override suspend fun getTableStatistics(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): TableStatistics? =
        withContext(Dispatchers.IO) {
            val schemaName = schema ?: "public"
            val sql =
                """
                SELECT
                    c.relname AS table_name,
                    n.nspname AS schema_name,
                    c.reltuples::bigint AS estimated_rows,
                    pg_total_relation_size(c.oid) AS total_size,
                    pg_table_size(c.oid) AS table_size,
                    pg_indexes_size(c.oid) AS index_size,
                    EXTRACT(EPOCH FROM s.last_vacuum)::bigint * 1000 AS last_vacuum,
                    EXTRACT(EPOCH FROM s.last_analyze)::bigint * 1000 AS last_analyze,
                    s.n_dead_tup AS dead_tuples
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                LEFT JOIN pg_stat_user_tables s ON s.relid = c.oid
                WHERE c.relname = ? AND n.nspname = ?
                """.trimIndent()

            try {
                connection.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, table)
                    stmt.setString(2, schemaName)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            TableStatistics(
                                tableName = rs.getString("table_name"),
                                schema = rs.getString("schema_name"),
                                estimatedRowCount = rs.getLong("estimated_rows").coerceAtLeast(0),
                                totalSizeBytes = rs.getLong("total_size"),
                                dataSizeBytes = rs.getLong("table_size"),
                                indexSizeBytes = rs.getLong("index_size"),
                                lastVacuum = rs.getLong("last_vacuum").takeIf { !rs.wasNull() },
                                lastAnalyze = rs.getLong("last_analyze").takeIf { !rs.wasNull() },
                                deadTuples = rs.getLong("dead_tuples").takeIf { !rs.wasNull() },
                                autoIncrementValue = null,
                                tableCollation = null,
                            )
                        } else {
                            null
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get PostgreSQL table statistics for $table" }
                super.getTableStatistics(connection, table, schema, catalog)
            }
        }

    // ==================== PostgreSQL Index Statistics ====================

    override suspend fun getIndexStatistics(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): List<IndexStatistics> =
        withContext(Dispatchers.IO) {
            val schemaName = schema ?: "public"
            val sql =
                """
                SELECT
                    i.indexrelname AS index_name,
                    i.relname AS table_name,
                    n.nspname AS schema_name,
                    pg_relation_size(i.indexrelid) AS index_size,
                    i.idx_scan AS index_scans,
                    i.idx_tup_read AS tuples_read,
                    i.idx_tup_fetch AS tuples_fetched,
                    CASE WHEN i.idx_scan = 0 THEN true ELSE false END AS is_unused
                FROM pg_stat_user_indexes i
                JOIN pg_namespace n ON n.oid = (
                    SELECT relnamespace FROM pg_class WHERE oid = i.relid
                )
                WHERE i.relname = ? AND n.nspname = ?
                ORDER BY i.indexrelname
                """.trimIndent()

            try {
                connection.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, table)
                    stmt.setString(2, schemaName)
                    stmt.executeQuery().use { rs ->
                        val stats = mutableListOf<IndexStatistics>()
                        while (rs.next()) {
                            stats.add(
                                IndexStatistics(
                                    indexName = rs.getString("index_name"),
                                    tableName = rs.getString("table_name"),
                                    schema = rs.getString("schema_name"),
                                    sizeBytes = rs.getLong("index_size"),
                                    indexScans = rs.getLong("index_scans"),
                                    tuplesRead = rs.getLong("tuples_read"),
                                    tuplesFetched = rs.getLong("tuples_fetched"),
                                    isUnused = rs.getBoolean("is_unused"),
                                ),
                            )
                        }
                        stats
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get PostgreSQL index statistics" }
                emptyList()
            }
        }

    // ==================== PostgreSQL Tables with Stats ====================

    override suspend fun getTablesWithStats(
        connection: Connection,
        schema: String?,
        catalog: String?,
    ): List<TableWithStats> =
        withContext(Dispatchers.IO) {
            val schemaFilter = schema?.let { "AND n.nspname = ?" } ?: "AND n.nspname NOT IN ('pg_catalog', 'information_schema')"
            val sql =
                """
                SELECT
                    c.relname AS table_name,
                    n.nspname AS schema_name,
                    CASE c.relkind
                        WHEN 'r' THEN 'TABLE'
                        WHEN 'v' THEN 'VIEW'
                        WHEN 'm' THEN 'MATERIALIZED_VIEW'
                        WHEN 'p' THEN 'PARTITIONED_TABLE'
                        ELSE 'OTHER'
                    END AS table_type,
                    c.reltuples::bigint AS estimated_rows,
                    pg_total_relation_size(c.oid) AS total_size,
                    obj_description(c.oid, 'pg_class') AS comment
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE c.relkind IN ('r', 'v', 'm', 'p')
                $schemaFilter
                ORDER BY n.nspname, c.relname
                """.trimIndent()

            try {
                val stmt =
                    if (schema != null) {
                        connection.prepareStatement(sql).apply { setString(1, schema) }
                    } else {
                        connection.prepareStatement(sql)
                    }

                stmt.use { ps ->
                    ps.executeQuery().use { rs ->
                        val tables = mutableListOf<TableWithStats>()
                        while (rs.next()) {
                            tables.add(
                                TableWithStats(
                                    name = rs.getString("table_name"),
                                    schema = rs.getString("schema_name"),
                                    catalog = catalog,
                                    type = rs.getString("table_type"),
                                    estimatedRowCount = rs.getLong("estimated_rows").coerceAtLeast(0),
                                    sizeBytes = rs.getLong("total_size"),
                                    comment = rs.getString("comment"),
                                ),
                            )
                        }
                        tables
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get PostgreSQL tables with stats" }
                super.getTablesWithStats(connection, schema, catalog)
            }
        }

    // ==================== PostgreSQL Running Queries ====================

    override suspend fun getRunningQueries(connection: Connection): List<RunningQuery> =
        withContext(Dispatchers.IO) {
            val sql =
                """
                SELECT
                    pid,
                    datname AS database,
                    usename AS username,
                    application_name,
                    client_addr::text AS client_address,
                    state,
                    query,
                    EXTRACT(EPOCH FROM backend_start)::bigint * 1000 AS start_time,
                    EXTRACT(EPOCH FROM (now() - query_start))::bigint * 1000 AS duration_ms,
                    wait_event_type,
                    wait_event
                FROM pg_stat_activity
                WHERE state != 'idle'
                  AND pid != pg_backend_pid()
                ORDER BY query_start DESC
                """.trimIndent()

            try {
                connection.createStatement().use { stmt ->
                    stmt.executeQuery(sql).use { rs ->
                        val queries = mutableListOf<RunningQuery>()
                        while (rs.next()) {
                            queries.add(
                                RunningQuery(
                                    pid = rs.getLong("pid"),
                                    database = rs.getString("database"),
                                    username = rs.getString("username"),
                                    applicationName = rs.getString("application_name"),
                                    clientAddress = rs.getString("client_address"),
                                    state = mapPostgresState(rs.getString("state")),
                                    query = rs.getString("query"),
                                    startTime = rs.getLong("start_time").takeIf { !rs.wasNull() },
                                    durationMs = rs.getLong("duration_ms").takeIf { !rs.wasNull() },
                                    waitEventType = rs.getString("wait_event_type"),
                                    waitEvent = rs.getString("wait_event"),
                                ),
                            )
                        }
                        queries
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get PostgreSQL running queries" }
                emptyList()
            }
        }

    private fun mapPostgresState(state: String?): QueryState =
        when (state) {
            "active" -> QueryState.ACTIVE
            "idle" -> QueryState.IDLE
            "idle in transaction" -> QueryState.IDLE_IN_TRANSACTION
            "idle in transaction (aborted)" -> QueryState.IDLE_IN_TRANSACTION
            "fastpath function call" -> QueryState.ACTIVE
            "disabled" -> QueryState.DISABLED
            else -> QueryState.UNKNOWN
        }

    // ==================== PostgreSQL Server Status ====================

    override suspend fun getServerStatus(connection: Connection): ServerStatus? =
        withContext(Dispatchers.IO) {
            try {
                val version = getVersion(connection)
                var activeConnections = 0
                var maxConnections: Int? = null
                var cacheHitRatio: Double? = null
                var databaseSize: Long? = null
                var isReplica = false

                // Get connection counts
                connection.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT count(*) FROM pg_stat_activity WHERE state != 'idle'").use { rs ->
                        if (rs.next()) activeConnections = rs.getInt(1)
                    }
                }

                connection.createStatement().use { stmt ->
                    stmt.executeQuery("SHOW max_connections").use { rs ->
                        if (rs.next()) maxConnections = rs.getInt(1)
                    }
                }

                // Cache hit ratio
                connection.createStatement().use { stmt ->
                    stmt
                        .executeQuery(
                            """
                            SELECT
                                CASE WHEN blks_hit + blks_read > 0
                                THEN blks_hit::float / (blks_hit + blks_read)
                                ELSE 0 END AS ratio
                            FROM pg_stat_database
                            WHERE datname = current_database()
                            """.trimIndent(),
                        ).use { rs ->
                            if (rs.next()) cacheHitRatio = rs.getDouble("ratio")
                        }
                }

                // Database size
                connection.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT pg_database_size(current_database())").use { rs ->
                        if (rs.next()) databaseSize = rs.getLong(1)
                    }
                }

                // Check if replica
                connection.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT pg_is_in_recovery()").use { rs ->
                        if (rs.next()) isReplica = rs.getBoolean(1)
                    }
                }

                ServerStatus(
                    version = version,
                    uptime = null,
                    activeConnections = activeConnections,
                    maxConnections = maxConnections,
                    activeTransactions = null,
                    cacheHitRatio = cacheHitRatio,
                    databaseSizeBytes = databaseSize,
                    replicationLag = null,
                    isReplica = isReplica,
                )
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get PostgreSQL server status" }
                null
            }
        }

    // ==================== PostgreSQL EXPLAIN ====================

    override fun buildExplainQuery(
        query: String,
        analyze: Boolean,
        format: ExplainFormat,
    ): String {
        val formatStr =
            when (format) {
                ExplainFormat.JSON -> "JSON"
                ExplainFormat.XML -> "XML"
                ExplainFormat.YAML -> "YAML"
                else -> "TEXT"
            }
        val analyzeStr = if (analyze) ", ANALYZE" else ""
        val buffersStr = if (analyze) ", BUFFERS" else ""
        return "EXPLAIN (FORMAT $formatStr$analyzeStr$buffersStr) $query"
    }

    override suspend fun explainQuery(
        connection: Connection,
        query: String,
        analyze: Boolean,
        format: ExplainFormat,
    ): ExplainResult =
        withContext(Dispatchers.IO) {
            val explainSql = buildExplainQuery(query, analyze, format)
            val output = StringBuilder()

            try {
                connection.createStatement().use { stmt ->
                    stmt.executeQuery(explainSql).use { rs ->
                        while (rs.next()) {
                            output.appendLine(rs.getString(1))
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

            // Parse JSON output for metrics
            var totalCost: Double? = null
            var planningTime: Double? = null
            var executionTime: Double? = null

            if (format == ExplainFormat.JSON) {
                // Extract metrics from JSON
                val costMatch = Regex("\"Total Cost\":\\s*([\\d.]+)").find(rawOutput)
                totalCost = costMatch?.groupValues?.get(1)?.toDoubleOrNull()

                val planningMatch = Regex("\"Planning Time\":\\s*([\\d.]+)").find(rawOutput)
                planningTime = planningMatch?.groupValues?.get(1)?.toDoubleOrNull()

                val executionMatch = Regex("\"Execution Time\":\\s*([\\d.]+)").find(rawOutput)
                executionTime = executionMatch?.groupValues?.get(1)?.toDoubleOrNull()
            }

            ExplainResult(
                rawOutput = rawOutput,
                format = format,
                totalCost = totalCost,
                actualTime = executionTime,
                planningTime = planningTime,
                executionTime = executionTime,
                hasSeqScan = rawOutput.contains("Seq Scan", ignoreCase = true),
                hasIndexScan =
                    rawOutput.contains("Index Scan", ignoreCase = true) ||
                        rawOutput.contains("Index Only Scan", ignoreCase = true) ||
                        rawOutput.contains("Bitmap Index Scan", ignoreCase = true),
                hasSortOperation = rawOutput.contains("Sort", ignoreCase = true),
                hasHashJoin = rawOutput.contains("Hash Join", ignoreCase = true),
                hasNestedLoop = rawOutput.contains("Nested Loop", ignoreCase = true),
                warnings = extractPostgresWarnings(rawOutput),
            )
        }

    private fun extractPostgresWarnings(output: String): List<String> {
        val warnings = mutableListOf<String>()
        if (output.contains("Seq Scan") && !output.contains("Index")) {
            warnings.add("Query uses sequential scan - consider adding an index")
        }
        if (output.contains("Sort Method: external")) {
            warnings.add("Sort spills to disk - consider increasing work_mem")
        }
        if (output.contains("Rows Removed by Filter") && output.contains(Regex("Rows Removed by Filter: (\\d+)"))) {
            val match = Regex("Rows Removed by Filter: (\\d+)").find(output)
            val removed = match?.groupValues?.get(1)?.toLongOrNull() ?: 0
            if (removed > 1000) {
                warnings.add("Large number of rows filtered ($removed) - consider more selective WHERE clause")
            }
        }
        return warnings
    }

    // ==================== PostgreSQL Native DDL ====================

    override suspend fun getNativeCreateTableDdl(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): String =
        withContext(Dispatchers.IO) {
            // PostgreSQL doesn't have a simple SHOW CREATE TABLE
            // We generate it from metadata, but with PostgreSQL-specific syntax
            val columns = getColumns(connection, table, schema, catalog)
            val pk = getPrimaryKey(connection, table, schema, catalog)
            val fks = getForeignKeys(connection, table, schema, catalog)
            val indexes = getIndexes(connection, table, schema, catalog)

            buildString {
                val fullName = buildTableName(table, schema)
                appendLine("CREATE TABLE $fullName (")

                columns.forEachIndexed { index, col ->
                    append("    ${escapeIdentifier(col.name)} ${mapToPostgresType(col)}")
                    if (!col.nullable) append(" NOT NULL")
                    if (col.defaultValue != null) append(" DEFAULT ${col.defaultValue}")
                    if (index < columns.size - 1 || pk != null || fks.isNotEmpty()) append(",")
                    appendLine()
                }

                pk?.let {
                    val constraintName = it.name?.let { n -> "CONSTRAINT ${escapeIdentifier(n)} " } ?: ""
                    append("    ${constraintName}PRIMARY KEY (${it.columns.joinToString { c -> escapeIdentifier(c) }})")
                    if (fks.isNotEmpty()) append(",")
                    appendLine()
                }

                fks.forEachIndexed { index, fk ->
                    val constraintName = fk.name?.let { n -> "CONSTRAINT ${escapeIdentifier(n)} " } ?: ""
                    append("    $constraintName")
                    append("FOREIGN KEY (${fk.columns.joinToString { c -> escapeIdentifier(c) }})")
                    append(" REFERENCES ${escapeIdentifier(fk.referencedTable)}")
                    append("(${fk.referencedColumns.joinToString { c -> escapeIdentifier(c) }})")
                    if (fk.deleteRule != su.kidoz.core.model.ForeignKeyRule.NO_ACTION) {
                        append(" ON DELETE ${fk.deleteRule.name.replace("_", " ")}")
                    }
                    if (fk.updateRule != su.kidoz.core.model.ForeignKeyRule.NO_ACTION) {
                        append(" ON UPDATE ${fk.updateRule.name.replace("_", " ")}")
                    }
                    if (index < fks.size - 1) append(",")
                    appendLine()
                }

                appendLine(");")

                // Add index creation statements
                indexes.filter { idx -> pk == null || idx.name != pk.name }.forEach { idx ->
                    val unique = if (idx.unique) "UNIQUE " else ""
                    appendLine()
                    appendLine("CREATE ${unique}INDEX ${escapeIdentifier(idx.name)}")
                    appendLine("    ON $fullName (${idx.columns.joinToString { c -> escapeIdentifier(c) }});")
                }
            }
        }

    private fun mapToPostgresType(col: su.kidoz.core.model.ColumnInfo): String {
        // Return the original type, or map JDBC types to PostgreSQL types
        val scale = col.scale ?: 0
        val precision = col.precision ?: 0
        val size = col.size ?: 0

        return when {
            col.dataType.equals("serial", ignoreCase = true) -> "SERIAL"
            col.dataType.equals("bigserial", ignoreCase = true) -> "BIGSERIAL"
            col.dataType.equals("int4", ignoreCase = true) -> "INTEGER"
            col.dataType.equals("int8", ignoreCase = true) -> "BIGINT"
            col.dataType.equals("float8", ignoreCase = true) -> "DOUBLE PRECISION"
            col.dataType.equals("bool", ignoreCase = true) -> "BOOLEAN"
            col.dataType.contains("varchar", ignoreCase = true) -> "VARCHAR($size)"
            col.dataType.contains("char", ignoreCase = true) -> "CHAR($size)"
            col.dataType.equals("numeric", ignoreCase = true) -> {
                if (scale > 0) {
                    "NUMERIC($precision, $scale)"
                } else if (precision > 0) {
                    "NUMERIC($precision)"
                } else {
                    "NUMERIC"
                }
            }
            else -> col.dataType
        }
    }
}
