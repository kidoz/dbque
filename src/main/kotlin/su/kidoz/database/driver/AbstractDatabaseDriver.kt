package su.kidoz.database.driver

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.kidoz.core.model.*
import su.kidoz.database.capabilities.*
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.DriverManager

abstract class AbstractDatabaseDriver : DatabaseDriver {
    protected val logger = KotlinLogging.logger {}

    // Cache for version/capabilities
    @Volatile
    protected var cachedVersion: DatabaseVersion? = null

    @Volatile
    protected var cachedCapabilities: DatabaseCapabilities? = null

    override suspend fun connect(config: ConnectionConfig): Connection =
        withContext(Dispatchers.IO) {
            Class.forName(type.driverClass)
            val url = config.buildJdbcUrl()
            logger.debug { "Connecting to $url" }

            val props =
                java.util.Properties().apply {
                    if (config.username.isNotBlank()) {
                        setProperty("user", config.username)
                    }
                    if (config.password.isNotBlank()) {
                        setProperty("password", config.password)
                    }
                    config.properties.forEach { (key, value) ->
                        setProperty(key, value)
                    }
                }

            DriverManager.getConnection(url, props)
        }

    override suspend fun testConnection(config: ConnectionConfig): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                connect(config).use { connection ->
                    val metadata = connection.metaData
                    "${metadata.databaseProductName} ${metadata.databaseProductVersion}"
                }
            }
        }

    override suspend fun getDatabases(connection: Connection): List<DatabaseInfo> =
        withContext(Dispatchers.IO) {
            val databases = mutableListOf<DatabaseInfo>()
            val metadata = connection.metaData
            metadata.catalogs.use { rs ->
                while (rs.next()) {
                    databases.add(DatabaseInfo(name = rs.getString("TABLE_CAT")))
                }
            }
            databases
        }

    override suspend fun getSchemas(
        connection: Connection,
        database: String?,
    ): List<SchemaInfo> =
        withContext(Dispatchers.IO) {
            val schemas = mutableListOf<SchemaInfo>()
            val metadata = connection.metaData
            metadata.schemas.use { rs ->
                while (rs.next()) {
                    val schemaName = rs.getString("TABLE_SCHEM")
                    val catalog = rs.getString("TABLE_CATALOG")
                    if (database == null || catalog == database) {
                        schemas.add(SchemaInfo(name = schemaName, catalog = catalog))
                    }
                }
            }
            schemas
        }

    override suspend fun getTables(
        connection: Connection,
        schema: String?,
        catalog: String?,
    ): List<TableInfo> =
        withContext(Dispatchers.IO) {
            val tables = mutableListOf<TableInfo>()
            val metadata = connection.metaData
            metadata.getTables(catalog, schema, "%", arrayOf("TABLE")).use { rs ->
                while (rs.next()) {
                    tables.add(
                        TableInfo(
                            name = rs.getString("TABLE_NAME"),
                            schema = rs.getString("TABLE_SCHEM"),
                            catalog = rs.getString("TABLE_CAT"),
                            type = TableType.TABLE,
                            comment = rs.getString("REMARKS"),
                        ),
                    )
                }
            }
            tables.sortedBy { it.name }
        }

    override suspend fun getViews(
        connection: Connection,
        schema: String?,
        catalog: String?,
    ): List<ViewInfo> =
        withContext(Dispatchers.IO) {
            val views = mutableListOf<ViewInfo>()
            val metadata = connection.metaData
            metadata.getTables(catalog, schema, "%", arrayOf("VIEW")).use { rs ->
                while (rs.next()) {
                    views.add(
                        ViewInfo(
                            name = rs.getString("TABLE_NAME"),
                            schema = rs.getString("TABLE_SCHEM"),
                            catalog = rs.getString("TABLE_CAT"),
                            comment = rs.getString("REMARKS"),
                        ),
                    )
                }
            }
            views.sortedBy { it.name }
        }

    override suspend fun getColumns(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): List<ColumnInfo> =
        withContext(Dispatchers.IO) {
            val columns = mutableListOf<ColumnInfo>()
            val metadata = connection.metaData
            metadata.getColumns(catalog, schema, table, "%").use { rs ->
                while (rs.next()) {
                    columns.add(
                        ColumnInfo(
                            name = rs.getString("COLUMN_NAME"),
                            dataType = rs.getString("TYPE_NAME"),
                            jdbcType = rs.getInt("DATA_TYPE"),
                            nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                            size = rs.getInt("COLUMN_SIZE"),
                            precision = rs.getInt("COLUMN_SIZE"),
                            scale = rs.getInt("DECIMAL_DIGITS"),
                            defaultValue = rs.getString("COLUMN_DEF"),
                            autoIncrement = rs.getString("IS_AUTOINCREMENT") == "YES",
                            ordinalPosition = rs.getInt("ORDINAL_POSITION"),
                            comment = rs.getString("REMARKS"),
                        ),
                    )
                }
            }
            columns.sortedBy { it.ordinalPosition }
        }

    override suspend fun getPrimaryKey(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): PrimaryKeyInfo? =
        withContext(Dispatchers.IO) {
            val columns = mutableListOf<Pair<Int, String>>()
            var pkName: String? = null
            val metadata = connection.metaData
            metadata.getPrimaryKeys(catalog, schema, table).use { rs ->
                while (rs.next()) {
                    pkName = rs.getString("PK_NAME")
                    val seq = rs.getInt("KEY_SEQ")
                    val colName = rs.getString("COLUMN_NAME")
                    columns.add(seq to colName)
                }
            }
            if (columns.isEmpty()) {
                null
            } else {
                PrimaryKeyInfo(
                    name = pkName,
                    columns = columns.sortedBy { it.first }.map { it.second },
                )
            }
        }

    override suspend fun getForeignKeys(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): List<ForeignKeyInfo> =
        withContext(Dispatchers.IO) {
            val fkMap = mutableMapOf<String, MutableList<Pair<Int, Pair<String, String>>>>()
            val fkMeta = mutableMapOf<String, Triple<String, ForeignKeyRule, ForeignKeyRule>>()
            val metadata = connection.metaData

            metadata.getImportedKeys(catalog, schema, table).use { rs ->
                while (rs.next()) {
                    val fkName = rs.getString("FK_NAME") ?: "fk_${rs.getInt("KEY_SEQ")}"
                    val seq = rs.getInt("KEY_SEQ")
                    val fkColumn = rs.getString("FKCOLUMN_NAME")
                    val pkColumn = rs.getString("PKCOLUMN_NAME")
                    val pkTable = rs.getString("PKTABLE_NAME")
                    val updateRule = mapForeignKeyRule(rs.getInt("UPDATE_RULE"))
                    val deleteRule = mapForeignKeyRule(rs.getInt("DELETE_RULE"))

                    fkMap.getOrPut(fkName) { mutableListOf() }.add(seq to (fkColumn to pkColumn))
                    fkMeta[fkName] = Triple(pkTable, updateRule, deleteRule)
                }
            }

            fkMap.map { (name, cols) ->
                val (refTable, updateRule, deleteRule) = fkMeta[name]!!
                val sortedCols = cols.sortedBy { it.first }
                ForeignKeyInfo(
                    name = name,
                    columns = sortedCols.map { it.second.first },
                    referencedTable = refTable,
                    referencedColumns = sortedCols.map { it.second.second },
                    updateRule = updateRule,
                    deleteRule = deleteRule,
                )
            }
        }

    override suspend fun getIndexes(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): List<IndexInfo> =
        withContext(Dispatchers.IO) {
            val indexMap = mutableMapOf<String, MutableList<Pair<Int, String>>>()
            val indexUnique = mutableMapOf<String, Boolean>()
            val metadata = connection.metaData

            metadata.getIndexInfo(catalog, schema, table, false, false).use { rs ->
                while (rs.next()) {
                    val indexName = rs.getString("INDEX_NAME") ?: continue
                    val seq = rs.getInt("ORDINAL_POSITION")
                    val colName = rs.getString("COLUMN_NAME") ?: continue
                    val nonUnique = rs.getBoolean("NON_UNIQUE")

                    indexMap.getOrPut(indexName) { mutableListOf() }.add(seq to colName)
                    indexUnique[indexName] = !nonUnique
                }
            }

            indexMap.map { (name, cols) ->
                IndexInfo(
                    name = name,
                    columns = cols.sortedBy { it.first }.map { it.second },
                    unique = indexUnique[name] ?: false,
                )
            }
        }

    override suspend fun generateCreateTableDdl(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): String =
        withContext(Dispatchers.IO) {
            val columns = getColumns(connection, table, schema, catalog)
            val pk = getPrimaryKey(connection, table, schema, catalog)
            val fks = getForeignKeys(connection, table, schema, catalog)

            buildString {
                val fullName = buildTableName(table, schema)
                appendLine("CREATE TABLE $fullName (")

                columns.forEachIndexed { index, col ->
                    append("    ${escapeIdentifier(col.name)} ${col.typeDisplay}")
                    if (!col.nullable) append(" NOT NULL")
                    if (col.defaultValue != null) append(" DEFAULT ${col.defaultValue}")
                    if (col.autoIncrement) append(" AUTO_INCREMENT")
                    if (index < columns.size - 1 || pk != null || fks.isNotEmpty()) append(",")
                    appendLine()
                }

                pk?.let {
                    append("    PRIMARY KEY (${it.columns.joinToString { c -> escapeIdentifier(c) }})")
                    if (fks.isNotEmpty()) append(",")
                    appendLine()
                }

                fks.forEachIndexed { index, fk ->
                    append("    FOREIGN KEY (${fk.columns.joinToString { c -> escapeIdentifier(c) }})")
                    append(" REFERENCES ${escapeIdentifier(fk.referencedTable)}")
                    append("(${fk.referencedColumns.joinToString { c -> escapeIdentifier(c) }})")
                    if (fk.deleteRule != ForeignKeyRule.NO_ACTION) append(" ON DELETE ${fk.deleteRule.name.replace("_", " ")}")
                    if (fk.updateRule != ForeignKeyRule.NO_ACTION) append(" ON UPDATE ${fk.updateRule.name.replace("_", " ")}")
                    if (index < fks.size - 1) append(",")
                    appendLine()
                }

                appendLine(");")
            }
        }

    override suspend fun generateInsertStatement(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): String =
        withContext(Dispatchers.IO) {
            val columns = getColumns(connection, table, schema, catalog)
            val fullName = buildTableName(table, schema)
            val colNames = columns.joinToString(", ") { escapeIdentifier(it.name) }
            val placeholders = columns.joinToString(", ") { "?" }
            "INSERT INTO $fullName ($colNames) VALUES ($placeholders);"
        }

    override suspend fun generateSelectStatement(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
        limit: Int,
    ): String =
        withContext(Dispatchers.IO) {
            val fullName = buildTableName(table, schema)
            "SELECT * FROM $fullName LIMIT $limit;"
        }

    override fun escapeIdentifier(identifier: String): String = "\"$identifier\""

    override fun getDefaultSchema(connection: Connection): String? =
        try {
            connection.schema
        } catch (e: Exception) {
            null
        }

    protected fun buildTableName(
        table: String,
        schema: String?,
    ): String =
        if (schema != null) {
            "${escapeIdentifier(schema)}.${escapeIdentifier(table)}"
        } else {
            escapeIdentifier(table)
        }

    private fun mapForeignKeyRule(rule: Int): ForeignKeyRule =
        when (rule) {
            DatabaseMetaData.importedKeyCascade -> ForeignKeyRule.CASCADE
            DatabaseMetaData.importedKeySetNull -> ForeignKeyRule.SET_NULL
            DatabaseMetaData.importedKeySetDefault -> ForeignKeyRule.SET_DEFAULT
            DatabaseMetaData.importedKeyRestrict -> ForeignKeyRule.RESTRICT
            else -> ForeignKeyRule.NO_ACTION
        }

    // ==================== Version & Capabilities - Default Implementations ====================

    override suspend fun getVersion(connection: Connection): DatabaseVersion =
        withContext(Dispatchers.IO) {
            cachedVersion?.let { return@withContext it }

            val metadata = connection.metaData
            val version =
                DatabaseVersion(
                    major = metadata.databaseMajorVersion,
                    minor = metadata.databaseMinorVersion,
                    patch = 0,
                    fullVersion = metadata.databaseProductVersion,
                    productName = metadata.databaseProductName,
                )
            cachedVersion = version
            version
        }

    override suspend fun getCapabilities(connection: Connection): DatabaseCapabilities =
        withContext(Dispatchers.IO) {
            cachedCapabilities?.let { return@withContext it }

            val version = getVersion(connection)
            val capabilities =
                when (type) {
                    DatabaseType.POSTGRESQL -> DatabaseCapabilities.forPostgres(version)

                    DatabaseType.MYSQL -> DatabaseCapabilities.forMySql(version)

                    DatabaseType.SQLITE -> DatabaseCapabilities.forSqlite(version)

                    DatabaseType.H2 -> DatabaseCapabilities.forH2(version, detectH2Mode(connection))

                    DatabaseType.MONGODB -> throw UnsupportedOperationException(
                        "MongoDB doesn't use JDBC. Use MongoDbDriver.getCapabilitiesMongo() instead.",
                    )

                    DatabaseType.ELASTICSEARCH -> throw UnsupportedOperationException(
                        "Elasticsearch doesn't use JDBC. Use ElasticsearchDriver.getCapabilitiesElasticsearch() instead.",
                    )
                }
            cachedCapabilities = capabilities
            capabilities
        }

    protected open fun detectH2Mode(connection: Connection): H2CompatibilityMode? {
        return try {
            val url = connection.metaData.url ?: return null
            when {
                url.contains(";MODE=PostgreSQL", ignoreCase = true) -> H2CompatibilityMode.PostgreSQL
                url.contains(";MODE=MySQL", ignoreCase = true) -> H2CompatibilityMode.MySQL
                url.contains(";MODE=Oracle", ignoreCase = true) -> H2CompatibilityMode.Oracle
                url.contains(";MODE=MSSQLServer", ignoreCase = true) -> H2CompatibilityMode.MSSQLServer
                url.contains(";MODE=DB2", ignoreCase = true) -> H2CompatibilityMode.DB2
                url.contains(";MODE=Derby", ignoreCase = true) -> H2CompatibilityMode.Derby
                url.contains(";MODE=HSQLDB", ignoreCase = true) -> H2CompatibilityMode.HSQLDB
                else -> H2CompatibilityMode.Regular
            }
        } catch (e: Exception) {
            null
        }
    }

    // ==================== Statistics - Default Implementations ====================

    override suspend fun getTableStatistics(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): TableStatistics? =
        withContext(Dispatchers.IO) {
            // Default: use COUNT(*) - inefficient but works everywhere
            try {
                val fullName = buildTableName(table, schema)
                connection.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT COUNT(*) FROM $fullName").use { rs ->
                        if (rs.next()) {
                            TableStatistics(
                                tableName = table,
                                schema = schema,
                                estimatedRowCount = rs.getLong(1),
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
                logger.warn(e) { "Failed to get table statistics for $table" }
                null
            }
        }

    override suspend fun getIndexStatistics(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): List<IndexStatistics> =
        withContext(Dispatchers.IO) {
            // Default: return empty - specific drivers will override
            emptyList()
        }

    override suspend fun getTablesWithStats(
        connection: Connection,
        schema: String?,
        catalog: String?,
    ): List<TableWithStats> =
        withContext(Dispatchers.IO) {
            // Default: use standard metadata without stats
            getTables(connection, schema, catalog).map { table ->
                TableWithStats(
                    name = table.name,
                    schema = table.schema,
                    catalog = table.catalog,
                    type = table.type.name,
                    estimatedRowCount = null,
                    sizeBytes = null,
                    comment = table.comment,
                )
            }
        }

    override suspend fun getRunningQueries(connection: Connection): List<RunningQuery> =
        withContext(Dispatchers.IO) {
            // Default: not supported
            emptyList()
        }

    override suspend fun getServerStatus(connection: Connection): ServerStatus? =
        withContext(Dispatchers.IO) {
            // Default: minimal status
            try {
                val version = getVersion(connection)
                ServerStatus(
                    version = version,
                    uptime = null,
                    activeConnections = 1, // At least us
                    maxConnections = null,
                    activeTransactions = null,
                    cacheHitRatio = null,
                    databaseSizeBytes = null,
                    replicationLag = null,
                    isReplica = false,
                )
            } catch (e: Exception) {
                null
            }
        }

    // ==================== EXPLAIN - Default Implementation ====================

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

            ExplainResult(
                rawOutput = output.toString(),
                format = format,
                totalCost = null,
                actualTime = null,
                planningTime = null,
                executionTime = null,
                hasSeqScan =
                    output.contains("Seq Scan", ignoreCase = true) ||
                        output.contains("TABLE SCAN", ignoreCase = true),
                hasIndexScan =
                    output.contains("Index Scan", ignoreCase = true) ||
                        output.contains("INDEX", ignoreCase = true),
                hasSortOperation = output.contains("Sort", ignoreCase = true),
                hasHashJoin = output.contains("Hash Join", ignoreCase = true),
                hasNestedLoop = output.contains("Nested Loop", ignoreCase = true),
            )
        }

    protected open fun buildExplainQuery(
        query: String,
        analyze: Boolean,
        format: ExplainFormat,
    ): String = "EXPLAIN $query"

    // ==================== Native DDL ====================

    override suspend fun getNativeCreateTableDdl(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): String =
        withContext(Dispatchers.IO) {
            // Default: fall back to generated DDL
            generateCreateTableDdl(connection, table, schema, catalog)
        }
}
