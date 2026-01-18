package su.kidoz.database.driver

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.bson.Document
import su.kidoz.core.model.*
import su.kidoz.database.MongoDatabaseConnection
import su.kidoz.database.capabilities.*
import java.sql.Connection
import java.util.concurrent.TimeUnit

/**
 * MongoDB database driver implementation.
 *
 * MongoDB is a NoSQL document database that doesn't use JDBC.
 * This driver uses the official MongoDB Kotlin Coroutine driver.
 */
class MongoDbDriver : DatabaseDriver {
    private val logger = KotlinLogging.logger {}

    override val type: DatabaseType = DatabaseType.MONGODB

    private var cachedVersion: DatabaseVersion? = null
    private var cachedCapabilities: DatabaseCapabilities? = null

    // ==================== Connection Methods ====================

    /**
     * MongoDB doesn't use JDBC Connection. This method throws an exception.
     * Use connectMongo() instead via ConnectionManager.
     */
    override suspend fun connect(config: ConnectionConfig): Connection =
        throw UnsupportedOperationException(
            "MongoDB doesn't use JDBC Connection. Use ConnectionManager.connect() which handles MongoDB specially.",
        )

    /**
     * Create a MongoDB connection.
     */
    suspend fun connectMongo(config: ConnectionConfig): MongoDatabaseConnection =
        withContext(Dispatchers.IO) {
            val connectionString = buildConnectionString(config)
            logger.info { "Connecting to MongoDB: ${config.host}:${config.port}" }

            val settings =
                MongoClientSettings
                    .builder()
                    .applyConnectionString(ConnectionString(connectionString))
                    .applyToSocketSettings { builder ->
                        builder.connectTimeout(30, TimeUnit.SECONDS)
                        builder.readTimeout(30, TimeUnit.SECONDS)
                    }.applyToConnectionPoolSettings { builder ->
                        builder.maxSize(5)
                        builder.minSize(1)
                        builder.maxConnectionIdleTime(10, TimeUnit.MINUTES)
                    }.build()

            val client = MongoClient.create(settings)
            val databaseName = config.database.ifBlank { "admin" }

            MongoDatabaseConnection(config, client, databaseName)
        }

