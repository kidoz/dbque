package su.kidoz.database.driver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.kidoz.core.model.DatabaseType
import su.kidoz.database.capabilities.*
import java.sql.Connection

class H2Driver : AbstractDatabaseDriver() {
    override val type: DatabaseType = DatabaseType.H2

    override fun getDefaultSchema(connection: Connection): String? =
        try {
            connection.createStatement().use { stmt ->
                stmt.executeQuery("SELECT SCHEMA()").use { rs ->
                    if (rs.next()) rs.getString(1) else "PUBLIC"
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get default schema" }
            "PUBLIC"
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

    override fun supportsCatalogs(): Boolean = false

    override fun getOptimizationProperties(): Map<String, String> = ConnectionOptimizations.forH2()

    // ==================== H2-Specific Version Detection ====================

    override suspend fun getVersion(connection: Connection): DatabaseVersion =
        withContext(Dispatchers.IO) {
            cachedVersion?.let { return@withContext it }

            try {
                connection.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT H2VERSION()").use { rs ->
                        if (rs.next()) {
                            val version = DatabaseVersion.parseH2(rs.getString(1))
                            cachedVersion = version
                            return@withContext version
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get H2 version" }
            }

            super.getVersion(connection)
        }

    override suspend fun getCapabilities(connection: Connection): DatabaseCapabilities =
        withContext(Dispatchers.IO) {
            cachedCapabilities?.let { return@withContext it }

            val version = getVersion(connection)
            val mode = detectH2Mode(connection)
            val capabilities = DatabaseCapabilities.forH2(version, mode)
            cachedCapabilities = capabilities
            capabilities
        }

    // ==================== H2 Table Statistics ====================

    override suspend fun getTableStatistics(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): TableStatistics? =
        withContext(Dispatchers.IO) {
            val schemaName = schema ?: "PUBLIC"

            try {
                // H2 stores row count estimates in INFORMATION_SCHEMA.TABLES
                val sql =
                    """
                    SELECT
                        TABLE_NAME,
                        TABLE_SCHEMA,
                        ROW_COUNT_ESTIMATE
                    FROM INFORMATION_SCHEMA.TABLES
                    WHERE TABLE_NAME = ? AND TABLE_SCHEMA = ?
                    """.trimIndent()

                connection.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, table.uppercase())
                    stmt.setString(2, schemaName.uppercase())
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            TableStatistics(
                                tableName = rs.getString("TABLE_NAME"),
                                schema = rs.getString("TABLE_SCHEMA"),
                                estimatedRowCount = rs.getLong("ROW_COUNT_ESTIMATE"),
                                totalSizeBytes = null,
                                dataSizeBytes = null,
                                indexSizeBytes = null,
                                lastVacuum = null,
                                lastAnalyze = null,
                                deadTuples = null,
                                autoIncrementValue = null,
                                tableCollation = null,
                            )
                        } else {
                            null
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get H2 table statistics for $table" }
                super.getTableStatistics(connection, table, schema, catalog)
            }
        }

    // ==================== H2 Tables with Stats ====================

    override suspend fun getTablesWithStats(
        connection: Connection,
        schema: String?,
        catalog: String?,
    ): List<TableWithStats> =
        withContext(Dispatchers.IO) {
            val schemaFilter = schema?.let { "TABLE_SCHEMA = ?" } ?: "TABLE_SCHEMA NOT IN ('INFORMATION_SCHEMA')"

            val sql =
                """
                SELECT
                    TABLE_NAME,
                    TABLE_SCHEMA,
                    TABLE_TYPE,
                    ROW_COUNT_ESTIMATE,
                    REMARKS
                FROM INFORMATION_SCHEMA.TABLES
                WHERE $schemaFilter
                ORDER BY TABLE_SCHEMA, TABLE_NAME
                """.trimIndent()

            try {
                val stmt =
                    if (schema != null) {
                        connection.prepareStatement(sql).apply { setString(1, schema.uppercase()) }
                    } else {
                        connection.prepareStatement(sql)
                    }

                stmt.use { ps ->
                    ps.executeQuery().use { rs ->
                        val tables = mutableListOf<TableWithStats>()
                        while (rs.next()) {
                            tables.add(
                                TableWithStats(
                                    name = rs.getString("TABLE_NAME"),
                                    schema = rs.getString("TABLE_SCHEMA"),
                                    catalog = null,
                                    type =
                                        when (rs.getString("TABLE_TYPE")) {
                                            "BASE TABLE", "TABLE" -> "TABLE"
                                            "VIEW" -> "VIEW"
                                            "SYSTEM TABLE" -> "SYSTEM_TABLE"
                                            else -> rs.getString("TABLE_TYPE")
                                        },
                                    estimatedRowCount = rs.getLong("ROW_COUNT_ESTIMATE"),
                                    sizeBytes = null,
                                    comment = rs.getString("REMARKS"),
                                ),
                            )
                        }
                        tables
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get H2 tables with stats" }
                super.getTablesWithStats(connection, schema, catalog)
            }
        }

    // ==================== H2 Running Queries (Sessions) ====================

    override suspend fun getRunningQueries(connection: Connection): List<RunningQuery> =
        withContext(Dispatchers.IO) {
            try {
                connection.createStatement().use { stmt ->
                    stmt
                        .executeQuery(
                            """
                            SELECT
                                SESSION_ID,
                                USER_NAME,
                                EXECUTING_STATEMENT,
                                SESSION_START,
                                STATEMENT_START
                            FROM INFORMATION_SCHEMA.SESSIONS
                            WHERE EXECUTING_STATEMENT IS NOT NULL
                            """.trimIndent(),
                        ).use { rs ->
                            val queries = mutableListOf<RunningQuery>()
                            while (rs.next()) {
                                val sessionStart = rs.getTimestamp("SESSION_START")?.time
                                val statementStart = rs.getTimestamp("STATEMENT_START")?.time
                                val durationMs =
                                    if (statementStart != null) {
                                        System.currentTimeMillis() - statementStart
                                    } else {
                                        null
                                    }

                                queries.add(
                                    RunningQuery(
                                        pid = rs.getLong("SESSION_ID"),
                                        database = null,
                                        username = rs.getString("USER_NAME"),
                                        applicationName = null,
                                        clientAddress = null,
                                        state = QueryState.ACTIVE,
                                        query = rs.getString("EXECUTING_STATEMENT"),
                                        startTime = sessionStart,
                                        durationMs = durationMs,
                                        waitEventType = null,
                                        waitEvent = null,
                                    ),
                                )
                            }
                            queries
                        }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get H2 running queries" }
                emptyList()
            }
        }

    // ==================== H2 Server Status ====================

    override suspend fun getServerStatus(connection: Connection): ServerStatus? =
        withContext(Dispatchers.IO) {
            try {
                val version = getVersion(connection)
                var activeSessions = 0

                // Count active sessions
                connection.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS").use { rs ->
                        if (rs.next()) activeSessions = rs.getInt(1)
                    }
                }

                // Get memory usage
                val additionalMetrics = mutableMapOf<String, Any>()
                try {
                    connection.createStatement().use { stmt ->
                        stmt
                            .executeQuery(
                                """
                                SELECT SETTING_NAME, SETTING_VALUE
                                FROM INFORMATION_SCHEMA.SETTINGS
                                WHERE SETTING_NAME LIKE '%CACHE%' OR SETTING_NAME LIKE '%MEMORY%'
                                """.trimIndent(),
                            ).use { rs ->
                                while (rs.next()) {
                                    additionalMetrics[rs.getString("SETTING_NAME")] = rs.getString("SETTING_VALUE")
                                }
                            }
                    }
                } catch (e: Exception) {
                    // Ignore - settings query might not work
                }

                // Get compatibility mode
                val mode = detectH2Mode(connection)
                if (mode != null) {
                    additionalMetrics["compatibility_mode"] = mode.name
                }

                ServerStatus(
                    version = version,
                    uptime = null,
                    activeConnections = activeSessions,
                    maxConnections = null,
                    activeTransactions = null,
                    cacheHitRatio = null,
                    databaseSizeBytes = null,
                    replicationLag = null,
                    isReplica = false,
                    additionalMetrics = additionalMetrics,
                )
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get H2 server status" }
                null
            }
        }

    // ==================== H2 EXPLAIN ====================

    override fun buildExplainQuery(
        query: String,
        analyze: Boolean,
        format: ExplainFormat,
    ): String {
        // H2 supports EXPLAIN ANALYZE
        return if (analyze) {
            "EXPLAIN ANALYZE $query"
        } else {
            "EXPLAIN $query"
        }
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
                val explainSql = buildExplainQuery(query, analyze, format)
                connection.createStatement().use { stmt ->
                    stmt.executeQuery(explainSql).use { rs ->
                        val metaData = rs.metaData
                        val columnCount = metaData.columnCount

                        while (rs.next()) {
                            val row = (1..columnCount).map { rs.getString(it) ?: "" }
                            output.appendLine(row.joinToString("\t"))
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

            // Parse for metrics (H2 specific patterns)
            var totalCost: Double? = null
            var executionTime: Double? = null

            if (analyze) {
                // H2 doesn't directly report execution time in EXPLAIN ANALYZE like PostgreSQL
                // scanCount pattern could be parsed here if needed
            }

            ExplainResult(
                rawOutput = rawOutput,
                format = ExplainFormat.TEXT,
                totalCost = totalCost,
                actualTime = executionTime,
                planningTime = null,
                executionTime = executionTime,
                hasSeqScan =
                    rawOutput.contains("tableScan", ignoreCase = true) ||
                        rawOutput.contains("SCAN", ignoreCase = true),
                hasIndexScan = rawOutput.contains("index", ignoreCase = true),
                hasSortOperation = rawOutput.contains("sort", ignoreCase = true),
                hasHashJoin = rawOutput.contains("hash", ignoreCase = true),
                hasNestedLoop = rawOutput.contains("nested", ignoreCase = true),
                warnings = extractH2Warnings(rawOutput),
            )
        }

    private fun extractH2Warnings(output: String): List<String> {
        val warnings = mutableListOf<String>()
        if (output.contains("tableScan", ignoreCase = true)) {
            warnings.add("Full table scan detected - consider adding an index")
        }
        return warnings
    }

    // ==================== H2 Native DDL ====================

    override suspend fun getNativeCreateTableDdl(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): String =
        withContext(Dispatchers.IO) {
            try {
                // H2 has SCRIPT command but it's complex; use SHOW CREATE TABLE if available (H2 2.0+)
                val schemaName = schema ?: "PUBLIC"
                val fullName = "${escapeIdentifier(schemaName)}.${escapeIdentifier(table)}"

                // Try SHOW CREATE TABLE (H2 2.0+)
                try {
                    connection.createStatement().use { stmt ->
                        stmt.executeQuery("SHOW CREATE TABLE $fullName").use { rs ->
                            if (rs.next()) {
                                return@withContext rs.getString(1) + ";"
                            }
                        }
                    }
                } catch (e: Exception) {
                    // SHOW CREATE TABLE not supported in older versions
                }

                // Fallback: Get table DDL from INFORMATION_SCHEMA
                val ddl = StringBuilder()

                // Get SQL for table
                connection
                    .prepareStatement(
                        """
                        SELECT SQL
                        FROM INFORMATION_SCHEMA.TABLES
                        WHERE TABLE_NAME = ? AND TABLE_SCHEMA = ?
                        """.trimIndent(),
                    ).use { stmt ->
                        stmt.setString(1, table.uppercase())
                        stmt.setString(2, schemaName.uppercase())
                        stmt.executeQuery().use { rs ->
                            if (rs.next()) {
                                val sql = rs.getString("SQL")
                                if (sql != null) {
                                    ddl.appendLine("$sql;")
                                }
                            }
                        }
                    }

                // Get indexes
                connection
                    .prepareStatement(
                        """
                        SELECT SQL
                        FROM INFORMATION_SCHEMA.INDEXES
                        WHERE TABLE_NAME = ? AND TABLE_SCHEMA = ?
                          AND SQL IS NOT NULL
                          AND INDEX_TYPE_NAME != 'PRIMARY KEY'
                        """.trimIndent(),
                    ).use { stmt ->
                        stmt.setString(1, table.uppercase())
                        stmt.setString(2, schemaName.uppercase())
                        stmt.executeQuery().use { rs ->
                            while (rs.next()) {
                                rs.getString("SQL")?.let {
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

    // ==================== H2 Escape Identifier (mode-aware) ====================

    override fun escapeIdentifier(identifier: String): String {
        // Default to standard double quotes
        // Mode-specific quoting will be handled by DatabaseCapabilities
        return "\"$identifier\""
    }

    /**
     * Get the proper escape character based on compatibility mode.
     */
    fun getEscapeChar(connection: Connection): Char =
        when (detectH2Mode(connection)) {
            H2CompatibilityMode.MySQL -> '`'
            else -> '"'
        }
}
