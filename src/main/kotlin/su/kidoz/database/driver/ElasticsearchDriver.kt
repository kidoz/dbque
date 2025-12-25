package su.kidoz.database.driver

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.mapping.Property
import co.elastic.clients.elasticsearch.cat.IndicesResponse
import co.elastic.clients.elasticsearch.indices.GetMappingResponse
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.rest_client.RestClientTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.elasticsearch.client.RestClient
import su.kidoz.core.model.*
import su.kidoz.database.ElasticsearchDatabaseConnection
import su.kidoz.database.capabilities.*
import java.sql.Connection

/**
 * Elasticsearch database driver implementation.
 *
 * Elasticsearch is a distributed search and analytics engine.
 * This driver uses the official Elasticsearch Java client.
 */
class ElasticsearchDriver : DatabaseDriver {
    private val logger = KotlinLogging.logger {}

    override val type: DatabaseType = DatabaseType.ELASTICSEARCH

    private var cachedVersion: DatabaseVersion? = null
    private var cachedCapabilities: DatabaseCapabilities? = null

    // ==================== Connection Methods ====================

    /**
     * Elasticsearch doesn't use JDBC Connection. This method throws an exception.
     * Use connectElasticsearch() instead via ConnectionManager.
     */
    override suspend fun connect(config: ConnectionConfig): Connection =
        throw UnsupportedOperationException(
            "Elasticsearch doesn't use JDBC Connection. Use ConnectionManager.connect() which handles Elasticsearch specially.",
        )

    /**
     * Create an Elasticsearch connection.
     */
    suspend fun connectElasticsearch(config: ConnectionConfig): ElasticsearchDatabaseConnection =
        withContext(Dispatchers.IO) {
            logger.info { "Connecting to Elasticsearch: ${config.host}:${config.port}" }

            val useSsl = config.properties["ssl"] == "true"
            val scheme = if (useSsl) "https" else "http"
            val port = if (config.port > 0) config.port else 9200

            val restClientBuilder =
                RestClient.builder(
                    HttpHost(config.host, port, scheme),
                )

            // Add authentication if provided
            if (config.username.isNotBlank()) {
                val credentialsProvider = BasicCredentialsProvider()
                credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    UsernamePasswordCredentials(config.username, config.password),
                )
                restClientBuilder.setHttpClientConfigCallback { httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
                }
            }

            val restClient = restClientBuilder.build()
            val transport = RestClientTransport(restClient, JacksonJsonpMapper())
            val client = ElasticsearchClient(transport)

