package su.kidoz.feature.editor.autocomplete

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
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
}

class AutocompleteProvider(
    private val connectionManager: ConnectionManager,
) {
    private val logger = KotlinLogging.logger {}

    private var cachedTables: List<TableInfo> = emptyList()
    private var cachedColumns: Map<String, List<ColumnInfo>> = emptyMap()
    private var lastConnectionId: String? = null

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

    private val sqlFunctions =
        listOf(
            "COUNT",
            "SUM",
            "AVG",
            "MIN",
            "MAX",
            "COALESCE",
            "NULLIF",
            "CAST",
            "CONCAT",
            "SUBSTRING",
            "TRIM",
            "UPPER",
            "LOWER",
            "LENGTH",
            "REPLACE",
            "NOW",
            "CURRENT_DATE",
            "CURRENT_TIME",
            "CURRENT_TIMESTAMP",
            "DATE",
            "TIME",
            "TIMESTAMP",
            "EXTRACT",
            "DATE_PART",
            "ABS",
            "CEIL",
            "FLOOR",
            "ROUND",
            "MOD",
            "POWER",
            "SQRT",
            "ROW_NUMBER",
            "RANK",
            "DENSE_RANK",
            "NTILE",
            "LAG",
            "LEAD",
            "FIRST_VALUE",
            "LAST_VALUE",
            "NTH_VALUE",
            "OVER",
            "PARTITION",
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
                val conn = connection.getConnection()
                val defaultSchema = driver.getDefaultSchema(conn)

                cachedTables = driver.getTables(conn, defaultSchema)
                cachedColumns =
                    cachedTables.associate { table ->
                        table.name to driver.getColumns(conn, table.name, table.schema)
                    }
                lastConnectionId = connectionId

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

            // Check context
            val context = analyzeContext(beforeCursor)

            when (context) {
                AutocompleteContext.AFTER_FROM, AutocompleteContext.AFTER_JOIN -> {
                    // Suggest tables
                    results.addAll(getTableSuggestions(currentWord))
                }
                AutocompleteContext.AFTER_SELECT, AutocompleteContext.AFTER_WHERE,
                AutocompleteContext.AFTER_SET, AutocompleteContext.AFTER_ORDER_BY,
                -> {
                    // Suggest columns first, then tables, then keywords
                    results.addAll(getColumnSuggestions(currentWord, beforeCursor))
                    results.addAll(getTableSuggestions(currentWord))
                    results.addAll(getFunctionSuggestions(currentWord))
                }
                AutocompleteContext.AFTER_DOT -> {
                    // Suggest columns for the table before the dot
                    val tableAlias = getTableBeforeDot(beforeCursor)
                    results.addAll(getColumnsForTable(tableAlias, currentWord))
                }
                else -> {
                    // Default: keywords first, then tables
                    results.addAll(getKeywordSuggestions(currentWord))
                    results.addAll(getTableSuggestions(currentWord))
                    results.addAll(getFunctionSuggestions(currentWord))
                }
            }

            results.distinctBy { it.text }.take(20)
        }

    private fun analyzeContext(textBeforeCursor: String): AutocompleteContext {
        val upper = textBeforeCursor.uppercase().trim()

        return when {
            upper.endsWith(".") -> AutocompleteContext.AFTER_DOT
            upper.matches(Regex(".*\\bFROM\\s+\\w*$")) -> AutocompleteContext.AFTER_FROM
            upper.matches(Regex(".*\\bJOIN\\s+\\w*$")) -> AutocompleteContext.AFTER_JOIN
            upper.matches(Regex(".*\\bSELECT\\s+.*")) && !upper.contains("FROM") -> AutocompleteContext.AFTER_SELECT
            upper.matches(Regex(".*\\bWHERE\\s+.*")) -> AutocompleteContext.AFTER_WHERE
            upper.matches(Regex(".*\\bSET\\s+.*")) -> AutocompleteContext.AFTER_SET
            upper.matches(Regex(".*\\bORDER\\s+BY\\s+.*")) -> AutocompleteContext.AFTER_ORDER_BY
            upper.matches(Regex(".*\\bGROUP\\s+BY\\s+.*")) -> AutocompleteContext.AFTER_ORDER_BY
            else -> AutocompleteContext.GENERAL
        }
    }

    private fun getTableBeforeDot(text: String): String {
        val dotIndex = text.lastIndexOf('.')
        if (dotIndex <= 0) return ""

        val beforeDot = text.substring(0, dotIndex)
        val wordStart = beforeDot.indexOfLast { it.isWhitespace() || it in "(),;" } + 1
        return beforeDot.substring(wordStart)
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

    private fun getColumnSuggestions(
        prefix: String,
        context: String,
    ): List<AutocompleteItem> {
        // Try to find referenced tables in the query
        val referencedTables = findReferencedTables(context)

        val columns =
            if (referencedTables.isNotEmpty()) {
                referencedTables.flatMap { tableName ->
                    cachedColumns[tableName] ?: emptyList()
                }
            } else {
                cachedColumns.values.flatten()
            }

        return columns
            .filter { it.name.uppercase().startsWith(prefix) }
            .map { column ->
                AutocompleteItem(
                    text = column.name,
                    displayText = column.name,
                    type = AutocompleteType.COLUMN,
                    detail = column.typeDisplay,
                )
            }.distinctBy { it.text }
    }

    private fun getColumnsForTable(
        tableName: String,
        prefix: String,
    ): List<AutocompleteItem> {
        // Find actual table name (might be an alias)
        val actualTableName = findActualTableName(tableName)
        val columns = cachedColumns[actualTableName] ?: cachedColumns[tableName] ?: return emptyList()

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

    private fun getFunctionSuggestions(prefix: String): List<AutocompleteItem> =
        sqlFunctions
            .filter { it.startsWith(prefix) }
            .map { func ->
                AutocompleteItem(
                    text = func,
                    displayText = "$func()",
                    type = AutocompleteType.FUNCTION,
                    insertText = "$func(",
                )
            }

    private fun findReferencedTables(query: String): List<String> {
        val upper = query.uppercase()
        val tables = mutableListOf<String>()

        // Find tables after FROM
        val fromRegex = Regex("\\bFROM\\s+(\\w+)", RegexOption.IGNORE_CASE)
        fromRegex.findAll(upper).forEach { match ->
            val tableName = match.groupValues[1]
            cachedTables.find { it.name.uppercase() == tableName }?.let {
                tables.add(it.name)
            }
        }

        // Find tables after JOIN
        val joinRegex = Regex("\\bJOIN\\s+(\\w+)", RegexOption.IGNORE_CASE)
        joinRegex.findAll(upper).forEach { match ->
            val tableName = match.groupValues[1]
            cachedTables.find { it.name.uppercase() == tableName }?.let {
                tables.add(it.name)
            }
        }

        return tables.distinct()
    }

    private fun findActualTableName(aliasOrName: String): String {
        // This is a simplified version - in a full implementation,
        // we would parse the query to find table aliases
        return cachedTables
            .find {
                it.name.equals(aliasOrName, ignoreCase = true)
            }?.name ?: aliasOrName
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
    AFTER_WHERE,
    AFTER_SET,
    AFTER_ORDER_BY,
    AFTER_DOT,
}
