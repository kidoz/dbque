package su.kidoz.database.driver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.kidoz.core.model.*
import su.kidoz.database.capabilities.*
import java.sql.Connection

open class MySqlDriver : AbstractDatabaseDriver() {
    override val type: DatabaseType = DatabaseType.MYSQL

    override suspend fun getSchemas(
        connection: Connection,
        database: String?,
    ): List<SchemaInfo> =
        withContext(Dispatchers.IO) {
            // MySQL doesn't have schemas in the traditional sense - databases act as schemas
            val schemas = mutableListOf<SchemaInfo>()
            connection.createStatement().use { stmt ->
                stmt.executeQuery("SHOW DATABASES").use { rs ->
                    while (rs.next()) {
                        val dbName = rs.getString(1)
                        if (database == null || dbName == database) {
                            schemas.add(SchemaInfo(name = dbName))
                        }
                    }
                }
            }
            schemas
        }

    // ==================== Database-Scoped Metadata ====================
    //
    // MySQL Connector/J exposes databases as JDBC *catalogs*, not schemas, so
    // `DatabaseMetaData.getTables(catalog, schemaPattern, ...)` ignores the schema
    // argument and a null catalog enumerates every database. The explorer passes the
    // database name as `schema`, so the inherited metadata calls would return tables
    // from all databases with a null `TABLE_SCHEM` - producing cross-database
    // duplicates and non-unique tree keys. These overrides scope to a single database
    // and populate `schema` with the real database name.