            ElasticsearchDatabaseConnection(config, client, restClient)
        }

    override suspend fun testConnection(config: ConnectionConfig): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = connectElasticsearch(config)
                try {
                    val info = connection.client.info()
                    val version = info.version().number()
                    val clusterName = info.clusterName()
                    "Elasticsearch $version (cluster: $clusterName)"
                } finally {
                    connection.close()
                }
            }
        }

    // ==================== Metadata Methods ====================

    override suspend fun getDatabases(connection: Connection): List<DatabaseInfo> =
        throw UnsupportedOperationException("Use getDatabasesElasticsearch() with ElasticsearchDatabaseConnection")

    /**
     * Get list of indices (Elasticsearch equivalent of databases/tables).
     */
    suspend fun getIndices(connection: ElasticsearchDatabaseConnection): List<DatabaseInfo> =
        withContext(Dispatchers.IO) {
            try {
                val response: IndicesResponse = connection.client.cat().indices()
                response.valueBody().map { index ->
                    DatabaseInfo(name = index.index() ?: "unknown")
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to list Elasticsearch indices" }
                emptyList()
            }
        }

    override suspend fun getSchemas(
        connection: Connection,
        database: String?,
    ): List<SchemaInfo> {
        // Elasticsearch doesn't have schemas
        return emptyList()
    }

    override suspend fun getTables(
        connection: Connection,
        schema: String?,
        catalog: String?,
    ): List<TableInfo> = throw UnsupportedOperationException("Use getIndicesAsTableInfo() with ElasticsearchDatabaseConnection")

    /**
     * Get indices as TableInfo for compatibility with the explorer.
     */
    suspend fun getIndicesAsTableInfo(connection: ElasticsearchDatabaseConnection): List<TableInfo> =
        withContext(Dispatchers.IO) {
            try {
                val response = connection.client.cat().indices()
                response.valueBody().map { index ->
                    TableInfo(
                        name = index.index() ?: "unknown",
                        schema = null,
                        catalog = null,
                        type = TableType.TABLE,
                        comment = "Docs: ${index.docsCount() ?: 0}, Size: ${index.storeSize() ?: "unknown"}",
                    )
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to list Elasticsearch indices" }
                emptyList()
            }
        }

    override suspend fun getViews(
        connection: Connection,
        schema: String?,
        catalog: String?,
    ): List<ViewInfo> {
        // Elasticsearch doesn't have views
        return emptyList()
    }

    override suspend fun getColumns(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): List<ColumnInfo> = throw UnsupportedOperationException("Use getFieldsElasticsearch() with ElasticsearchDatabaseConnection")

    /**
     * Get field mappings for an index (equivalent to columns).
     */
    suspend fun getFieldsElasticsearch(
        connection: ElasticsearchDatabaseConnection,
        indexName: String,
    ): List<ColumnInfo> =
        withContext(Dispatchers.IO) {
            try {
                val response: GetMappingResponse = connection.client.indices().getMapping { it.index(indexName) }
                val mappings = response.result()

                val fields = mutableListOf<ColumnInfo>()
                var ordinal = 1

                mappings[indexName]?.mappings()?.properties()?.forEach { (fieldName, property) ->
                    fields.add(propertyToColumnInfo(fieldName, property, ordinal++))
                    // Handle nested properties
                    extractNestedFields(fieldName, property, fields, ordinal)
                }

                fields
            } catch (e: Exception) {
                logger.error(e) { "Failed to get Elasticsearch mappings for $indexName" }
                emptyList()
            }
        }

    private fun propertyToColumnInfo(
        name: String,
        property: Property,
        ordinal: Int,
    ): ColumnInfo {
        val typeName = property._kind().jsonValue()
        return ColumnInfo(
            name = name,
            ordinalPosition = ordinal,
            dataType = typeName,
            jdbcType = mapElasticsearchTypeToJdbc(typeName),
            nullable = true,
            size = null,
            precision = null,
            scale = null,
            defaultValue = null,
            autoIncrement = false,
            comment = null,
        )
    }

    private fun extractNestedFields(
        prefix: String,
        property: Property,
        fields: MutableList<ColumnInfo>,
        startOrdinal: Int,
    ): Int {
        var ordinal = startOrdinal
        when {
            property.isObject() -> {
                property.`object`().properties()?.forEach { (nestedName, nestedProp) ->
                    val fullName = "$prefix.$nestedName"
                    fields.add(propertyToColumnInfo(fullName, nestedProp, ordinal++))
                    ordinal = extractNestedFields(fullName, nestedProp, fields, ordinal)
                }
            }
            property.isNested() -> {
                property.nested().properties()?.forEach { (nestedName, nestedProp) ->
                    val fullName = "$prefix.$nestedName"
                    fields.add(propertyToColumnInfo(fullName, nestedProp, ordinal++))
                    ordinal = extractNestedFields(fullName, nestedProp, fields, ordinal)
                }
            }
        }
        return ordinal
    }

    private fun mapElasticsearchTypeToJdbc(esType: String): Int =
        when (esType) {
            "text", "keyword" -> java.sql.Types.VARCHAR
            "long" -> java.sql.Types.BIGINT
            "integer" -> java.sql.Types.INTEGER
            "short" -> java.sql.Types.SMALLINT
            "byte" -> java.sql.Types.TINYINT
            "double" -> java.sql.Types.DOUBLE
            "float", "half_float" -> java.sql.Types.FLOAT
            "scaled_float" -> java.sql.Types.DECIMAL
            "boolean" -> java.sql.Types.BOOLEAN
            "date" -> java.sql.Types.TIMESTAMP
            "binary" -> java.sql.Types.BINARY
            "object", "nested" -> java.sql.Types.OTHER
            "geo_point", "geo_shape" -> java.sql.Types.OTHER
            else -> java.sql.Types.OTHER
        }

    override suspend fun getPrimaryKey(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): PrimaryKeyInfo? {
        // Elasticsearch uses _id as the document identifier
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
        // Elasticsearch doesn't have foreign keys
        return emptyList()
    }

    override suspend fun getIndexes(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): List<IndexInfo> {
        // In Elasticsearch, an "index" is the table itself
        // We can return info about the index's settings
        return emptyList()
    }

    // ==================== DDL Generation ====================

    override suspend fun generateCreateTableDdl(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): String =
        """
        PUT /$table
        {
          "settings": {
            "number_of_shards": 1,
            "number_of_replicas": 0
          },
          "mappings": {
            "properties": {
              // Define your fields here
            }
          }
        }
        """.trimIndent()

    override suspend fun generateInsertStatement(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): String =
        """
        POST /$table/_doc
        {
          // Document content here
        }
        """.trimIndent()

    override suspend fun generateSelectStatement(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
        limit: Int,
    ): String =
        """
        GET /$table/_search
        {
          "size": $limit,
          "query": {
            "match_all": {}
          }
        }
        """.trimIndent()

    override fun escapeIdentifier(identifier: String): String = identifier

    override fun getDefaultSchema(connection: Connection): String? = null

    override fun supportsSchemas(): Boolean = false

    override fun supportsCatalogs(): Boolean = false

    // ==================== Version & Capabilities ====================

    override suspend fun getVersion(connection: Connection): DatabaseVersion =
        throw UnsupportedOperationException("Use getVersionElasticsearch() with ElasticsearchDatabaseConnection")

    suspend fun getVersionElasticsearch(connection: ElasticsearchDatabaseConnection): DatabaseVersion =
        withContext(Dispatchers.IO) {
            cachedVersion?.let { return@withContext it }

            try {
                val info = connection.client.info()
                val versionString = info.version().number()
                val version = DatabaseVersion.parseElasticsearch(versionString)
                cachedVersion = version
                version
            } catch (e: Exception) {
                logger.error(e) { "Failed to get Elasticsearch version" }
                DatabaseVersion(0, 0, 0, "unknown", "Elasticsearch")
            }
        }

    override suspend fun getCapabilities(connection: Connection): DatabaseCapabilities =
        throw UnsupportedOperationException("Use getCapabilitiesElasticsearch() with ElasticsearchDatabaseConnection")

    suspend fun getCapabilitiesElasticsearch(connection: ElasticsearchDatabaseConnection): DatabaseCapabilities =
        withContext(Dispatchers.IO) {
            cachedCapabilities?.let { return@withContext it }

            val version = getVersionElasticsearch(connection)
            val capabilities = DatabaseCapabilities.forElasticsearch(version)
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
    ): TableStatistics? = throw UnsupportedOperationException("Use getIndexStatisticsElasticsearch() with ElasticsearchDatabaseConnection")

    suspend fun getIndexStatisticsElasticsearch(
        connection: ElasticsearchDatabaseConnection,
        indexName: String,
    ): TableStatistics? =
        withContext(Dispatchers.IO) {
            try {
                val stats = connection.client.indices().stats { it.index(indexName) }
                val indexStats = stats.indices()[indexName]?.primaries()

                TableStatistics(
                    tableName = indexName,
                    schema = null,
                    estimatedRowCount = indexStats?.docs()?.count() ?: 0L,
                    totalSizeBytes = indexStats?.store()?.sizeInBytes(),
                    dataSizeBytes = indexStats?.store()?.sizeInBytes(),
                    indexSizeBytes = null,
                    lastVacuum = null,
                    lastAnalyze = null,
                    deadTuples = indexStats?.docs()?.deleted(),
                    autoIncrementValue = null,
                    tableCollation = null,
                )
            } catch (e: Exception) {
                logger.error(e) { "Failed to get index stats for $indexName" }
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
    ): List<TableWithStats> =
        throw UnsupportedOperationException("Use getIndicesWithStatsElasticsearch() with ElasticsearchDatabaseConnection")

    suspend fun getIndicesWithStatsElasticsearch(connection: ElasticsearchDatabaseConnection): List<TableWithStats> =
        withContext(Dispatchers.IO) {
            try {
                val response = connection.client.cat().indices()
                response.valueBody().map { index ->
                    TableWithStats(
                        name = index.index() ?: "unknown",
                        schema = null,
                        catalog = null,
                        type = "INDEX",
                        estimatedRowCount = index.docsCount()?.toLong(),
                        sizeBytes = parseSize(index.storeSize()),
                        comment = "Health: ${index.health()}, Status: ${index.status()}",
                    )
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to get indices with stats" }
                emptyList()
            }
        }

    private fun parseSize(sizeStr: String?): Long? {
        if (sizeStr == null) return null
        return try {
            val value = sizeStr.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: return null
            val multiplier =
                when {
                    sizeStr.contains("kb", ignoreCase = true) -> 1024L
                    sizeStr.contains("mb", ignoreCase = true) -> 1024L * 1024
                    sizeStr.contains("gb", ignoreCase = true) -> 1024L * 1024 * 1024
                    sizeStr.contains("tb", ignoreCase = true) -> 1024L * 1024 * 1024 * 1024
                    else -> 1L
                }
            (value * multiplier).toLong()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getRunningQueries(connection: Connection): List<RunningQuery> =
        throw UnsupportedOperationException("Use getRunningQueriesElasticsearch() with ElasticsearchDatabaseConnection")

    suspend fun getRunningQueriesElasticsearch(connection: ElasticsearchDatabaseConnection): List<RunningQuery> =
        withContext(Dispatchers.IO) {
            try {
                val tasks = connection.client.tasks().list()
                val runningQueries = mutableListOf<RunningQuery>()

                // Iterate through nodes and their tasks
                tasks.nodes().forEach { (nodeId, nodeInfo) ->
                    nodeInfo.tasks().forEach { (taskId, taskInfo) ->
                        runningQueries.add(
                            RunningQuery(
                                pid = taskId.toLongOrNull() ?: 0L,
                                database = null,
                                username = null,
                                applicationName = nodeId,
                                clientAddress = null,
                                state = if (taskInfo.cancellable()) QueryState.ACTIVE else QueryState.IDLE,
                                query = "${taskInfo.action()} - ${taskInfo.description() ?: ""}",
                                startTime = taskInfo.startTimeInMillis(),
                                durationMs = taskInfo.runningTimeInNanos() / 1_000_000,
                                waitEventType = null,
                                waitEvent = null,
                            ),
                        )
                    }
                }

                runningQueries
            } catch (e: Exception) {
                logger.error(e) { "Failed to get running tasks" }
                emptyList()
            }
        }

    override suspend fun getServerStatus(connection: Connection): ServerStatus? =
        throw UnsupportedOperationException("Use getServerStatusElasticsearch() with ElasticsearchDatabaseConnection")

    suspend fun getServerStatusElasticsearch(connection: ElasticsearchDatabaseConnection): ServerStatus? =
        withContext(Dispatchers.IO) {
            try {
                val version = getVersionElasticsearch(connection)
                val health = connection.client.cluster().health()
                val stats = connection.client.cluster().stats()

                val additionalMetrics = mutableMapOf<String, Any>()
                additionalMetrics["cluster_name"] = health.clusterName()
                additionalMetrics["status"] = health.status().jsonValue()
                additionalMetrics["number_of_nodes"] = health.numberOfNodes()
                additionalMetrics["number_of_data_nodes"] = health.numberOfDataNodes()
                additionalMetrics["active_shards"] = health.activeShards()

                ServerStatus(
                    version = version,
                    uptime = null,
                    activeConnections = health.numberOfNodes(),
                    maxConnections = null,
                    activeTransactions = health.activePrimaryShards(),
                    cacheHitRatio = null,
                    databaseSizeBytes = stats.indices().store().sizeInBytes(),
                    replicationLag = null,
                    isReplica = false,
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
    ): ExplainResult = throw UnsupportedOperationException("Use explainQueryElasticsearch() with ElasticsearchDatabaseConnection")

    suspend fun explainQueryElasticsearch(
        connection: ElasticsearchDatabaseConnection,
        indexName: String,
        query: String,
    ): ExplainResult =
        withContext(Dispatchers.IO) {
            try {
                // Elasticsearch validate query API can be used for explain
                val validateResponse =
                    connection.client.indices().validateQuery { builder ->
                        builder
                            .index(indexName)
                            .explain(true)
                            .q(query)
                    }

                val rawOutput =
                    buildString {
                        appendLine("Valid: ${validateResponse.valid()}")
                        validateResponse.explanations()?.forEach { explanation ->
                            appendLine("Index: ${explanation.index()}")
                            appendLine("Valid: ${explanation.valid()}")
                            explanation.explanation()?.let { appendLine("Explanation: $it") }
                            explanation.error()?.let { appendLine("Error: $it") }
                        }
                    }

                ExplainResult(
                    rawOutput = rawOutput,
                    format = ExplainFormat.TEXT,
                    totalCost = null,
                    actualTime = null,
                    planningTime = null,
                    executionTime = null,
                    hasSeqScan = false,
                    hasIndexScan = true,
                    hasSortOperation = query.contains("sort", ignoreCase = true),
                    hasHashJoin = false,
                    hasNestedLoop = false,
                    warnings = if (validateResponse.valid()) emptyList() else listOf("Query validation failed"),
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
    ): String = throw UnsupportedOperationException("Use getIndexMappingElasticsearch() with ElasticsearchDatabaseConnection")

    suspend fun getIndexMappingElasticsearch(
        connection: ElasticsearchDatabaseConnection,
        indexName: String,
    ): String =
        withContext(Dispatchers.IO) {
            try {
                val mapping = connection.client.indices().getMapping { it.index(indexName) }
                val indexMapping = mapping.result()[indexName]

                buildString {
                    appendLine("PUT /$indexName")
                    appendLine("{")
                    appendLine("  \"mappings\": {")
                    appendLine("    \"properties\": {")
                    indexMapping?.mappings()?.properties()?.entries?.forEachIndexed { idx, (name, prop) ->
                        val comma = if (idx < (indexMapping.mappings()?.properties()?.size ?: 0) - 1) "," else ""
                        appendLine("      \"$name\": { \"type\": \"${prop._kind().jsonValue()}\" }$comma")
                    }
                    appendLine("    }")
                    appendLine("  }")
                    appendLine("}")
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to get index mapping for $indexName" }
                generateCreateTableDdl(connection as Connection, indexName, null, null)
            }
        }
}
