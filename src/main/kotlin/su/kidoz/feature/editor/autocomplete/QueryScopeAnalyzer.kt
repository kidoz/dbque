package su.kidoz.feature.editor.autocomplete

import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Reference to a table in a query
 */
data class TableRef(
    val tableName: String,
    val schema: String? = null,
    val isSubquery: Boolean = false,
    val subqueryColumns: List<String> = emptyList(),
)

/**
 * CTE (Common Table Expression) information
 */
data class CteInfo(
    val name: String,
    val columns: List<String>,
    val query: String,
)

/**
 * Query scope containing aliases and CTEs
 */
data class QueryScope(
    val aliases: Map<String, TableRef> = emptyMap(),
    val cteDefinitions: Map<String, CteInfo> = emptyMap(),
    val parentScope: QueryScope? = null,
) {
    /**
     * Resolve an alias to its actual table name, looking up through parent scopes
     */
    fun resolveAlias(alias: String): TableRef? {
        val normalizedAlias = alias.lowercase()
        return aliases.entries.find { it.key.lowercase() == normalizedAlias }?.value
            ?: cteDefinitions[normalizedAlias]?.let { cte ->
                TableRef(cte.name, isSubquery = true, subqueryColumns = cte.columns)
            }
            ?: parentScope?.resolveAlias(alias)
    }

    /**
     * Get all available table references (aliases + CTEs)
     */
    fun getAllTableRefs(): Map<String, TableRef> {
        val result = mutableMapOf<String, TableRef>()
        parentScope?.getAllTableRefs()?.let { result.putAll(it) }
        result.putAll(aliases)
        cteDefinitions.forEach { (name, cte) ->
            result[name] = TableRef(cte.name, isSubquery = true, subqueryColumns = cte.columns)
        }
        return result
    }
}

/**
 * Analyzes SQL queries to extract table aliases, CTEs, and build scope hierarchy.
 * Used for context-aware autocompletion.
 */
class QueryScopeAnalyzer {
    private val logger = KotlinLogging.logger {}

    /**
     * Analyze a SQL query and extract the scope at the given cursor position
     */
    fun analyze(
        sql: String,
        cursorPosition: Int = sql.length,
    ): QueryScope {
        val normalizedSql = sql.uppercase()

        // Extract CTEs first
        val cteDefinitions = extractCtes(sql)

        // Extract table aliases from FROM and JOIN clauses
        val aliases = extractAliases(sql)

        return QueryScope(
            aliases = aliases,
            cteDefinitions = cteDefinitions,
        )
    }

    /**
     * Extract CTEs from WITH clause
     */
    private fun extractCtes(sql: String): Map<String, CteInfo> {
        val ctes = mutableMapOf<String, CteInfo>()

        // Match WITH ... AS (...) patterns
        // WITH cte_name [(col1, col2, ...)] AS (SELECT ...)
        val withPattern =
            Regex(
                """(?i)\bWITH\s+(?:RECURSIVE\s+)?(.+?)\s+AS\s*\(""",
                RegexOption.DOT_MATCHES_ALL,
            )

        val withMatch = withPattern.find(sql) ?: return ctes

        // Parse the WITH clause to extract CTE names and their column lists
        val ctePattern =
            Regex(
                """(?i)(\w+)\s*(?:\(([^)]+)\))?\s*AS\s*\(""",
            )

        var searchStart = withMatch.range.first
        val mainQueryStart = findMainQueryStart(sql)

        val cteSection = sql.substring(searchStart, mainQueryStart.coerceAtMost(sql.length))

        ctePattern.findAll(cteSection).forEach { match ->
            val cteName = match.groupValues[1]
            val columnList = match.groupValues[2]
            val columns =
                if (columnList.isNotBlank()) {
                    columnList.split(",").map { it.trim() }
                } else {
                    // Try to extract columns from the SELECT clause
                    extractSelectColumns(sql, match.range.last)
                }

            ctes[cteName.lowercase()] =
                CteInfo(
                    name = cteName,
                    columns = columns,
                    query = "", // We could extract the full query if needed
                )
        }

        return ctes
    }

