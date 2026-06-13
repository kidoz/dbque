package su.kidoz.database.executor

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.kidoz.core.model.QueryExecution
import su.kidoz.core.model.QueryExecutionResult
import su.kidoz.core.model.QueryResult
import su.kidoz.core.model.ResultColumn
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types

class QueryExecutor {
    private val logger = KotlinLogging.logger {}

    suspend fun execute(
        connection: Connection,
        execution: QueryExecution,
    ): QueryExecutionResult =
        withContext(Dispatchers.IO) {
            try {
                connection.createStatement().use { statement ->
                    statement.apply {
                        if (execution.maxRows > 0) maxRows = execution.maxRows
                        if (execution.timeout > 0) queryTimeout = execution.timeout
                        if (execution.fetchSize > 0) fetchSize = execution.fetchSize
                    }

                    val queries = splitQueries(execution.query)
                    val results = mutableListOf<QueryResult>()

                    for (query in queries) {
                        val trimmedQuery = query.trim()
                        if (trimmedQuery.isEmpty()) continue

                        logger.debug { "Executing query: $trimmedQuery" }

                        val queryStartTime = System.currentTimeMillis()
                        val hasResultSet = statement.execute(trimmedQuery)
                        val execTime = System.currentTimeMillis() - queryStartTime

                        if (hasResultSet) {
                            statement.resultSet?.use { rs ->
                                results.add(mapResultSet(rs, trimmedQuery, execTime))
                            }
                        } else {
                            val affectedRows = statement.updateCount
                            results.add(
                                QueryResult(
                                    columns = emptyList(),
                                    rows = emptyList(),
                                    rowCount = 0,
                                    affectedRows = affectedRows,
                                    executionTimeMs = execTime,
                                    query = trimmedQuery,
                                    isResultSet = false,
                                ),
                            )
                        }

                        // Handle multiple results from a single execute call.
                        while (true) {
                            val hasMoreResults = statement.moreResults
                            val updateCount = statement.updateCount

                            if (!hasMoreResults && updateCount == -1) {
                                break
                            }

                            if (hasMoreResults) {
                                statement.resultSet?.use { rs ->
                                    results.add(
                                        mapResultSet(
                                            rs,
                                            trimmedQuery,
                                            System.currentTimeMillis() - queryStartTime,
                                        ),
                                    )
                                }
                            } else {
                                results.add(
                                    QueryResult(
                                        columns = emptyList(),
                                        rows = emptyList(),
                                        rowCount = 0,
                                        affectedRows = updateCount,
                                        executionTimeMs = System.currentTimeMillis() - queryStartTime,
                                        query = trimmedQuery,
                                        isResultSet = false,
                                    ),
                                )
                            }
                        }
                    }

                    // Collect warnings
                    val warnings = mutableListOf<String>()
                    var warning = statement.warnings
                    while (warning != null) {
                        warnings.add(warning.message ?: "Unknown warning")
                        warning = warning.nextWarning
                    }

                    if (results.size == 1) {
                        QueryExecutionResult.Success(results.first().copy(warnings = warnings))
                    } else {
                        QueryExecutionResult.MultiResult(
                            results.mapIndexed { index, result ->
                                if (index == results.lastIndex) {
                                    result.copy(warnings = warnings)
                                } else {
                                    result
                                }
                            },
                        )
                    }
                }
            } catch (e: SQLException) {
                logger.error(e) { "Query execution failed" }
                QueryExecutionResult.Error(
                    message = e.message ?: "Unknown error",
                    sqlState = e.sqlState,
                    errorCode = e.errorCode,
                )
            }
        }

    private fun mapResultSet(
        rs: ResultSet,
        query: String,
        execTimeMs: Long,
    ): QueryResult {
        val metadata = rs.metaData
        val columnCount = metadata.columnCount

        val columns =
            (1..columnCount).map { i ->
                ResultColumn(
                    name = metadata.getColumnName(i),
                    label = metadata.getColumnLabel(i),
                    typeName = metadata.getColumnTypeName(i),
                    jdbcType = metadata.getColumnType(i),
                    displaySize = metadata.getColumnDisplaySize(i),
                    precision = metadata.getPrecision(i),
                    scale = metadata.getScale(i),
                    nullable = metadata.isNullable(i) != 0,
                    autoIncrement = metadata.isAutoIncrement(i),
                    tableName = metadata.getTableName(i).takeIf { it.isNotBlank() },
                    schemaName = metadata.getSchemaName(i).takeIf { it.isNotBlank() },
                )
            }

        val rows = mutableListOf<List<Any?>>()
        while (rs.next()) {
            val row =
                (1..columnCount).map { i ->
                    getColumnValue(rs, i, metadata.getColumnType(i))
                }
            rows.add(row)
        }

        return QueryResult(
            columns = columns,
            rows = rows,
            rowCount = rows.size,
            executionTimeMs = execTimeMs,
            query = query,
            isResultSet = true,
        )
    }

