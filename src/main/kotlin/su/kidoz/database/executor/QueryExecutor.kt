package su.kidoz.database.executor

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import su.kidoz.core.model.QueryExecution
import su.kidoz.core.model.QueryExecutionResult
import su.kidoz.core.model.QueryResult
import su.kidoz.core.model.ResultColumn
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types
import java.util.concurrent.CancellationException

class QueryExecutor {
    private val logger = KotlinLogging.logger {}

    suspend fun execute(
        connection: Connection,
        execution: QueryExecution,
    ): QueryExecutionResult =
        withContext(Dispatchers.IO) {
            try {
                connection.createStatement().use { statement ->
                    val cancelHandler =
                        coroutineContext.job.invokeOnCompletion { cause ->
                            if (cause is kotlinx.coroutines.CancellationException || cause is CancellationException) {
                                try {
                                    statement.cancel()
                                    logger.debug { "Statement cancelled successfully" }
                                } catch (e: Exception) {
                                    logger.warn(e) { "Failed to cancel statement" }
                                }
                            }
                        }

                    try {
                        statement.apply {
                            if (execution.maxRows > 0) maxRows = execution.maxRows
                            if (execution.timeout > 0) queryTimeout = execution.timeout
                            if (execution.fetchSize > 0) fetchSize = execution.fetchSize
                        }

                        val queries =
                            su.kidoz.feature.editor.QuerySplitter
                                .getAllQueries(execution.query)
                                .map { it.query }
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
                    } finally {
                        cancelHandler.dispose()
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
}