    override suspend fun testConnection(config: ConnectionConfig): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = connectMongo(config)
                try {
                    val result =
                        connection.client
                            .getDatabase("admin")
                            .runCommand(Document("buildInfo", 1))
                    val version = result.getString("version") ?: "Unknown"
                    "MongoDB $version"
                } finally {
                    connection.close()
                }
            }
        }

    private fun buildConnectionString(config: ConnectionConfig): String {
        val sb = StringBuilder("mongodb://")

        // Authentication
        if (config.username.isNotBlank()) {
            sb.append(config.username)
            if (config.password.isNotBlank()) {
                sb.append(":").append(config.password)
            }
            sb.append("@")
        }

        // Host and port
        sb.append(config.host)
        if (config.port > 0) {
            sb.append(":").append(config.port)
        }

        // Database
        if (config.database.isNotBlank()) {
            sb.append("/").append(config.database)
        }

        // Additional options from properties
        val options = mutableListOf<String>()
        config.properties["authSource"]?.let { options.add("authSource=$it") }
        config.properties["authMechanism"]?.let { options.add("authMechanism=$it") }
        config.properties["replicaSet"]?.let { options.add("replicaSet=$it") }
        config.properties["ssl"]?.let { if (it == "true") options.add("ssl=true") }
        config.properties["tls"]?.let { if (it == "true") options.add("tls=true") }

        if (options.isNotEmpty()) {
            sb.append("?").append(options.joinToString("&"))
        }

        return sb.toString()
    }

    // ==================== Metadata Methods ====================

    override suspend fun getDatabases(connection: Connection): List<DatabaseInfo> =
        throw UnsupportedOperationException("Use getDatabasesMongo() with MongoDatabaseConnection")

    suspend fun getDatabasesMongo(connection: MongoDatabaseConnection): List<DatabaseInfo> =
        withContext(Dispatchers.IO) {
            try {
                connection.client.listDatabaseNames().toList().map { name ->
                    DatabaseInfo(name = name)
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to list MongoDB databases" }
                emptyList()
            }
        }

    override suspend fun getSchemas(
        connection: Connection,
        database: String?,
    ): List<SchemaInfo> {
        // MongoDB doesn't have schemas - collections are directly under databases
        return emptyList()
    }

    override suspend fun getTables(
        connection: Connection,
        schema: String?,
        catalog: String?,
    ): List<TableInfo> = throw UnsupportedOperationException("Use getCollections() with MongoDatabaseConnection")

    /**
     * Get collections (equivalent to tables in MongoDB).
     */
    suspend fun getCollections(
        connection: MongoDatabaseConnection,
        databaseName: String? = null,
    ): List<TableInfo> =
        withContext(Dispatchers.IO) {
            try {
                val db =
                    if (databaseName != null) {
                        connection.getDatabase(databaseName)
                    } else {
                        connection.database
                    }

                db.listCollectionNames().toList().map { name ->
                    TableInfo(
                        name = name,
                        schema = null,
                        catalog = db.name,
                        type = TableType.TABLE, // Collections are similar to tables
                        comment = null,
                    )
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to list MongoDB collections" }
                emptyList()
            }
        }

    override suspend fun getViews(
        connection: Connection,
        schema: String?,
        catalog: String?,
    ): List<ViewInfo> {
        // MongoDB views can be detected but we'll return empty for simplicity
        return emptyList()
    }

    override suspend fun getColumns(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): List<ColumnInfo> = throw UnsupportedOperationException("Use getFieldsMongo() with MongoDatabaseConnection")

    /**
     * Infer schema from sample documents in a collection.
     * MongoDB is schemaless, so we sample documents to infer field structure.
     */
    suspend fun getFieldsMongo(
        connection: MongoDatabaseConnection,
        collectionName: String,
        databaseName: String? = null,
        sampleSize: Int = 100,
    ): List<ColumnInfo> =
        withContext(Dispatchers.IO) {
            try {
                val db =
                    if (databaseName != null) {
                        connection.getDatabase(databaseName)
                    } else {
                        connection.database
                    }

                val collection = db.getCollection<Document>(collectionName)
                val fieldMap = mutableMapOf<String, MutableSet<String>>()

                // Sample documents to infer schema
                collection.find().limit(sampleSize).toList().forEach { doc ->
                    extractFields(doc, "", fieldMap)
                }

                // Convert to ColumnInfo
                fieldMap.entries.sortedBy { it.key }.mapIndexed { index, (fieldName, types) ->
                    val primaryType = types.firstOrNull() ?: "unknown"
                    ColumnInfo(
                        name = fieldName,
                        ordinalPosition = index + 1,
                        dataType = primaryType,
                        jdbcType = mapBsonTypeToJdbcType(primaryType),
                        nullable = true, // MongoDB fields are always nullable
                        size = null,
                        precision = null,
                        scale = null,
                        defaultValue = null,
                        autoIncrement = fieldName == "_id",
                        comment = if (types.size > 1) "Mixed types: ${types.joinToString(", ")}" else null,
                    )
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to infer MongoDB schema for $collectionName" }
                emptyList()
            }
        }

    private fun extractFields(
        doc: Document,
        prefix: String,
        fieldMap: MutableMap<String, MutableSet<String>>,
    ) {
        doc.forEach { (key, value) ->
            val fieldName = if (prefix.isEmpty()) key else "$prefix.$key"
            val typeName = getBsonTypeName(value)
            fieldMap.getOrPut(fieldName) { mutableSetOf() }.add(typeName)

            // Recurse into embedded documents
            if (value is Document) {
                extractFields(value, fieldName, fieldMap)
            }
        }
    }

    private fun getBsonTypeName(value: Any?): String =
        when (value) {
            null -> "null"
            is String -> "string"
            is Int, is Long -> "int"
            is Double, is Float -> "double"
            is Boolean -> "bool"
            is java.util.Date -> "date"
            is org.bson.types.ObjectId -> "objectId"
            is Document -> "object"
            is List<*> -> "array"
            is org.bson.types.Binary -> "binary"
            is org.bson.types.Decimal128 -> "decimal"
            else -> value::class.simpleName ?: "unknown"
        }

    private fun mapBsonTypeToJdbcType(bsonType: String): Int =
        when (bsonType) {
            "string" -> java.sql.Types.VARCHAR
            "int" -> java.sql.Types.BIGINT
            "double" -> java.sql.Types.DOUBLE
            "bool" -> java.sql.Types.BOOLEAN
            "date" -> java.sql.Types.TIMESTAMP
            "objectId" -> java.sql.Types.VARCHAR
            "object" -> java.sql.Types.OTHER
            "array" -> java.sql.Types.ARRAY
            "binary" -> java.sql.Types.BINARY
            "decimal" -> java.sql.Types.DECIMAL
            "null" -> java.sql.Types.NULL
            else -> java.sql.Types.OTHER
        }

    override suspend fun getPrimaryKey(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): PrimaryKeyInfo? {
        // MongoDB always uses _id as primary key
        return PrimaryKeyInfo(
            name = "_id",
            columns = listOf("_id"),
        )
    }

    override suspend fun getForeignKeys(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): List<ForeignKeyInfo> {
        // MongoDB doesn't have foreign keys
        return emptyList()
    }

    override suspend fun getIndexes(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): List<IndexInfo> = throw UnsupportedOperationException("Use getIndexesMongo() with MongoDatabaseConnection")

    suspend fun getIndexesMongo(
        connection: MongoDatabaseConnection,
        collectionName: String,
        databaseName: String? = null,
    ): List<IndexInfo> =
        withContext(Dispatchers.IO) {
            try {
                val db =
                    if (databaseName != null) {
                        connection.getDatabase(databaseName)
                    } else {
                        connection.database
                    }

                val collection = db.getCollection<Document>(collectionName)
                collection.listIndexes().toList().map { indexDoc ->
                    val name = indexDoc.getString("name") ?: "unknown"
                    val keyDoc = indexDoc.get("key", Document::class.java) ?: Document()
                    val columns = keyDoc.keys.toList()
                    val unique = indexDoc.getBoolean("unique", false)

                    IndexInfo(
                        name = name,
                        columns = columns,
                        unique = unique,
                        type = if (keyDoc.values.any { it == "text" }) IndexType.FULLTEXT else IndexType.BTREE,
                    )
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to list indexes for $collectionName" }
                emptyList()
            }
        }

    // ==================== DDL Generation ====================

    override suspend fun generateCreateTableDdl(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): String {
        // MongoDB uses db.createCollection() but it's optional
        return "db.createCollection(\"$table\")"
    }

    override suspend fun generateInsertStatement(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): String = "db.$table.insertOne({ /* document */ })"

    override suspend fun generateSelectStatement(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
        limit: Int,
    ): String = "db.$table.find().limit($limit)"

    override fun escapeIdentifier(identifier: String): String {
        // MongoDB field names don't need escaping in the same way
        return identifier
    }

    override fun getDefaultSchema(connection: Connection): String? = null

    override fun supportsSchemas(): Boolean = false

    override fun supportsCatalogs(): Boolean = true // Databases act like catalogs

    // ==================== Version & Capabilities ====================

    override suspend fun getVersion(connection: Connection): DatabaseVersion =
        throw UnsupportedOperationException("Use getVersionMongo() with MongoDatabaseConnection")

    suspend fun getVersionMongo(connection: MongoDatabaseConnection): DatabaseVersion =
        withContext(Dispatchers.IO) {
            cachedVersion?.let { return@withContext it }

            try {
                val result =
                    connection.client
                        .getDatabase("admin")
                        .runCommand(Document("buildInfo", 1))
                val versionString = result.getString("version") ?: "0.0.0"
                val version = DatabaseVersion.parseMongoDB(versionString)
                cachedVersion = version
                version
            } catch (e: Exception) {
                logger.error(e) { "Failed to get MongoDB version" }
                DatabaseVersion(0, 0, 0, "unknown", "MongoDB")
            }
        }

    override suspend fun getCapabilities(connection: Connection): DatabaseCapabilities =
        throw UnsupportedOperationException("Use getCapabilitiesMongo() with MongoDatabaseConnection")

    suspend fun getCapabilitiesMongo(connection: MongoDatabaseConnection): DatabaseCapabilities =
        withContext(Dispatchers.IO) {
            cachedCapabilities?.let { return@withContext it }

            val version = getVersionMongo(connection)
            val capabilities = DatabaseCapabilities.forMongoDB(version)
            cachedCapabilities = capabilities
            capabilities
        }

    override fun getOptimizationProperties(): Map<String, String> = emptyMap()

    // ==================== Statistics & Monitoring ====================

    override suspend fun getTableStatistics(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): TableStatistics? = throw UnsupportedOperationException("Use getCollectionStatsMongo() with MongoDatabaseConnection")

    suspend fun getCollectionStatsMongo(
        connection: MongoDatabaseConnection,
        collectionName: String,
        databaseName: String? = null,
    ): TableStatistics? =
        withContext(Dispatchers.IO) {
            try {
                val db =
                    if (databaseName != null) {
                        connection.getDatabase(databaseName)
                    } else {
                        connection.database
                    }

                val stats = db.runCommand(Document("collStats", collectionName))

                TableStatistics(
                    tableName = collectionName,
                    schema = null,
                    estimatedRowCount = stats.getInteger("count")?.toLong() ?: 0L,
                    totalSizeBytes = stats.getInteger("size")?.toLong(),
                    dataSizeBytes = stats.getInteger("storageSize")?.toLong(),
                    indexSizeBytes = stats.getInteger("totalIndexSize")?.toLong(),
                    lastVacuum = null,
                    lastAnalyze = null,
                    deadTuples = null,
                    autoIncrementValue = null,
                    tableCollation = null,
                )
            } catch (e: Exception) {
                logger.error(e) { "Failed to get collection stats for $collectionName" }
                null
            }
        }

    override suspend fun getIndexStatistics(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): List<IndexStatistics> = emptyList()

    override suspend fun getTablesWithStats(
        connection: Connection,
        schema: String?,
        catalog: String?,
    ): List<TableWithStats> = throw UnsupportedOperationException("Use getCollectionsWithStatsMongo() with MongoDatabaseConnection")

    suspend fun getCollectionsWithStatsMongo(
        connection: MongoDatabaseConnection,
        databaseName: String? = null,
    ): List<TableWithStats> =
        withContext(Dispatchers.IO) {
            try {
                val db =
                    if (databaseName != null) {
                        connection.getDatabase(databaseName)
                    } else {
                        connection.database
                    }

                db.listCollectionNames().toList().map { name ->
                    val stats =
                        try {
                            db.runCommand(Document("collStats", name))
                        } catch (e: Exception) {
                            null
                        }

                    TableWithStats(
                        name = name,
                        schema = null,
                        catalog = db.name,
                        type = "COLLECTION",
                        estimatedRowCount = stats?.getInteger("count")?.toLong(),
                        sizeBytes = stats?.getInteger("size")?.toLong(),
                        comment = null,
                    )
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to get collections with stats" }
                emptyList()
            }
        }

    override suspend fun getRunningQueries(connection: Connection): List<RunningQuery> =
        throw UnsupportedOperationException("Use getRunningQueriesMongo() with MongoDatabaseConnection")

    suspend fun getRunningQueriesMongo(connection: MongoDatabaseConnection): List<RunningQuery> =
        withContext(Dispatchers.IO) {
            try {
                val result =
                    connection.client
                        .getDatabase("admin")
                        .runCommand(Document("currentOp", 1))

                val inprog = result.getList("inprog", Document::class.java) ?: emptyList()

                inprog.mapNotNull { op ->
                    val opid = op.getInteger("opid") ?: return@mapNotNull null
                    val query = op.get("command", Document::class.java)?.toJson() ?: op.getString("op") ?: ""
                    val secs = op.getInteger("secs_running")?.toLong()?.times(1000)

                    RunningQuery(
                        pid = opid.toLong(),
                        database = op.getString("ns")?.substringBefore("."),
                        username = op.getString("client"),
                        applicationName = op.getString("appName"),
                        clientAddress = op.getString("client_s"),
                        state = QueryState.ACTIVE,
                        query = query,
                        startTime = null,
                        durationMs = secs,
                        waitEventType = op.getString("waitingForLock")?.let { "Lock" },
                        waitEvent = null,
                    )
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to get running queries" }
                emptyList()
            }
        }

    override suspend fun getServerStatus(connection: Connection): ServerStatus? =
        throw UnsupportedOperationException("Use getServerStatusMongo() with MongoDatabaseConnection")

    suspend fun getServerStatusMongo(connection: MongoDatabaseConnection): ServerStatus? =
        withContext(Dispatchers.IO) {
            try {
                val version = getVersionMongo(connection)
                val status =
                    connection.client
                        .getDatabase("admin")
                        .runCommand(Document("serverStatus", 1))

                val connections = status.get("connections", Document::class.java)
                val currentConnections = connections?.getInteger("current")
                val availableConnections = connections?.getInteger("available")

                val mem = status.get("mem", Document::class.java)
                val residentMem = mem?.getInteger("resident")?.toLong()?.times(1024 * 1024) // Convert MB to bytes

                val uptime = status.getDouble("uptime")?.toLong()

                val repl = status.get("repl", Document::class.java)
                val isReplica = repl != null
                val isSecondary = repl?.getBoolean("secondary") ?: false

                val additionalMetrics = mutableMapOf<String, Any>()
                status.getString("host")?.let { additionalMetrics["host"] = it }
                status.get("storageEngine")?.let {
                    (it as? Document)?.getString("name")?.let { name -> additionalMetrics["storageEngine"] = name }
                }
                repl?.getString("setName")?.let { additionalMetrics["replicaSet"] = it }

                ServerStatus(
                    version = version,
                    uptime = uptime,
                    activeConnections = currentConnections ?: 0,
                    maxConnections =
                        if (availableConnections != null &&
                            currentConnections != null
                        ) {
                            currentConnections + availableConnections
                        } else {
                            null
                        },
                    activeTransactions = null,
                    cacheHitRatio = null,
                    databaseSizeBytes = residentMem,
                    replicationLag = null,
                    isReplica = isReplica && isSecondary,
                    additionalMetrics = additionalMetrics,
                )
            } catch (e: Exception) {
                logger.error(e) { "Failed to get server status" }
                null
            }
        }

    // ==================== Query Execution ====================

    override suspend fun explainQuery(
        connection: Connection,
        query: String,
        analyze: Boolean,
        format: ExplainFormat,
    ): ExplainResult = throw UnsupportedOperationException("Use explainQueryMongo() with MongoDatabaseConnection")

    suspend fun explainQueryMongo(
        connection: MongoDatabaseConnection,
        collectionName: String,
        query: Document,
        verbosity: String = "executionStats",
    ): ExplainResult =
        withContext(Dispatchers.IO) {
            try {
                val db = connection.database
                val explainCmd =
                    Document().apply {
                        put(
                            "explain",
                            Document().apply {
                                put("find", collectionName)
                                put("filter", query)
                            },
                        )
                        put("verbosity", verbosity)
                    }

                val result = db.runCommand(explainCmd)
                val rawOutput = result.toJson()

                val executionStats = result.get("executionStats", Document::class.java)
                val executionTimeMs = executionStats?.getInteger("executionTimeMillis")?.toDouble()
                val totalDocsExamined = executionStats?.getInteger("totalDocsExamined")
                val totalKeysExamined = executionStats?.getInteger("totalKeysExamined")

                val warnings = mutableListOf<String>()
                if (totalDocsExamined != null && totalKeysExamined != null) {
                    if (totalDocsExamined > 0 && totalKeysExamined == 0) {
                        warnings.add("Collection scan detected - consider adding an index")
                    }
                }

                ExplainResult(
                    rawOutput = rawOutput,
                    format = ExplainFormat.JSON,
                    totalCost = null,
                    actualTime = executionTimeMs,
                    planningTime = null,
                    executionTime = executionTimeMs,
                    hasSeqScan = totalKeysExamined == 0 && (totalDocsExamined ?: 0) > 0,
                    hasIndexScan = (totalKeysExamined ?: 0) > 0,
                    hasSortOperation = rawOutput.contains("\"stage\" : \"SORT\""),
                    hasHashJoin = false,
                    hasNestedLoop = false,
                    warnings = warnings,
                )
            } catch (e: Exception) {
                logger.error(e) { "Failed to explain query" }
                ExplainResult(
                    rawOutput = "Error: ${e.message}",
                    format = ExplainFormat.TEXT,
                    totalCost = null,
                    actualTime = null,
                    planningTime = null,
                    executionTime = null,
                )
            }
        }

    override suspend fun getNativeCreateTableDdl(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): String = generateCreateTableDdl(connection, table, schema, catalog)
}
