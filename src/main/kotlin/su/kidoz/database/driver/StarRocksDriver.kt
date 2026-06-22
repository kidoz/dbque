package su.kidoz.database.driver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.kidoz.core.model.*
import su.kidoz.database.capabilities.*
import java.sql.Connection
import java.sql.Types

/**
 * Driver for StarRocks (https://starrocks.io).
 *
 * StarRocks is wire-compatible with the MySQL protocol and ships no JDBC driver of its
 * own, so it reuses MySQL Connector/J on the FE query port (default 9030) and inherits
 * most metadata behaviour from [MySqlDriver]. Only the parts where StarRocks diverges
 * from MySQL are overridden here:
 *  - version detection uses `current_version()` (the MySQL-compat `VERSION()` reports a
 *    fake MySQL version, not the StarRocks one),
 *  - EXPLAIN has no `FORMAT=JSON/TREE`; StarRocks supports `EXPLAIN [ANALYZE|COSTS|VERBOSE]`,
 *  - StarRocks has no InnoDB internals, so secondary-index statistics are unavailable,
 *  - StarRocks does not enforce foreign keys and exposes no conventional secondary indexes,
 *    and the MySQL connector's `getImportedKeys`/`getIndexInfo` issue `information_schema`
 *    queries StarRocks cannot resolve, so both are reported as empty rather than failing.
 */
class StarRocksDriver : MySqlDriver() {
    override val type: DatabaseType = DatabaseType.STARROCKS

    // ==================== StarRocks Version Detection ====================