    override suspend fun getTables(
        connection: Connection,
        schema: String?,
        catalog: String?,
    ): List<TableInfo> =
        withContext(Dispatchers.IO) {
            val dbName =
                schema ?: catalog ?: connection.catalog
                    ?: return@withContext super.getTables(connection, schema, catalog)

            val sql =
                """
                SELECT TABLE_NAME, TABLE_SCHEMA, TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_NAME
                """.trimIndent()

            try {
                connection.prepareStatement(sql).use { ps ->
                    ps.setString(1, dbName)
                    ps.executeQuery().use { rs ->
                        val tables = mutableListOf<TableInfo>()
                        while (rs.next()) {
                            tables.add(
                                TableInfo(
                                    name = rs.getString("TABLE_NAME"),
                                    schema = rs.getString("TABLE_SCHEMA"),
                                    catalog = null,
                                    type = TableType.TABLE,
                                    comment = rs.getString("TABLE_COMMENT")?.takeIf { it.isNotEmpty() },
                                ),
                            )
                        }
                        tables
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to list MySQL tables, falling back to JDBC metadata" }
                super.getTables(connection, schema, catalog)
            }
        }

    override suspend fun getViews(
        connection: Connection,
        schema: String?,
        catalog: String?,
    ): List<ViewInfo> =
        withContext(Dispatchers.IO) {
            val dbName =
                schema ?: catalog ?: connection.catalog
                    ?: return@withContext super.getViews(connection, schema, catalog)

            val sql =
                """
                SELECT TABLE_NAME, TABLE_SCHEMA, TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'VIEW'
                ORDER BY TABLE_NAME
                """.trimIndent()

            try {
                connection.prepareStatement(sql).use { ps ->
                    ps.setString(1, dbName)
                    ps.executeQuery().use { rs ->
                        val views = mutableListOf<ViewInfo>()
                        while (rs.next()) {
                            views.add(
                                ViewInfo(
                                    name = rs.getString("TABLE_NAME"),
                                    schema = rs.getString("TABLE_SCHEMA"),
                                    catalog = null,
                                    comment = rs.getString("TABLE_COMMENT")?.takeIf { it.isNotEmpty() },
                                ),
                            )
                        }
                        views
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to list MySQL views, falling back to JDBC metadata" }
                super.getViews(connection, schema, catalog)
            }
        }

    // The remaining metadata lookups go through JDBC `DatabaseMetaData`, which filters by
    // catalog for MySQL. Move the database name into the catalog position so a table that
    // exists in several databases resolves to the correct one.

    override suspend fun getColumns(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): List<ColumnInfo> = super.getColumns(connection, table, schema = null, catalog = schema ?: catalog)

    override suspend fun getPrimaryKey(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): PrimaryKeyInfo? = super.getPrimaryKey(connection, table, schema = null, catalog = schema ?: catalog)

    override suspend fun getForeignKeys(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): List<ForeignKeyInfo> = super.getForeignKeys(connection, table, schema = null, catalog = schema ?: catalog)

    override suspend fun getIndexes(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): List<IndexInfo> = super.getIndexes(connection, table, schema = null, catalog = schema ?: catalog)

    override fun escapeIdentifier(identifier: String): String = "`$identifier`"

    override fun getDefaultSchema(connection: Connection): String? =
        try {
            connection.catalog
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get default schema" }
            null
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

    override fun supportsSchemas(): Boolean = false

    override fun supportsCatalogs(): Boolean = true

    override fun getOptimizationProperties(): Map<String, String> = ConnectionOptimizations.forMySql()

    // ==================== MySQL-Specific Version Detection ====================

    override suspend fun getVersion(connection: Connection): DatabaseVersion =
        withContext(Dispatchers.IO) {
            cachedVersion?.let { return@withContext it }

            try {
                connection.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT VERSION()").use { rs ->
                        if (rs.next()) {
                            val version = DatabaseVersion.parseMySql(rs.getString(1))
                            cachedVersion = version
                            return@withContext version
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get MySQL version, falling back to metadata" }
            }

            super.getVersion(connection)
        }

    // ==================== MySQL Table Statistics ====================

    override suspend fun getTableStatistics(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): TableStatistics? =
        withContext(Dispatchers.IO) {
            val dbName = schema ?: catalog ?: connection.catalog ?: return@withContext null
            val sql =
                """
                SELECT
                    TABLE_NAME,
                    TABLE_SCHEMA,
                    TABLE_ROWS,
                    DATA_LENGTH,
                    INDEX_LENGTH,
                    DATA_LENGTH + INDEX_LENGTH AS TOTAL_SIZE,
                    AUTO_INCREMENT,
                    TABLE_COLLATION
                FROM information_schema.TABLES
                WHERE TABLE_NAME = ? AND TABLE_SCHEMA = ?
                """.trimIndent()

            try {
                connection.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, table)
                    stmt.setString(2, dbName)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            TableStatistics(
                                tableName = rs.getString("TABLE_NAME"),
                                schema = rs.getString("TABLE_SCHEMA"),
                                estimatedRowCount = rs.getLong("TABLE_ROWS"),
                                totalSizeBytes = rs.getLong("TOTAL_SIZE"),
                                dataSizeBytes = rs.getLong("DATA_LENGTH"),
                                indexSizeBytes = rs.getLong("INDEX_LENGTH"),
                                lastVacuum = null,
                                lastAnalyze = null,
                                deadTuples = null,
                                autoIncrementValue = rs.getLong("AUTO_INCREMENT").takeIf { !rs.wasNull() },
                                tableCollation = rs.getString("TABLE_COLLATION"),
                            )
                        } else {
                            null
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get MySQL table statistics for $table" }
                super.getTableStatistics(connection, table, schema, catalog)
            }
        }

    // ==================== MySQL Index Statistics ====================

    override suspend fun getIndexStatistics(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): List<IndexStatistics> =
        withContext(Dispatchers.IO) {
            val dbName = schema ?: catalog ?: connection.catalog ?: return@withContext emptyList()
            val version = getVersion(connection)

            // MySQL 8.0+ has sys schema with index statistics
            if (version.isAtLeast(8)) {
                try {
                    val sql =
                        """
                        SELECT
                            s.INDEX_NAME,
                            s.TABLE_NAME,
                            s.TABLE_SCHEMA,
                            SUM(s.STAT_VALUE * @@innodb_page_size) AS index_size
                        FROM mysql.innodb_index_stats s
                        WHERE s.TABLE_NAME = ? AND s.DATABASE_NAME = ?
                          AND s.stat_name = 'size'
                        GROUP BY s.INDEX_NAME, s.TABLE_NAME, s.TABLE_SCHEMA
                        """.trimIndent()

                    connection.prepareStatement(sql).use { stmt ->
                        stmt.setString(1, table)
                        stmt.setString(2, dbName)
                        stmt.executeQuery().use { rs ->
                            val stats = mutableListOf<IndexStatistics>()
                            while (rs.next()) {
                                stats.add(
                                    IndexStatistics(
                                        indexName = rs.getString("INDEX_NAME"),
                                        tableName = rs.getString("TABLE_NAME"),
                                        schema = rs.getString("TABLE_SCHEMA"),
                                        sizeBytes = rs.getLong("index_size"),
                                        indexScans = null,
                                        tuplesRead = null,
                                        tuplesFetched = null,
                                        isUnused = false,
                                    ),
                                )
                            }
                            return@withContext stats
                        }
                    }
                } catch (e: Exception) {
                    logger.debug(e) { "Could not get MySQL index stats from innodb_index_stats" }
                }
            }

            // Fallback: get basic index info
            try {
                val sql =
                    """
                    SELECT
                        INDEX_NAME,
                        TABLE_NAME,
                        TABLE_SCHEMA,
                        STAT_VALUE
                    FROM information_schema.STATISTICS
                    WHERE TABLE_NAME = ? AND TABLE_SCHEMA = ?
                    GROUP BY INDEX_NAME, TABLE_NAME, TABLE_SCHEMA, STAT_VALUE
                    """.trimIndent()

                connection.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, table)
                    stmt.setString(2, dbName)
                    stmt.executeQuery().use { rs ->
                        val stats = mutableListOf<IndexStatistics>()
                        while (rs.next()) {
                            stats.add(
                                IndexStatistics(
                                    indexName = rs.getString("INDEX_NAME"),
                                    tableName = rs.getString("TABLE_NAME"),
                                    schema = rs.getString("TABLE_SCHEMA"),
                                    sizeBytes = null,
                                    indexScans = null,
                                    tuplesRead = null,
                                    tuplesFetched = null,
                                    isUnused = false,
                                ),
                            )
                        }
                        stats
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get MySQL index statistics" }
                emptyList()
            }
        }

    // ==================== MySQL Tables with Stats ====================

    override suspend fun getTablesWithStats(
        connection: Connection,
        schema: String?,
        catalog: String?,
    ): List<TableWithStats> =
        withContext(Dispatchers.IO) {
            val dbName = schema ?: catalog ?: connection.catalog
            val dbFilter =
                dbName?.let { "TABLE_SCHEMA = ?" } ?: "TABLE_SCHEMA NOT IN ('mysql', 'information_schema', 'performance_schema', 'sys')"

            val sql =
                """
                SELECT
                    TABLE_NAME,
                    TABLE_SCHEMA,
                    TABLE_TYPE,
                    TABLE_ROWS,
                    DATA_LENGTH + INDEX_LENGTH AS TOTAL_SIZE,
                    TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE $dbFilter
                ORDER BY TABLE_SCHEMA, TABLE_NAME
                """.trimIndent()

            try {
                val stmt =
                    if (dbName != null) {
                        connection.prepareStatement(sql).apply { setString(1, dbName) }
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
                                            "BASE TABLE" -> "TABLE"
                                            "VIEW" -> "VIEW"
                                            "SYSTEM VIEW" -> "SYSTEM_VIEW"
                                            else -> rs.getString("TABLE_TYPE")
                                        },
                                    estimatedRowCount = rs.getLong("TABLE_ROWS"),
                                    sizeBytes = rs.getLong("TOTAL_SIZE"),
                                    comment = rs.getString("TABLE_COMMENT")?.takeIf { it.isNotEmpty() },
                                ),
                            )
                        }
                        tables
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get MySQL tables with stats" }
                super.getTablesWithStats(connection, schema, catalog)
            }
        }

    // ==================== MySQL Running Queries ====================

    override suspend fun getRunningQueries(connection: Connection): List<RunningQuery> =
        withContext(Dispatchers.IO) {
            try {
                connection.createStatement().use { stmt ->
                    stmt.executeQuery("SHOW FULL PROCESSLIST").use { rs ->
                        val queries = mutableListOf<RunningQuery>()
                        while (rs.next()) {
                            val state = rs.getString("State") ?: ""
                            val command = rs.getString("Command") ?: ""

                            // Skip our own connection and sleeping connections
                            if (command == "Sleep" || command == "Daemon") continue

                            queries.add(
                                RunningQuery(
                                    pid = rs.getLong("Id"),
                                    database = rs.getString("db"),
                                    username = rs.getString("User"),
                                    applicationName = null,
                                    clientAddress = rs.getString("Host"),
                                    state =
                                        when {
                                            command == "Query" && state.isNotEmpty() -> QueryState.ACTIVE
                                            command == "Sleep" -> QueryState.IDLE
                                            state.contains("Waiting") -> QueryState.WAITING
                                            else -> QueryState.ACTIVE
                                        },
                                    query = rs.getString("Info"),
                                    startTime = null,
                                    durationMs = rs.getLong("Time") * 1000,
                                    waitEventType = null,
                                    waitEvent = state.takeIf { it.isNotEmpty() },
                                ),
                            )
                        }
                        queries
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get MySQL running queries" }
                emptyList()
            }
        }

    // ==================== MySQL Server Status ====================

    override suspend fun getServerStatus(connection: Connection): ServerStatus? =
        withContext(Dispatchers.IO) {
            try {
                val version = getVersion(connection)
                var activeConnections = 0
                var maxConnections: Int? = null
                var uptime: Long? = null

                // Get global status
                connection.createStatement().use { stmt ->
                    val query =
                        "SHOW GLOBAL STATUS WHERE Variable_name IN " +
                            "('Threads_connected', 'Threads_running', 'Uptime')"
                    stmt.executeQuery(query).use { rs ->
                        while (rs.next()) {
                            when (rs.getString("Variable_name")) {
                                "Threads_connected" -> activeConnections = rs.getInt("Value")
                                "Uptime" -> uptime = rs.getLong("Value")
                            }
                        }
                    }
                }

                // Get max connections
                connection.createStatement().use { stmt ->
                    stmt.executeQuery("SHOW VARIABLES LIKE 'max_connections'").use { rs ->
                        if (rs.next()) {
                            maxConnections = rs.getInt("Value")
                        }
                    }
                }

                // Get database size
                val dbName = connection.catalog
                var databaseSize: Long? = null
                if (dbName != null) {
                    connection.createStatement().use { stmt ->
                        stmt
                            .executeQuery(
                                """
                                SELECT SUM(DATA_LENGTH + INDEX_LENGTH) AS size
                                FROM information_schema.TABLES
                                WHERE TABLE_SCHEMA = '$dbName'
                                """.trimIndent(),
                            ).use { rs ->
                                if (rs.next()) {
                                    databaseSize = rs.getLong("size").takeIf { !rs.wasNull() }
                                }
                            }
                    }
                }

                // Check replica status
                var isReplica = false
                try {
                    connection.createStatement().use { stmt ->
                        stmt.executeQuery("SHOW REPLICA STATUS").use { rs ->
                            isReplica = rs.next()
                        }
                    }
                } catch (e: Exception) {
                    // Try old syntax
                    try {
                        connection.createStatement().use { stmt ->
                            stmt.executeQuery("SHOW SLAVE STATUS").use { rs ->
                                isReplica = rs.next()
                            }
                        }
                    } catch (e2: Exception) {
                        // Not a replica or no permissions
                    }
                }

                ServerStatus(
                    version = version,
                    uptime = uptime,
                    activeConnections = activeConnections,
                    maxConnections = maxConnections,
                    activeTransactions = null,
                    cacheHitRatio = null,
                    databaseSizeBytes = databaseSize,
                    replicationLag = null,
                    isReplica = isReplica,
                )
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get MySQL server status" }
                null
            }
        }

    // ==================== MySQL EXPLAIN ====================

    override fun buildExplainQuery(
        query: String,
        analyze: Boolean,
        format: ExplainFormat,
    ): String {
        val version = cachedVersion
        return when {
            analyze && version != null && version.isAtLeast(8) -> "EXPLAIN ANALYZE $query"
            format == ExplainFormat.JSON -> "EXPLAIN FORMAT=JSON $query"
            format == ExplainFormat.TREE && version != null && version.isAtLeast(8) -> "EXPLAIN FORMAT=TREE $query"
            else -> "EXPLAIN $query"
        }
    }

    override suspend fun explainQuery(
        connection: Connection,
        query: String,
        analyze: Boolean,
        format: ExplainFormat,
    ): ExplainResult =
        withContext(Dispatchers.IO) {
            val version = getVersion(connection)
            val effectiveFormat =
                when {
                    format == ExplainFormat.JSON -> ExplainFormat.JSON
                    format == ExplainFormat.TREE && version.isAtLeast(8) -> ExplainFormat.TREE
                    else -> ExplainFormat.TEXT
                }

            val explainSql = buildExplainQuery(query, analyze, effectiveFormat)
            val output = StringBuilder()

            try {
                connection.createStatement().use { stmt ->
                    stmt.executeQuery(explainSql).use { rs ->
                        val metaData = rs.metaData
                        val columnCount = metaData.columnCount

                        if (effectiveFormat == ExplainFormat.JSON) {
                            // JSON format returns single column
                            while (rs.next()) {
                                output.appendLine(rs.getString(1))
                            }
                        } else {
                            // Traditional EXPLAIN format
                            while (rs.next()) {
                                val row = (1..columnCount).map { rs.getString(it) ?: "" }
                                output.appendLine(row.joinToString("\t"))
                            }
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

            // Parse for metrics
            var totalCost: Double? = null
            var executionTime: Double? = null

            if (effectiveFormat == ExplainFormat.JSON) {
                val costMatch = Regex("\"query_cost\":\\s*\"?([\\d.]+)\"?").find(rawOutput)
                totalCost = costMatch?.groupValues?.get(1)?.toDoubleOrNull()
            }

            if (analyze && version.isAtLeast(8)) {
                // EXPLAIN ANALYZE shows actual times
                val timeMatch = Regex("actual time=([\\d.]+)\\.\\.([\\d.]+)").find(rawOutput)
                executionTime = timeMatch?.groupValues?.get(2)?.toDoubleOrNull()
            }

            ExplainResult(
                rawOutput = rawOutput,
                format = effectiveFormat,
                totalCost = totalCost,
                actualTime = executionTime,
                planningTime = null,
                executionTime = executionTime,
                hasSeqScan =
                    rawOutput.contains("ALL", ignoreCase = false) ||
                        rawOutput.contains("Table scan", ignoreCase = true),
                hasIndexScan =
                    rawOutput.contains("ref") ||
                        rawOutput.contains("range") ||
                        rawOutput.contains("index") ||
                        rawOutput.contains("Index scan", ignoreCase = true),
                hasSortOperation =
                    rawOutput.contains("filesort", ignoreCase = true) ||
                        rawOutput.contains("Sort", ignoreCase = true),
                hasHashJoin = rawOutput.contains("hash join", ignoreCase = true),
                hasNestedLoop = rawOutput.contains("nested loop", ignoreCase = true),
                warnings = extractMySqlWarnings(rawOutput),
            )
        }

    private fun extractMySqlWarnings(output: String): List<String> {
        val warnings = mutableListOf<String>()
        if (output.contains("ALL") && !output.contains("index")) {
            warnings.add("Full table scan detected - consider adding an index")
        }
        if (output.contains("filesort")) {
            warnings.add("Using filesort - consider adding an index to avoid sorting")
        }
        if (output.contains("Using temporary")) {
            warnings.add("Using temporary table - query may be slow for large datasets")
        }
        if (output.contains("Using where") && !output.contains("Using index")) {
            warnings.add("Filtering rows in server - index may not cover all conditions")
        }
        return warnings
    }

    // ==================== MySQL Native DDL ====================

    override suspend fun getNativeCreateTableDdl(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): String =
        withContext(Dispatchers.IO) {
            val dbName = schema ?: catalog ?: connection.catalog
            val fullName =
                if (dbName != null) {
                    "${escapeIdentifier(dbName)}.${escapeIdentifier(table)}"
                } else {
                    escapeIdentifier(table)
                }

            try {
                connection.createStatement().use { stmt ->
                    stmt.executeQuery("SHOW CREATE TABLE $fullName").use { rs ->
                        if (rs.next()) {
                            return@withContext rs.getString(2) + ";"
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get native CREATE TABLE for $table" }
            }

            // Fallback
            generateCreateTableDdl(connection, table, schema, catalog)
        }
}
