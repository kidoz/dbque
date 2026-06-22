package su.kidoz.database.capabilities

import su.kidoz.core.model.DatabaseType

/**
 * Describes the capabilities of a specific database connection,
 * including version-specific features and optimizations.
 */
data class DatabaseCapabilities(
    val type: DatabaseType,
    val version: DatabaseVersion,
    // SQL Features
    val supportsExplainAnalyze: Boolean = false,
    val supportsJsonExplain: Boolean = false,
    val supportsWindowFunctions: Boolean = false,
    val supportsCTEs: Boolean = false,
    val supportsMerge: Boolean = false,
    val supportsUpsert: Boolean = false,
    val supportsReturning: Boolean = false,
    val supportsLateralJoin: Boolean = false,
    val supportsFullOuterJoin: Boolean = true,
    // Schema Features
    val supportsPartitioning: Boolean = false,
    val supportsInvisibleIndexes: Boolean = false,
    val supportsCheckConstraints: Boolean = true,
    val supportsGeneratedColumns: Boolean = false,
    val supportsTableSampling: Boolean = false,
    val supportsStoredProcedures: Boolean = false,
    val supportsRoles: Boolean = false,
    // Monitoring
    val hasSystemSchema: Boolean = false,
    val hasStatisticsViews: Boolean = false,
    val hasQueryStats: Boolean = false,
    val supportsProgressMonitoring: Boolean = false,
    // Syntax
    val identifierQuoteChar: Char = '"',
    val maxIdentifierLength: Int = 128,
    val defaultSchema: String = "",
    val stringConcatOperator: String = "||",
    val limitSyntax: LimitSyntax = LimitSyntax.LIMIT,
    // Optimization hints
    val recommendedFetchSize: Int = 1000,
    val supportsBatchRewrite: Boolean = false,
    val supportsServerPreparedStatements: Boolean = true,
    // H2 specific
    val compatibilityMode: H2CompatibilityMode? = null,
) {
    companion object {
        fun forPostgres(version: DatabaseVersion): DatabaseCapabilities =
            DatabaseCapabilities(
                type = DatabaseType.POSTGRESQL,
                version = version,
                // SQL Features
                supportsExplainAnalyze = true,
                supportsJsonExplain = true,
                supportsWindowFunctions = true,
                supportsCTEs = true,
                supportsMerge = version.isAtLeast(15),
                supportsUpsert = version.isAtLeast(9, 5),
                supportsReturning = true,
                supportsLateralJoin = version.isAtLeast(9, 3),
                supportsFullOuterJoin = true,
                // Schema Features
                supportsPartitioning = version.isAtLeast(10),
                supportsInvisibleIndexes = false,
                supportsCheckConstraints = true,
                supportsGeneratedColumns = version.isAtLeast(12),
                supportsTableSampling = version.isAtLeast(9, 5),
                supportsStoredProcedures = version.isAtLeast(11),
                supportsRoles = true,
                // Monitoring
                hasSystemSchema = true, // pg_catalog
                hasStatisticsViews = true, // pg_stat_*
                hasQueryStats = true, // pg_stat_statements
                supportsProgressMonitoring = version.isAtLeast(10),
                // Syntax
                identifierQuoteChar = '"',
                maxIdentifierLength = 63,
                defaultSchema = "public",
                stringConcatOperator = "||",
                limitSyntax = LimitSyntax.LIMIT,
                // Optimization
                recommendedFetchSize = 1000,
                supportsBatchRewrite = true,
                supportsServerPreparedStatements = true,
            )

        fun forMySql(version: DatabaseVersion): DatabaseCapabilities {
            val is8OrHigher = version.isAtLeast(8)
            return DatabaseCapabilities(
                type = DatabaseType.MYSQL,
                version = version,
                // SQL Features
                supportsExplainAnalyze = is8OrHigher,
                supportsJsonExplain = version.isAtLeast(5, 7),
                supportsWindowFunctions = is8OrHigher,
                supportsCTEs = is8OrHigher,
                supportsMerge = false,
                supportsUpsert = true, // ON DUPLICATE KEY UPDATE
                supportsReturning = false,
                supportsLateralJoin = is8OrHigher,
                supportsFullOuterJoin = false, // MySQL doesn't support FULL OUTER JOIN
                // Schema Features
                supportsPartitioning = true,
                supportsInvisibleIndexes = is8OrHigher,
                supportsCheckConstraints = version.isAtLeast(8, 0, 16),
                supportsGeneratedColumns = version.isAtLeast(5, 7),
                supportsTableSampling = false,
                supportsStoredProcedures = true,
                supportsRoles = is8OrHigher,
                // Monitoring
                hasSystemSchema = version.isAtLeast(5, 7), // sys schema
                hasStatisticsViews = version.isAtLeast(5, 7),
                hasQueryStats = version.isAtLeast(5, 7), // performance_schema
                supportsProgressMonitoring = false,
                // Syntax
                identifierQuoteChar = '`',
                maxIdentifierLength = 64,
                defaultSchema = "",
                stringConcatOperator = "", // Uses CONCAT function
                limitSyntax = LimitSyntax.LIMIT,
                // Optimization
                recommendedFetchSize = 1000,
                supportsBatchRewrite = true,
                supportsServerPreparedStatements = true,
            )
        }

        fun forStarRocks(version: DatabaseVersion): DatabaseCapabilities =
            DatabaseCapabilities(
                type = DatabaseType.STARROCKS,
                version = version,
                // SQL Features
                supportsExplainAnalyze = true, // EXPLAIN ANALYZE
                supportsJsonExplain = false, // no EXPLAIN FORMAT=JSON; uses EXPLAIN COSTS/VERBOSE
                supportsWindowFunctions = true,
                supportsCTEs = true, // recursive CTEs added in 4.1
                supportsMerge = false,
                supportsUpsert = false, // updates go through Primary Key tables, not ON DUPLICATE KEY
                supportsReturning = false,
                supportsLateralJoin = false,
                supportsFullOuterJoin = true,
                // Schema Features
                supportsPartitioning = true, // range/list/expression partitioning
                supportsInvisibleIndexes = false,
                supportsCheckConstraints = false, // not enforced
                supportsGeneratedColumns = true, // generated columns
                supportsTableSampling = false,
                supportsStoredProcedures = false,
                supportsRoles = true, // RBAC
                // Monitoring
                hasSystemSchema = true, // information_schema
                hasStatisticsViews = true,
                hasQueryStats = true, // query profile / audit
                supportsProgressMonitoring = false,
                // Syntax
                identifierQuoteChar = '`',
                maxIdentifierLength = 64,
                defaultSchema = "",
                stringConcatOperator = "", // uses CONCAT function
                limitSyntax = LimitSyntax.LIMIT,
                // Optimization
                recommendedFetchSize = 1000,
                supportsBatchRewrite = true,
                supportsServerPreparedStatements = false,
            )

        fun forSqlite(version: DatabaseVersion): DatabaseCapabilities =
            DatabaseCapabilities(
                type = DatabaseType.SQLITE,
                version = version,
                // SQL Features
                supportsExplainAnalyze = false, // EXPLAIN QUERY PLAN only
                supportsJsonExplain = false,
                supportsWindowFunctions = version.isAtLeast(3, 25),
                supportsCTEs = version.isAtLeast(3, 8, 3),
                supportsMerge = false,
                supportsUpsert = version.isAtLeast(3, 24),
                supportsReturning = version.isAtLeast(3, 35),
                supportsLateralJoin = false,
                supportsFullOuterJoin = version.isAtLeast(3, 39),
                // Schema Features
                supportsPartitioning = false,
                supportsInvisibleIndexes = false,
                supportsCheckConstraints = true,
                supportsGeneratedColumns = version.isAtLeast(3, 31),
                supportsTableSampling = false,
                supportsStoredProcedures = false,
                supportsRoles = false,
                // Monitoring
                hasSystemSchema = true, // sqlite_master
                hasStatisticsViews = true, // sqlite_stat1
                hasQueryStats = false,
                supportsProgressMonitoring = false,
                // Syntax
                identifierQuoteChar = '"',
                maxIdentifierLength = Int.MAX_VALUE,
                defaultSchema = "main",
                stringConcatOperator = "||",
                limitSyntax = LimitSyntax.LIMIT,
                // Optimization
                recommendedFetchSize = 1000,
                supportsBatchRewrite = false,
                supportsServerPreparedStatements = false,
            )

        fun forH2(
            version: DatabaseVersion,
            mode: H2CompatibilityMode?,
        ): DatabaseCapabilities =
            DatabaseCapabilities(
                type = DatabaseType.H2,
                version = version,
                // SQL Features
                supportsExplainAnalyze = true,
                supportsJsonExplain = false,
                supportsWindowFunctions = true,
                supportsCTEs = true,
                supportsMerge = true,
                supportsUpsert = true,
                supportsReturning = false,
                supportsLateralJoin = true,
                supportsFullOuterJoin = true,
                // Schema Features
                supportsPartitioning = false,
                supportsInvisibleIndexes = false,
                supportsCheckConstraints = true,
                supportsGeneratedColumns = true,
                supportsTableSampling = false,
                supportsStoredProcedures = true,
                supportsRoles = true,
                // Monitoring
                hasSystemSchema = true, // INFORMATION_SCHEMA
                hasStatisticsViews = true,
                hasQueryStats = false,
                supportsProgressMonitoring = false,
                // Syntax - depends on compatibility mode
                identifierQuoteChar =
                    when (mode) {
                        H2CompatibilityMode.MySQL -> '`'
                        else -> '"'
                    },
                maxIdentifierLength = 256,
                defaultSchema = "PUBLIC",
                stringConcatOperator =
                    when (mode) {
                        H2CompatibilityMode.MySQL -> ""
                        else -> "||"
                    },
                limitSyntax =
                    when (mode) {
                        H2CompatibilityMode.MSSQLServer -> LimitSyntax.TOP
                        H2CompatibilityMode.Oracle -> LimitSyntax.ROWNUM
                        else -> LimitSyntax.LIMIT
                    },
                // Optimization
                recommendedFetchSize = 1000,
                supportsBatchRewrite = true,
                supportsServerPreparedStatements = true,
                // H2 specific
                compatibilityMode = mode,
            )

        fun forMongoDB(version: DatabaseVersion): DatabaseCapabilities =
            DatabaseCapabilities(
                type = DatabaseType.MONGODB,
                version = version,
                // SQL Features - MongoDB is NoSQL, most SQL features don't apply
                supportsExplainAnalyze = true, // MongoDB has explain()
                supportsJsonExplain = true, // Native JSON output
                supportsWindowFunctions = version.isAtLeast(5), // $setWindowFields in 5.0+
                supportsCTEs = false, // No SQL CTEs
                supportsMerge = version.isAtLeast(4, 2), // $merge stage
                supportsUpsert = true, // updateOne with upsert: true
                supportsReturning = version.isAtLeast(4, 2), // findOneAndUpdate with returnDocument
                supportsLateralJoin = true, // $lookup with pipeline
                supportsFullOuterJoin = false, // No full outer join equivalent
                // Schema Features
                supportsPartitioning = true, // Sharding
                supportsInvisibleIndexes = version.isAtLeast(4, 4), // Hidden indexes
                supportsCheckConstraints = version.isAtLeast(3, 6), // JSON Schema validation
                supportsGeneratedColumns = false,
                supportsTableSampling = true, // $sample stage
                supportsStoredProcedures = false,
                supportsRoles = true, // Built-in and user-defined roles
                // Monitoring
                hasSystemSchema = true, // system.* collections
                hasStatisticsViews = true, // db.serverStatus(), collStats
                hasQueryStats = true, // db.currentOp(), profiler
                supportsProgressMonitoring = version.isAtLeast(4, 4), // currentOp progress
                // Syntax - MongoDB uses its own query language
                identifierQuoteChar = '"', // Not really applicable
                maxIdentifierLength = 120, // Collection name limit
                defaultSchema = "", // No schemas
                stringConcatOperator = "", // Uses $concat
                limitSyntax = LimitSyntax.LIMIT, // .limit()
                // Optimization
                recommendedFetchSize = 1000,
                supportsBatchRewrite = true, // bulkWrite
                supportsServerPreparedStatements = false,
            )

        fun forElasticsearch(version: DatabaseVersion): DatabaseCapabilities =
            DatabaseCapabilities(
                type = DatabaseType.ELASTICSEARCH,
                version = version,
                // SQL Features - Elasticsearch is a search engine, limited SQL support
                supportsExplainAnalyze = true, // _validate/query with explain
                supportsJsonExplain = true, // Native JSON output
                supportsWindowFunctions = false,
                supportsCTEs = false,
                supportsMerge = false,
                supportsUpsert = true, // PUT with document ID
                supportsReturning = true, // Response includes document
                supportsLateralJoin = false,
                supportsFullOuterJoin = false,
                // Schema Features
                supportsPartitioning = true, // Index sharding
                supportsInvisibleIndexes = false,
                supportsCheckConstraints = false,
                supportsGeneratedColumns = false,
                supportsTableSampling = true, // Random scoring
                supportsStoredProcedures = false,
                supportsRoles = true, // Security roles
                // Monitoring
                hasSystemSchema = true, // _cat, _cluster APIs
                hasStatisticsViews = true, // _stats API
                hasQueryStats = true, // _tasks API
                supportsProgressMonitoring = version.isAtLeast(7),
                // Syntax - Elasticsearch uses Query DSL
                identifierQuoteChar = '"',
                maxIdentifierLength = 255, // Index name limit
                defaultSchema = "",
                stringConcatOperator = "",
                limitSyntax = LimitSyntax.LIMIT, // size parameter
                // Optimization
                recommendedFetchSize = 1000,
                supportsBatchRewrite = true, // _bulk API
                supportsServerPreparedStatements = false,
            )
    }
}

enum class LimitSyntax {
    LIMIT, // LIMIT n (PostgreSQL, MySQL, SQLite, H2)
    TOP, // TOP n (SQL Server)
    ROWNUM, // WHERE ROWNUM <= n (Oracle)
    FETCH, // FETCH FIRST n ROWS ONLY (SQL:2008 standard)
}

enum class H2CompatibilityMode {
    Regular,
    PostgreSQL,
    MySQL,
    Oracle,
    MSSQLServer,
    DB2,
    Derby,
    HSQLDB,
}