    override suspend fun getVersion(connection: Connection): DatabaseVersion =
        withContext(Dispatchers.IO) {
            cachedVersion?.let { return@withContext it }

            try {
                connection.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT current_version()").use { rs ->
                        if (rs.next()) {
                            val version = DatabaseVersion.parseStarRocks(rs.getString(1))
                            cachedVersion = version
                            return@withContext version
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get StarRocks version, falling back to MySQL-compat VERSION()" }
            }

            super.getVersion(connection)
        }

    // ==================== StarRocks EXPLAIN ====================

    override fun buildExplainQuery(
        query: String,
        analyze: Boolean,
        format: ExplainFormat,
    ): String =
        when {
            analyze -> "EXPLAIN ANALYZE $query"

            // StarRocks lacks FORMAT=JSON/TREE; the cost-annotated plan is the closest analogue.
            format == ExplainFormat.JSON || format == ExplainFormat.TREE -> "EXPLAIN COSTS $query"

            else -> "EXPLAIN $query"
        }

    override suspend fun explainQuery(
        connection: Connection,
        query: String,
        analyze: Boolean,
        format: ExplainFormat,
    ): ExplainResult =
        // StarRocks only ever returns a textual plan, so coerce JSON/TREE requests to TEXT.
        super.explainQuery(connection, query, analyze, ExplainFormat.TEXT)

    // ==================== StarRocks Catalogs ====================

    /**
     * List catalogs via `SHOW CATALOGS`. Falls back to a single internal catalog on older
     * clusters (or insufficient privileges) so the explorer still renders.
     */
    override suspend fun getCatalogs(connection: Connection): List<CatalogInfo> =
        withContext(Dispatchers.IO) {
            try {
                connection.createStatement().use { stmt ->
                    stmt.executeQuery("SHOW CATALOGS").use { rs ->
                        val columns = rs.metaData.columnCount
                        val catalogs = mutableListOf<CatalogInfo>()
                        while (rs.next()) {
                            val name = rs.getString(1) ?: continue
                            val type = if (columns >= 2) rs.getString(2) else null
                            val comment = if (columns >= 3) rs.getString(3) else null
                            val internal =
                                name.equals(DEFAULT_CATALOG, ignoreCase = true) ||
                                    type?.equals("Internal", ignoreCase = true) == true
                            catalogs.add(CatalogInfo(name = name, type = type, isInternal = internal, comment = comment))
                        }
                        catalogs.sortedWith(compareByDescending<CatalogInfo> { it.isInternal }.thenBy { it.name })
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "SHOW CATALOGS failed; treating connection as a single internal catalog" }
                listOf(CatalogInfo(name = DEFAULT_CATALOG, type = "Internal", isInternal = true))
            }
        }

    /**
     * List the databases inside a catalog. The internal catalog uses `SHOW DATABASES`; external
     * catalogs use `SHOW DATABASES FROM <catalog>`. Schema names are catalog-qualified for
     * external catalogs ("catalog.db") so downstream table/column lookups can resolve them.
     */
    suspend fun getDatabasesInCatalog(
        connection: Connection,
        catalog: CatalogInfo,
    ): List<SchemaInfo> =
        withContext(Dispatchers.IO) {
            val sql =
                if (catalog.isInternal) {
                    "SHOW DATABASES"
                } else {
                    "SHOW DATABASES FROM ${escapeIdentifier(catalog.name)}"
                }
            try {
                connection.createStatement().use { stmt ->
                    stmt.executeQuery(sql).use { rs ->
                        val schemas = mutableListOf<SchemaInfo>()
                        while (rs.next()) {
                            val db = rs.getString(1) ?: continue
                            val schemaName = if (catalog.isInternal) db else "${catalog.name}.$db"
                            schemas.add(SchemaInfo(name = schemaName, catalog = catalog.name))
                        }
                        schemas.sortedBy { it.name }
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to list databases in catalog ${catalog.name}" }
                emptyList()
            }
        }

    // Split a (possibly catalog-qualified) schema identifier into catalog + database. A null
    // catalog means the internal catalog, which is served by the information_schema fast paths.
    private fun parseQualifiedSchema(schema: String?): Pair<String?, String?> =
        when {
            schema == null -> null to null
            schema.contains('.') -> schema.substringBefore('.') to schema.substringAfter('.')
            else -> null to schema
        }

    // ==================== StarRocks Keywords & Functions ====================

    // Surface StarRocks-only DDL/query keywords on top of whatever the MySQL connector reports,
    // so autocomplete and highlighting understand table-model and distribution syntax.
    override suspend fun getKeywords(connection: Connection): List<String> =
        mergeDistinct(super.getKeywords(connection), STARROCKS_KEYWORDS)

    // StarRocks ships a large analytical function library (bitmap/HLL/array/map/JSON/window)
    // that the MySQL connector's metadata does not know about; add it to completion.
    override suspend fun getFunctions(connection: Connection): List<String> =
        mergeDistinct(super.getFunctions(connection), STARROCKS_FUNCTIONS)

    private fun mergeDistinct(
        base: List<String>,
        extra: List<String>,
    ): List<String> = (base + extra).distinctBy { it.uppercase() }

    // ==================== Catalog-Qualified Metadata ====================

    // For the internal catalog, defer to MySqlDriver's information_schema path. External catalog
    // tables are not in information_schema, so list them with `SHOW TABLES FROM catalog.db`.
    override suspend fun getTables(
        connection: Connection,
        schema: String?,
        catalog: String?,
    ): List<TableInfo> {
        val (cat, db) = parseQualifiedSchema(schema)
        if (cat == null || db == null) {
            // Internal catalog: use the information_schema path, then annotate with the table
            // model (duplicate/aggregate/unique/primary) from information_schema.tables_config.
            val tables = super.getTables(connection, schema, catalog)
            val models = getTableModels(connection, schema ?: connection.catalog)
            return if (models.isEmpty()) {
                tables
            } else {
                tables.map { it.copy(tableModel = models[it.name]) }
            }
        }

        return withContext(Dispatchers.IO) {
            try {
                connection.createStatement().use { stmt ->
                    stmt
                        .executeQuery("SHOW TABLES FROM ${escapeIdentifier(cat)}.${escapeIdentifier(db)}")
                        .use { rs ->
                            val tables = mutableListOf<TableInfo>()
                            while (rs.next()) {
                                tables.add(
                                    TableInfo(
                                        name = rs.getString(1),
                                        schema = schema, // keep the qualified id so keys/details resolve
                                        catalog = cat,
                                        type = TableType.TABLE,
                                    ),
                                )
                            }
                            tables.sortedBy { it.name }
                        }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to list tables in $cat.$db" }
                emptyList()
            }
        }
    }

    // External catalogs do not separate views from tables over JDBC, so only the internal
    // catalog reports views (via the information_schema path).
    override suspend fun getViews(
        connection: Connection,
        schema: String?,
        catalog: String?,
    ): List<ViewInfo> {
        val (cat, _) = parseQualifiedSchema(schema)
        return if (cat == null) super.getViews(connection, schema, catalog) else emptyList()
    }

    // External catalog columns are not exposed through JDBC metadata; read them with DESCRIBE.
    override suspend fun getColumns(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): List<ColumnInfo> {
        val (cat, db) = parseQualifiedSchema(schema)
        if (cat == null || db == null) return super.getColumns(connection, table, schema, catalog)

        return withContext(Dispatchers.IO) {
            try {
                connection.createStatement().use { stmt ->
                    val qualified = "${escapeIdentifier(cat)}.${escapeIdentifier(db)}.${escapeIdentifier(table)}"
                    stmt.executeQuery("DESCRIBE $qualified").use { rs ->
                        val columns = mutableListOf<ColumnInfo>()
                        var ordinal = 0
                        while (rs.next()) {
                            // DESCRIBE columns: Field, Type, Null, [Key, Default, Extra]
                            val nullable = rs.getString(3)?.equals("yes", ignoreCase = true) ?: true
                            columns.add(
                                ColumnInfo(
                                    name = rs.getString(1),
                                    dataType = rs.getString(2) ?: "",
                                    jdbcType = Types.OTHER,
                                    nullable = nullable,
                                    ordinalPosition = ordinal++,
                                ),
                            )
                        }
                        columns
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to describe $cat.$db.$table" }
                emptyList()
            }
        }
    }

    // ==================== StarRocks Table Model & Materialized Views ====================

    // Map table name -> friendly table model for an internal-catalog database, from tables_config.
    private suspend fun getTableModels(
        connection: Connection,
        dbName: String?,
    ): Map<String, String> =
        withContext(Dispatchers.IO) {
            if (dbName == null) return@withContext emptyMap()
            try {
                val sql = "SELECT TABLE_NAME, TABLE_MODEL FROM information_schema.tables_config WHERE TABLE_SCHEMA = ?"
                connection.prepareStatement(sql).use { ps ->
                    ps.setString(1, dbName)
                    ps.executeQuery().use { rs ->
                        val models = mutableMapOf<String, String>()
                        while (rs.next()) {
                            val name = rs.getString("TABLE_NAME") ?: continue
                            friendlyTableModel(rs.getString("TABLE_MODEL"))?.let { models[name] = it }
                        }
                        models
                    }
                }
            } catch (e: Exception) {
                logger.debug(e) { "information_schema.tables_config unavailable on this StarRocks version" }
                emptyMap()
            }
        }

    private fun friendlyTableModel(raw: String?): String? =
        when (raw?.uppercase()) {
            "DUP_KEYS" -> "duplicate"
            "AGG_KEYS" -> "aggregate"
            "UNIQUE_KEYS" -> "unique"
            "PRIMARY_KEYS" -> "primary"
            null, "" -> null
            else -> raw.lowercase()
        }

    /**
     * List materialized views in an internal-catalog database via information_schema. External
     * catalogs have no StarRocks materialized views, so they return empty.
     */
    suspend fun getMaterializedViews(
        connection: Connection,
        schema: String?,
    ): List<ViewInfo> =
        withContext(Dispatchers.IO) {
            val (cat, db) = parseQualifiedSchema(schema)
            if (cat != null) return@withContext emptyList()
            val dbName = db ?: connection.catalog ?: return@withContext emptyList()
            try {
                val sql = "SELECT TABLE_NAME FROM information_schema.materialized_views WHERE TABLE_SCHEMA = ? ORDER BY TABLE_NAME"
                connection.prepareStatement(sql).use { ps ->
                    ps.setString(1, dbName)
                    ps.executeQuery().use { rs ->
                        val mvs = mutableListOf<ViewInfo>()
                        while (rs.next()) {
                            mvs.add(ViewInfo(name = rs.getString(1), schema = schema, catalog = null))
                        }
                        mvs
                    }
                }
            } catch (e: Exception) {
                logger.debug(e) { "information_schema.materialized_views unavailable on this StarRocks version" }
                emptyList()
            }
        }

    // ==================== StarRocks Constraint & Index Introspection ====================

    // StarRocks is an OLAP engine that does not enforce foreign keys, and the MySQL
    // connector's getImportedKeys() runs an information_schema query referencing
    // KEY_COLUMN_USAGE.CONSTRAINT_NAME, which StarRocks cannot resolve. Try the inherited
    // path (forward-compatible if a future version supports it) and fall back to none.
    override suspend fun getForeignKeys(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): List<ForeignKeyInfo> =
        try {
            super.getForeignKeys(connection, table, schema, catalog)
        } catch (e: Exception) {
            logger.debug(e) { "Foreign-key metadata unavailable on StarRocks for $table" }
            emptyList()
        }

    // StarRocks has no conventional secondary indexes (it uses sort/prefix keys and
    // bitmap/bloom-filter indexes), and getIndexInfo() hits the same unsupported
    // information_schema path; degrade to an empty list instead of failing.
    override suspend fun getIndexes(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): List<IndexInfo> =
        try {
            super.getIndexes(connection, table, schema, catalog)
        } catch (e: Exception) {
            logger.debug(e) { "Index metadata unavailable on StarRocks for $table" }
            emptyList()
        }

    // ==================== StarRocks Index Statistics ====================

    override suspend fun getIndexStatistics(
        connection: Connection,
        table: String,
        schema: String?,
        catalog: String?,
    ): List<IndexStatistics> = emptyList() // StarRocks has no InnoDB secondary-index statistics

    private companion object {
        const val DEFAULT_CATALOG = "default_catalog"

        // StarRocks-specific keywords beyond the MySQL set (table models, distribution,
        // partitioning, materialized views, catalogs, loads).
        val STARROCKS_KEYWORDS =
            listOf(
                "AGGREGATE",
                "DUPLICATE",
                "DISTRIBUTED",
                "BUCKETS",
                "PROPERTIES",
                "HASH",
                "RANDOM",
                "ROLLUP",
                "MATERIALIZED",
                "REFRESH",
                "ASYNC",
                "MANUAL",
                "EVERY",
                "PARTITIONS",
                "TEMPORARY",
                "CATALOG",
                "CATALOGS",
                "EXTERNAL",
                "RESOURCE",
                "BROKER",
                "ROUTINE",
                "LOAD",
                "STREAM",
                "COLOCATE",
                "BITMAP",
                "HLL",
                "PERCENTILE",
                "STRUCT",
                "ARRAY",
                "MAP",
                "JSON",
                "LARGEINT",
                "STORAGE",
                "WAREHOUSE",
                "PIPE",
                "TASK",
                "FILES",
                "OVERWRITE",
            )

        // A curated slice of StarRocks' analytical function library for completion.
        val STARROCKS_FUNCTIONS =
            listOf(
                // bitmap
                "to_bitmap",
                "bitmap_union",
                "bitmap_count",
                "bitmap_and",
                "bitmap_or",
                "bitmap_xor",
                "bitmap_contains",
                "bitmap_to_string",
                "bitmap_from_string",
                "bitmap_hash",
                "bitmap_union_count",
                // HLL
                "hll_union",
                "hll_union_agg",
                "hll_cardinality",
                "hll_hash",
                "hll_empty",
                // approximate / percentile
                "approx_count_distinct",
                "percentile_approx",
                "percentile_cont",
                "percentile_disc",
                "multi_distinct_count",
                // array
                "array_agg",
                "array_length",
                "array_contains",
                "array_map",
                "array_filter",
                "array_sum",
                "array_sortby",
                "array_distinct",
                "array_join",
                "unnest",
                // map / struct
                "map_keys",
                "map_values",
                "map_size",
                "row",
                "named_struct",
                // json
                "parse_json",
                "json_query",
                "json_exists",
                "json_array",
                "json_object",
                "get_json_string",
                "get_json_int",
                "get_json_double",
                // date / time
                "date_trunc",
                "date_format",
                "days_diff",
                "time_slice",
                "str_to_date",
                "from_unixtime",
                "unix_timestamp",
                // string
                "split",
                "split_part",
                "concat_ws",
                "regexp_extract",
                "regexp_replace",
                "group_concat",
                // window / aggregate
                "retention",
                "window_funnel",
                "max_by",
                "min_by",
                "any_value",
            )
    }
}