    /**
     * Find where the main query starts (after CTE definitions)
     */
    private fun findMainQueryStart(sql: String): Int {
        val upperSql = sql.uppercase()

        // Find the last AS ( and its closing parenthesis, then find the next SELECT/INSERT/UPDATE/DELETE
        var depth = 0
        var i = 0
        var afterWith = false

        while (i < sql.length) {
            when {
                upperSql.substring(i).startsWith("WITH") && !afterWith -> {
                    afterWith = true
                    i += 4
                }

                sql[i] == '(' -> {
                    depth++
                }

                sql[i] == ')' -> {
                    depth--
                    if (depth == 0 && afterWith) {
                        // Check if next keyword is SELECT/INSERT/UPDATE/DELETE (main query)
                        val remaining = sql.substring(i + 1).trimStart()
                        val remainingUpper = remaining.uppercase()
                        if (remainingUpper.startsWith("SELECT") ||
                            remainingUpper.startsWith("INSERT") ||
                            remainingUpper.startsWith("UPDATE") ||
                            remainingUpper.startsWith("DELETE")
                        ) {
                            return i + 1 + (sql.substring(i + 1).length - remaining.length)
                        }
                    }
                }
            }
            i++
        }

        return 0
    }

    /**
     * Extract columns from a SELECT clause at the given position
     */
    private fun extractSelectColumns(
        sql: String,
        startPosition: Int,
    ): List<String> {
        val columns = mutableListOf<String>()

        // Find the SELECT after the AS (
        val selectPattern = Regex("""(?i)\(\s*SELECT\s+(.+?)\s+FROM""", RegexOption.DOT_MATCHES_ALL)
        val selectMatch = selectPattern.find(sql, startPosition) ?: return columns

        val columnsPart = selectMatch.groupValues[1]

        // Parse column expressions
        columnsPart.split(",").forEach { colExpr ->
            val trimmed = colExpr.trim()
            // Extract alias if present (column AS alias or column alias)
            val aliasPattern = Regex("""(?i)(?:.*\s+AS\s+(\w+)|.*\s+(\w+))$""")
            val aliasMatch = aliasPattern.find(trimmed)

            val columnName =
                when {
                    aliasMatch != null -> aliasMatch.groupValues[1].ifEmpty { aliasMatch.groupValues[2] }
                    trimmed.contains(".") -> trimmed.substringAfterLast(".").trim()
                    else -> trimmed
                }

            if (columnName.isNotBlank() && columnName != "*") {
                columns.add(columnName)
            }
        }

        return columns
    }

    /**
     * Extract table aliases from FROM and JOIN clauses
     */
    private fun extractAliases(sql: String): Map<String, TableRef> {
        val aliases = mutableMapOf<String, TableRef>()

        // Pattern to match table references with aliases:
        // FROM table_name [AS] alias
        // JOIN table_name [AS] alias
        // FROM schema.table_name [AS] alias
        val tablePattern =
            Regex(
                """(?i)\b(?:FROM|JOIN)\s+(?:(\w+)\.)?(\w+)(?:\s+(?:AS\s+)?(\w+))?""",
            )

        tablePattern.findAll(sql).forEach { match ->
            val schema = match.groupValues[1].takeIf { it.isNotBlank() }
            val tableName = match.groupValues[2]
            val alias = match.groupValues[3].takeIf { it.isNotBlank() }

            // Skip SQL keywords that might be matched
            if (tableName.uppercase() in SQL_KEYWORDS) return@forEach

            val tableRef =
                TableRef(
                    tableName = tableName,
                    schema = schema,
                )

            // Add both the alias and the table name as keys
            if (alias != null) {
                aliases[alias.lowercase()] = tableRef
            }
            // Always add the table name itself
            aliases[tableName.lowercase()] = tableRef
        }

        return aliases
    }

    /**
     * Get the table name or alias before a dot at the given position
     */
    fun getTableBeforeDot(
        text: String,
        dotPosition: Int,
    ): String? {
        if (dotPosition <= 0 || dotPosition > text.length) return null

        val beforeDot = text.substring(0, dotPosition)
        val wordStart = beforeDot.indexOfLast { it.isWhitespace() || it in "(),;=" } + 1
        val word = beforeDot.substring(wordStart)

        return word.takeIf { it.isNotBlank() && it.all { c -> c.isLetterOrDigit() || c == '_' } }
    }

    companion object {
        private val SQL_KEYWORDS =
            setOf(
                "SELECT",
                "FROM",
                "WHERE",
                "JOIN",
                "LEFT",
                "RIGHT",
                "INNER",
                "OUTER",
                "FULL",
                "CROSS",
                "ON",
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
                "ORDER",
                "BY",
                "GROUP",
                "HAVING",
                "LIMIT",
                "OFFSET",
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
                "WITH",
                "RECURSIVE",
                "RETURNING",
            )
    }
}