    private fun getColumnValue(
        rs: ResultSet,
        index: Int,
        jdbcType: Int,
    ): Any? {
        val value =
            when (jdbcType) {
                Types.NULL -> {
                    null
                }

                Types.BOOLEAN, Types.BIT -> {
                    rs.getBoolean(index)
                }

                Types.TINYINT -> {
                    rs.getByte(index)
                }

                Types.SMALLINT -> {
                    rs.getShort(index)
                }

                Types.INTEGER -> {
                    rs.getInt(index)
                }

                Types.BIGINT -> {
                    rs.getLong(index)
                }

                Types.REAL -> {
                    rs.getFloat(index)
                }

                Types.FLOAT, Types.DOUBLE -> {
                    rs.getDouble(index)
                }

                Types.DECIMAL, Types.NUMERIC -> {
                    rs.getBigDecimal(index)
                }

                Types.DATE -> {
                    rs.getDate(index)
                }

                Types.TIME, Types.TIME_WITH_TIMEZONE -> {
                    rs.getTime(index)
                }

                Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> {
                    rs.getTimestamp(index)
                }

                Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> {
                    rs.getBytes(index)?.let { "[BLOB: ${it.size} bytes]" }
                }

                Types.CLOB, Types.NCLOB -> {
                    rs.getClob(index)?.let { "[CLOB: ${it.length()} chars]" }
                }

                Types.ARRAY -> {
                    rs.getArray(index)?.array
                }

                else -> {
                    rs.getString(index)
                }
            }

        return if (rs.wasNull()) null else value
    }

    private fun splitQueries(sql: String): List<String> {
        val queries = mutableListOf<String>()
        var current = StringBuilder()
        var i = 0
        val len = sql.length

        while (i < len) {
            val c = sql[i]

            when {
                // Single-line comment: -- until end of line
                c == '-' && i + 1 < len && sql[i + 1] == '-' -> {
                    current.append(c)
                    i++
                    current.append(sql[i])
                    i++
                    // Read until end of line
                    while (i < len && sql[i] != '\n') {
                        current.append(sql[i])
                        i++
                    }
                    if (i < len) {
                        current.append(sql[i])
                        i++
                    }
                }

                // Multi-line comment: /* ... */
                c == '/' && i + 1 < len && sql[i + 1] == '*' -> {
                    current.append(c)
                    i++
                    current.append(sql[i])
                    i++
                    // Read until closing */
                    while (i < len) {
                        if (sql[i] == '*' && i + 1 < len && sql[i + 1] == '/') {
                            current.append(sql[i])
                            i++
                            current.append(sql[i])
                            i++
                            break
                        }
                        current.append(sql[i])
                        i++
                    }
                }

                // Dollar-quoted string (PostgreSQL): $tag$...$tag$ or $$...$$
                c == '$' -> {
                    val dollarQuote = extractDollarQuoteTag(sql, i)
                    if (dollarQuote != null) {
                        current.append(dollarQuote)
                        i += dollarQuote.length
                        // Find the closing dollar quote
                        val closingIndex = sql.indexOf(dollarQuote, i)
                        if (closingIndex >= 0) {
                            current.append(sql.substring(i, closingIndex + dollarQuote.length))
                            i = closingIndex + dollarQuote.length
                        } else {
                            // No closing tag found, append rest of string
                            current.append(sql.substring(i))
                            i = len
                        }
                    } else {
                        current.append(c)
                        i++
                    }
                }

                // Single or double quoted string
                c == '\'' || c == '"' -> {
                    val stringChar = c
                    current.append(c)
                    i++
                    while (i < len) {
                        val sc = sql[i]
                        current.append(sc)
                        i++
                        if (sc == stringChar) {
                            // Check for escaped quote (doubled)
                            if (i < len && sql[i] == stringChar) {
                                current.append(sql[i])
                                i++
                            } else {
                                break
                            }
                        }
                    }
                }

                // Semicolon - query separator
                c == ';' -> {
                    val query = current.toString().trim()
                    if (query.isNotEmpty()) {
                        queries.add(query)
                    }
                    current = StringBuilder()
                    i++
                }

                else -> {
                    current.append(c)
                    i++
                }
            }
        }

        val lastQuery = current.toString().trim()
        if (lastQuery.isNotEmpty()) {
            queries.add(lastQuery)
        }

        return queries
    }

    /**
     * Extracts a dollar quote tag from the SQL string starting at the given position.
     * Returns the tag (e.g., "$$" or "$tag$") if found, or null if not a valid dollar quote.
     */
    private fun extractDollarQuoteTag(
        sql: String,
        startIndex: Int,
    ): String? {
        if (startIndex >= sql.length || sql[startIndex] != '$') return null

        var i = startIndex + 1
        // Empty tag: $$
        if (i < sql.length && sql[i] == '$') {
            return "$$"
        }

        // Named tag: $identifier$
        // Tag must start with letter or underscore, followed by letters, digits, or underscores
        if (i >= sql.length) return null
        val firstChar = sql[i]
        if (!firstChar.isLetter() && firstChar != '_') return null

        i++
        while (i < sql.length) {
            val c = sql[i]
            if (c == '$') {
                return sql.substring(startIndex, i + 1)
            }
            if (!c.isLetterOrDigit() && c != '_') {
                return null
            }
            i++
        }
        return null
    }
}
