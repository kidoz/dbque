package su.kidoz.feature.editor.autocomplete

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.kidoz.core.model.ColumnInfo
import su.kidoz.core.model.TableInfo
import su.kidoz.database.ConnectionManager

data class AutocompleteItem(
    val text: String,
    val displayText: String,
    val type: AutocompleteType,
    val detail: String? = null,
    val insertText: String = text,
)

enum class AutocompleteType {
    KEYWORD,
    TABLE,
    VIEW,
    COLUMN,
    FUNCTION,
    SCHEMA,
    DATABASE,
    ALIAS,
    CTE,
}

class AutocompleteProvider(
    private val connectionManager: ConnectionManager,
) {
    private val logger = KotlinLogging.logger {}

    private var cachedTables: List<TableInfo> = emptyList()
    private var cachedColumns: Map<String, List<ColumnInfo>> = emptyMap()
    private var lastConnectionId: String? = null

    private val scopeAnalyzer = QueryScopeAnalyzer()
    private val functionSignatureProvider = FunctionSignatureProvider()

    private val sqlKeywords =
        listOf(
            "SELECT",
            "FROM",
            "WHERE",
            "AND",
            "OR",
            "NOT",
            "IN",
            "IS",
            "NULL",
            "LIKE",
            "BETWEEN",
            "EXISTS",
            "CASE",
            "WHEN",
            "THEN",
            "ELSE",
            "END",
            "AS",
            "ON",
            "JOIN",
            "LEFT",
            "RIGHT",
            "INNER",
            "OUTER",
            "FULL",
            "CROSS",
            "NATURAL",
            "ORDER",
            "BY",
            "ASC",
            "DESC",
            "NULLS",
            "FIRST",
            "LAST",
            "GROUP",
            "HAVING",
            "LIMIT",
            "OFFSET",
            "FETCH",
            "NEXT",
            "ROWS",
            "ONLY",
            "UNION",
            "INTERSECT",
            "EXCEPT",
            "ALL",
            "DISTINCT",
            "INSERT",
            "INTO",
            "VALUES",
            "UPDATE",
            "SET",
            "DELETE",
            "CREATE",
            "ALTER",
            "DROP",
            "TABLE",
            "INDEX",
            "VIEW",
            "DATABASE",
            "SCHEMA",
            "PRIMARY",
            "KEY",
            "FOREIGN",
            "REFERENCES",
            "UNIQUE",
            "CHECK",
            "DEFAULT",
            "CONSTRAINT",
            "CASCADE",
            "RESTRICT",
            "NO",
            "ACTION",
            "BEGIN",
            "COMMIT",
            "ROLLBACK",
            "TRANSACTION",
            "SAVEPOINT",
            "GRANT",
            "REVOKE",
            "TRUNCATE",
            "WITH",
            "RECURSIVE",
            "RETURNING",
        )

    suspend fun refreshCache() =
        withContext(Dispatchers.IO) {
            val connection = connectionManager.activeConnection ?: return@withContext
            val connectionId = connection.config.id

            if (connectionId == lastConnectionId && cachedTables.isNotEmpty()) {
                return@withContext
            }

            try {
                val driver = connection.driver
                connection.getConnection().use { conn ->
                    val defaultSchema = driver.getDefaultSchema(conn)

                    cachedTables = driver.getTables(conn, defaultSchema)
                    cachedColumns =
                        cachedTables.associate { table ->
                            table.name to driver.getColumns(conn, table.name, table.schema)
                        }
                    lastConnectionId = connectionId
                }

                logger.debug { "Autocomplete cache refreshed: ${cachedTables.size} tables" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to refresh autocomplete cache" }
            }
        }

    suspend fun getSuggestions(
        text: String,
        cursorPosition: Int,
    ): List<AutocompleteItem> =
        withContext(Dispatchers.Default) {
            if (text.isEmpty() || cursorPosition == 0) {
                return@withContext emptyList()
            }

            // Get the word being typed
            val beforeCursor = text.substring(0, cursorPosition)
            val wordStart = beforeCursor.indexOfLast { it.isWhitespace() || it in "(),;." } + 1
            val currentWord = beforeCursor.substring(wordStart).uppercase()

            if (currentWord.isEmpty()) {
                return@withContext emptyList()
            }

            val results = mutableListOf<AutocompleteItem>()

            // Analyze query scope for alias and CTE resolution
            val scope = scopeAnalyzer.analyze(text, cursorPosition)

            // Check context
            val context = analyzeContext(beforeCursor)

            when (context) {
                AutocompleteContext.AFTER_FROM, AutocompleteContext.AFTER_JOIN -> {
                    // Suggest tables, views, and CTEs
                    results.addAll(getTableSuggestions(currentWord))
                    results.addAll(getCTESuggestions(scope, currentWord))
                }

                AutocompleteContext.AFTER_SELECT, AutocompleteContext.AFTER_WHERE,
                AutocompleteContext.AFTER_SET, AutocompleteContext.AFTER_ORDER_BY,
                -> {
                    // Suggest columns first (with alias awareness), then tables, then keywords
                    results.addAll(getColumnSuggestionsWithScope(currentWord, scope))
                    results.addAll(getAliasSuggestions(scope, currentWord))
                    results.addAll(getTableSuggestions(currentWord))
                    results.addAll(getFunctionSuggestionsEnhanced(currentWord))
                }

                AutocompleteContext.AFTER_DOT -> {
                    // Suggest columns for the table/alias before the dot
                    val tableOrAlias = scopeAnalyzer.getTableBeforeDot(beforeCursor, beforeCursor.length - 1)
                    if (tableOrAlias != null) {
                        results.addAll(getColumnsForTableOrAlias(tableOrAlias, currentWord, scope))
                    }
                }

                AutocompleteContext.AFTER_ON -> {
                    // Suggest columns for JOIN condition - prioritize FK relationships
                    results.addAll(getJoinColumnSuggestions(currentWord, scope, beforeCursor))
                }

                else -> {
                    // Default: keywords first, then tables
                    results.addAll(getKeywordSuggestions(currentWord))
                    results.addAll(getTableSuggestions(currentWord))
                    results.addAll(getFunctionSuggestionsEnhanced(currentWord))
                }
            }

            results.distinctBy { it.text.lowercase() }.take(20)
        }

    private fun analyzeContext(textBeforeCursor: String): AutocompleteContext {
        val upper = textBeforeCursor.uppercase().trim()

        return when {
            upper.endsWith(".") -> AutocompleteContext.AFTER_DOT
            upper.matches(Regex(".*\\bFROM\\s+\\w*$")) -> AutocompleteContext.AFTER_FROM
            upper.matches(Regex(".*\\bJOIN\\s+\\w*$")) -> AutocompleteContext.AFTER_JOIN
            upper.matches(Regex(".*\\bON\\s+.*")) -> AutocompleteContext.AFTER_ON
            upper.matches(Regex(".*\\bSELECT\\s+.*")) && !upper.contains("FROM") -> AutocompleteContext.AFTER_SELECT
            upper.matches(Regex(".*\\bWHERE\\s+.*")) -> AutocompleteContext.AFTER_WHERE
            upper.matches(Regex(".*\\bSET\\s+.*")) -> AutocompleteContext.AFTER_SET
            upper.matches(Regex(".*\\bORDER\\s+BY\\s+.*")) -> AutocompleteContext.AFTER_ORDER_BY
            upper.matches(Regex(".*\\bGROUP\\s+BY\\s+.*")) -> AutocompleteContext.AFTER_ORDER_BY
            else -> AutocompleteContext.GENERAL
        }
    }

    private fun getKeywordSuggestions(prefix: String): List<AutocompleteItem> =
        sqlKeywords
            .filter { it.startsWith(prefix) }
            .map { keyword ->
                AutocompleteItem(
                    text = keyword,
                    displayText = keyword,
                    type = AutocompleteType.KEYWORD,
                )
            }

    private fun getTableSuggestions(prefix: String): List<AutocompleteItem> =
        cachedTables
            .filter { it.name.uppercase().startsWith(prefix) }
            .map { table ->
                AutocompleteItem(
                    text = table.name,
                    displayText = table.name,
                    type = AutocompleteType.TABLE,
                    detail = table.schema,
                )
            }

    private fun getCTESuggestions(
        scope: QueryScope,
        prefix: String,
    ): List<AutocompleteItem> =
        scope.cteDefinitions.values
            .filter { it.name.uppercase().startsWith(prefix) }
            .map { cte ->
                AutocompleteItem(
                    text = cte.name,
                    displayText = cte.name,
                    type = AutocompleteType.CTE,
                    detail = "CTE (${cte.columns.size} columns)",
                )
            }

    private fun getAliasSuggestions(
        scope: QueryScope,
        prefix: String,
    ): List<AutocompleteItem> =
        scope.aliases.entries
            .filter { it.key.uppercase().startsWith(prefix) }
            .map { (alias, tableRef) ->
                AutocompleteItem(
                    text = alias,
                    displayText = alias,
                    type = AutocompleteType.ALIAS,
                    detail = "alias for ${tableRef.tableName}",
                )
            }

    private fun getColumnSuggestionsWithScope(
        prefix: String,
        scope: QueryScope,
    ): List<AutocompleteItem> {
        val results = mutableListOf<AutocompleteItem>()

        // Get columns from all tables/aliases in scope
        scope.getAllTableRefs().forEach { (alias, tableRef) ->
            if (tableRef.isSubquery) {
                // CTE or subquery columns
                tableRef.subqueryColumns
                    .filter { it.uppercase().startsWith(prefix) }
                    .forEach { column ->
                        results.add(
                            AutocompleteItem(
                                text = column,
                                displayText = column,
                                type = AutocompleteType.COLUMN,
                                detail = "from $alias",
                            ),
                        )
                    }
            } else {
                // Regular table columns
                cachedColumns[tableRef.tableName]
                    ?.filter { it.name.uppercase().startsWith(prefix) }
                    ?.forEach { column ->
                        results.add(
                            AutocompleteItem(
                                text = column.name,
                                displayText = column.name,
                                type = AutocompleteType.COLUMN,
                                detail = "${column.typeDisplay} (${tableRef.tableName})",
                            ),
                        )
                    }
            }
        }

        // If scope is empty, fall back to all columns
        if (results.isEmpty()) {
            cachedColumns.values
                .flatten()
                .filter { it.name.uppercase().startsWith(prefix) }
                .forEach { column ->
                    results.add(
                        AutocompleteItem(
                            text = column.name,
                            displayText = column.name,
                            type = AutocompleteType.COLUMN,
                            detail = column.typeDisplay,
                        ),
                    )
                }
        }

        return results.distinctBy { it.text.lowercase() }
    }

    private fun getColumnsForTableOrAlias(
        tableOrAlias: String,
        prefix: String,
        scope: QueryScope,
    ): List<AutocompleteItem> {
        // First try to resolve as an alias
        val resolved = scope.resolveAlias(tableOrAlias)

        return if (resolved != null) {
            if (resolved.isSubquery) {
                // CTE or subquery - use declared columns
                resolved.subqueryColumns
                    .filter { prefix.isEmpty() || it.uppercase().startsWith(prefix) }
                    .map { column ->
                        AutocompleteItem(
                            text = column,
                            displayText = column,
                            type = AutocompleteType.COLUMN,
                            detail = "from ${resolved.tableName}",
                        )
                    }
            } else {
                // Regular table alias
                getColumnsForTable(resolved.tableName, prefix)
            }
        } else {
            // Try as direct table name
            getColumnsForTable(tableOrAlias, prefix)
        }
    }

    private fun getColumnsForTable(
        tableName: String,
        prefix: String,
    ): List<AutocompleteItem> {
        val columns =
            cachedColumns[tableName]
                ?: cachedColumns.entries.find { it.key.equals(tableName, ignoreCase = true) }?.value
                ?: return emptyList()

        return columns
            .filter { prefix.isEmpty() || it.name.uppercase().startsWith(prefix) }
            .map { column ->
                AutocompleteItem(
                    text = column.name,
                    displayText = column.name,
                    type = AutocompleteType.COLUMN,
                    detail = column.typeDisplay,
                )
            }
    }

    private fun getJoinColumnSuggestions(
        prefix: String,
        scope: QueryScope,
        beforeCursor: String,
    ): List<AutocompleteItem> {
        val results = mutableListOf<AutocompleteItem>()

        // Get all table references for column suggestions
        scope.getAllTableRefs().forEach { (alias, tableRef) ->
            if (!tableRef.isSubquery) {
                cachedColumns[tableRef.tableName]
                    ?.filter { it.name.uppercase().startsWith(prefix) }
                    ?.forEach { column ->
                        // Prioritize ID columns and columns with similar names
                        @Suppress("UNUSED_VARIABLE")
                        val priority =
                            when {
                                column.name.endsWith("_id", ignoreCase = true) -> 0
                                column.name.equals("id", ignoreCase = true) -> 1
                                column.name.contains("_id_", ignoreCase = true) -> 2
                                else -> 3
                            }
                        results.add(
                            AutocompleteItem(
                                text = "$alias.${column.name}",
                                displayText = "$alias.${column.name}",
                                type = AutocompleteType.COLUMN,
                                detail = "${column.typeDisplay} (join key)",
                            ),
                        )
                    }
            }
        }

        return results.distinctBy { it.text.lowercase() }
    }

    private fun getFunctionSuggestionsEnhanced(prefix: String): List<AutocompleteItem> {
        val signatures = functionSignatureProvider.getFunctionsByPrefix(prefix)

        return signatures.map { sig ->
            val paramHint = functionSignatureProvider.formatShortHint(sig)
            AutocompleteItem(
                text = sig.name,
                displayText = paramHint,
                type = AutocompleteType.FUNCTION,
                detail = sig.description,
                insertText = "${sig.name}(",
            )
        }
    }

    fun invalidateCache() {
        cachedTables = emptyList()
        cachedColumns = emptyMap()
        lastConnectionId = null
    }
}

enum class AutocompleteContext {
    GENERAL,
    AFTER_SELECT,
    AFTER_FROM,
    AFTER_JOIN,
    AFTER_ON,
    AFTER_WHERE,
    AFTER_SET,
    AFTER_ORDER_BY,
    AFTER_DOT,
}
