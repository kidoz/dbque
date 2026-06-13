package su.kidoz.database.driver

import su.kidoz.core.model.*
import su.kidoz.database.capabilities.*
import java.sql.Connection

interface DatabaseDriver {
    val type: DatabaseType

    suspend fun connect(config: ConnectionConfig): Connection

    suspend fun testConnection(config: ConnectionConfig): Result<String>

    suspend fun getDatabases(connection: Connection): List<DatabaseInfo>

    suspend fun getSchemas(
        connection: Connection,
        database: String? = null,
    ): List<SchemaInfo>

    suspend fun getTables(
        connection: Connection,
        schema: String? = null,
        catalog: String? = null,
    ): List<TableInfo>

    suspend fun getViews(
        connection: Connection,
        schema: String? = null,
        catalog: String? = null,
    ): List<ViewInfo>

    suspend fun getColumns(
        connection: Connection,
        table: String,
        schema: String? = null,
        catalog: String? = null,
    ): List<ColumnInfo>

    suspend fun getPrimaryKey(
        connection: Connection,
        table: String,
        schema: String? = null,
        catalog: String? = null,
    ): PrimaryKeyInfo?

    suspend fun getForeignKeys(
        connection: Connection,
        table: String,
        schema: String? = null,
        catalog: String? = null,
    ): List<ForeignKeyInfo>

    /**
     * Get database-specific SQL keywords.
     */
    suspend fun getKeywords(connection: Connection): List<String> = emptyList()

    /**
     * Get database-specific functions.
     */
    suspend fun getFunctions(connection: Connection): List<String> = emptyList()

    suspend fun getIndexes(
        connection: Connection,
        table: String,
        schema: String? = null,
        catalog: String? = null,
    ): List<IndexInfo>

    suspend fun generateCreateTableDdl(
        connection: Connection,
        table: String,
        schema: String? = null,
        catalog: String? = null,
    ): String

    suspend fun generateInsertStatement(
        connection: Connection,
        table: String,
        schema: String? = null,
        catalog: String? = null,
    ): String

    suspend fun generateSelectStatement(
        connection: Connection,
        table: String,
        schema: String? = null,
        catalog: String? = null,
        limit: Int = 100,
    ): String

    fun escapeIdentifier(identifier: String): String

    fun getDefaultSchema(connection: Connection): String?

    fun supportsSchemas(): Boolean = true

    fun supportsCatalogs(): Boolean = true

    // ==================== Version & Capabilities ====================

    /**
     * Get database version information.
     */
    suspend fun getVersion(connection: Connection): DatabaseVersion

    /**
     * Get database capabilities based on version.
     */
    suspend fun getCapabilities(connection: Connection): DatabaseCapabilities

    /**
     * Get recommended connection properties for optimization.
     */
    fun getOptimizationProperties(): Map<String, String> = emptyMap()

    // ==================== Statistics & Monitoring ====================

    /**
     * Get table statistics (row count, size, etc.) using native queries.
     * More efficient than COUNT(*) for large tables.
     */
    suspend fun getTableStatistics(
        connection: Connection,
        table: String,
        schema: String? = null,
        catalog: String? = null,
    ): TableStatistics?

    /**
     * Get index statistics for performance analysis.
     */
    suspend fun getIndexStatistics(
        connection: Connection,
        table: String,
        schema: String? = null,
        catalog: String? = null,
    ): List<IndexStatistics>

    /**
     * Get tables with basic statistics in a single efficient query.
     */
    suspend fun getTablesWithStats(
        connection: Connection,
        schema: String? = null,
        catalog: String? = null,
    ): List<TableWithStats>

    /**
     * Get currently running queries.
     */
    suspend fun getRunningQueries(connection: Connection): List<RunningQuery>

    /**
     * Get server status information.
     */
    suspend fun getServerStatus(connection: Connection): ServerStatus?

    // ==================== Enhanced EXPLAIN ====================

    /**
     * Execute EXPLAIN with format control.
     * @param analyze If true, actually executes the query (PostgreSQL, MySQL 8.0+)
     * @param format Preferred output format (not all DBs support all formats)
     */
    suspend fun explainQuery(
        connection: Connection,
        query: String,
        analyze: Boolean = false,
        format: ExplainFormat = ExplainFormat.JSON,
    ): ExplainResult

    // ==================== Native DDL ====================

    /**
     * Get native CREATE TABLE DDL if available (e.g., SHOW CREATE TABLE for MySQL).
     * Falls back to generated DDL if not supported.
     */
    suspend fun getNativeCreateTableDdl(
        connection: Connection,
        table: String,
        schema: String? = null,
        catalog: String? = null,
    ): String
}
