package su.kidoz.database.capabilities

/**
 * Table statistics for performance insights.
 */
data class TableStatistics(
    val tableName: String,
    val schema: String?,
    val estimatedRowCount: Long,
    val totalSizeBytes: Long?,
    val dataSizeBytes: Long?,
    val indexSizeBytes: Long?,
    val lastVacuum: Long?, // PostgreSQL
    val lastAnalyze: Long?, // PostgreSQL
    val deadTuples: Long?, // PostgreSQL - bloat indicator
    val autoIncrementValue: Long?, // MySQL
    val tableCollation: String?, // MySQL
) {
    val totalSizeMB: Double?
        get() = totalSizeBytes?.let { it / 1024.0 / 1024.0 }

    val dataSizeMB: Double?
        get() = dataSizeBytes?.let { it / 1024.0 / 1024.0 }

    val indexSizeMB: Double?
        get() = indexSizeBytes?.let { it / 1024.0 / 1024.0 }

    val bloatPercentage: Double?
        get() =
            deadTuples?.let { dead ->
                if (estimatedRowCount > 0) {
                    (dead.toDouble() / (estimatedRowCount + dead)) * 100
                } else {
                    null
                }
            }
}

/**
 * Index statistics for performance analysis.
 */
data class IndexStatistics(
    val indexName: String,
    val tableName: String,
    val schema: String?,
    val sizeBytes: Long?,
    val indexScans: Long?, // Number of times index was used
    val tuplesRead: Long?, // Rows read via index
    val tuplesFetched: Long?, // Rows fetched from table
    val isUnused: Boolean = false,
    val lastUsed: Long? = null,
) {
    val sizeMB: Double?
        get() = sizeBytes?.let { it / 1024.0 / 1024.0 }
}

/**
 * Running query information for monitoring.
 */
data class RunningQuery(
    val pid: Long,
    val database: String?,
    val username: String?,
    val applicationName: String?,
    val clientAddress: String?,
    val state: QueryState,
    val query: String?,
    val startTime: Long?,
    val durationMs: Long?,
    val waitEventType: String?,
    val waitEvent: String?,
    val blockingPid: Long? = null,
)

enum class QueryState {
    ACTIVE,
    IDLE,
    IDLE_IN_TRANSACTION,
    WAITING,
    DISABLED,
    UNKNOWN,
}

/**
 * Server status for monitoring dashboard.
 */
data class ServerStatus(
    val version: DatabaseVersion,
    val uptime: Long?, // Seconds
    val activeConnections: Int,
    val maxConnections: Int?,
    val activeTransactions: Int?,
    val cacheHitRatio: Double?, // 0.0 - 1.0
    val databaseSizeBytes: Long?,
    val replicationLag: Long?, // Bytes or ms depending on DB
    val isReplica: Boolean = false,
    val additionalMetrics: Map<String, Any> = emptyMap(),
) {
    val databaseSizeMB: Double?
        get() = databaseSizeBytes?.let { it / 1024.0 / 1024.0 }

    val databaseSizeGB: Double?
        get() = databaseSizeBytes?.let { it / 1024.0 / 1024.0 / 1024.0 }

    val cacheHitPercentage: Double?
        get() = cacheHitRatio?.let { it * 100 }
}

/**
 * EXPLAIN result with parsed information.
 */
data class ExplainResult(
    val rawOutput: String,
    val format: ExplainFormat,
    val totalCost: Double?,
    val actualTime: Double?, // Only with ANALYZE
    val planningTime: Double?,
    val executionTime: Double?,
    val hasSeqScan: Boolean = false,
    val hasIndexScan: Boolean = false,
    val hasSortOperation: Boolean = false,
    val hasHashJoin: Boolean = false,
    val hasNestedLoop: Boolean = false,
    val warnings: List<String> = emptyList(),
)

enum class ExplainFormat {
    TEXT,
    JSON,
    XML,
    YAML,
    TREE, // MySQL 8.0+ tree format
}

/**
 * Table with statistics for efficient listing.
 */
data class TableWithStats(
    val name: String,
    val schema: String?,
    val catalog: String?,
    val type: String,
    val estimatedRowCount: Long?,
    val sizeBytes: Long?,
    val comment: String?,
)

/**
 * Connection optimization properties per database.
 */
object ConnectionOptimizations {
    fun forPostgres(): Map<String, String> =
        mapOf(
            "ApplicationName" to "DBQue",
            "reWriteBatchedInserts" to "true",
            "prepareThreshold" to "5",
            "defaultRowFetchSize" to "1000",
            "socketTimeout" to "60",
            "connectTimeout" to "30",
            "tcpKeepAlive" to "true",
        )

    fun forMySql(): Map<String, String> =
        mapOf(
            "useServerPrepStmts" to "true",
            "cachePrepStmts" to "true",
            "prepStmtCacheSize" to "250",
            "prepStmtCacheSqlLimit" to "2048",
            "rewriteBatchedStatements" to "true",
            "useCompression" to "false",
            "connectionAttributes" to "program_name:DBQue",
            "connectTimeout" to "30000",
            "socketTimeout" to "60000",
            "useSSL" to "false",
            "allowPublicKeyRetrieval" to "true",
        )

    fun forSqlite(): Map<String, String> =
        mapOf(
            // SQLite pragmas are set via connection, not properties
        )

    fun forH2(): Map<String, String> =
        mapOf(
            "DB_CLOSE_DELAY" to "-1",
            "DATABASE_TO_UPPER" to "false",
        )

    /**
     * SQLite PRAGMA settings for optimization.
     */
    val sqlitePragmas: Map<String, String> =
        mapOf(
            "journal_mode" to "WAL",
            "synchronous" to "NORMAL",
            "cache_size" to "-64000", // 64MB
            "temp_store" to "MEMORY",
            "mmap_size" to "268435456", // 256MB
            "foreign_keys" to "ON",
        )
}
