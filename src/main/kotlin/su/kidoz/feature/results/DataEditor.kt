package su.kidoz.feature.results

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.kidoz.core.model.QueryResult
import su.kidoz.core.model.ResultColumn
import su.kidoz.database.ConnectionManager
import java.sql.Types

data class CellEdit(
    val rowIndex: Int,
    val columnIndex: Int,
    val oldValue: Any?,
    val newValue: Any?,
    val column: ResultColumn,
)

data class PendingChanges(
    val tableName: String,
    val schema: String?,
    val edits: List<CellEdit>,
    val primaryKeyColumns: List<Int>,
)

class DataEditor(
    private val connectionManager: ConnectionManager,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun applyChanges(
        changes: PendingChanges,
        result: QueryResult,
    ): Result<Int> =
        withContext(Dispatchers.IO) {
            val connection =
                connectionManager.activeConnection?.getConnection()
                    ?: return@withContext Result.failure(Exception("No active connection"))

            val driver =
                connectionManager.activeConnection?.driver
                    ?: return@withContext Result.failure(Exception("No driver"))

            runCatching {
                var affectedRows = 0

                changes.edits.groupBy { it.rowIndex }.forEach { (rowIndex, rowEdits) ->
                    val originalRow =
                        result.rows.getOrNull(rowIndex)
                            ?: throw Exception("Row $rowIndex not found")

                    val sql =
                        buildUpdateStatement(
                            changes.tableName,
                            changes.schema,
                            rowEdits,
                            originalRow,
                            result.columns,
                            changes.primaryKeyColumns,
                            driver::escapeIdentifier,
                        )

                    logger.debug { "Executing update: $sql" }

                    connection.createStatement().use { stmt ->
                        affectedRows += stmt.executeUpdate(sql)
                    }
                }

                affectedRows
            }
        }

    suspend fun insertRow(
        tableName: String,
        schema: String?,
        columns: List<ResultColumn>,
        values: List<Any?>,
    ): Result<Int> =
        withContext(Dispatchers.IO) {
            val connection =
                connectionManager.activeConnection?.getConnection()
                    ?: return@withContext Result.failure(Exception("No active connection"))

            val driver =
                connectionManager.activeConnection?.driver
                    ?: return@withContext Result.failure(Exception("No driver"))

            runCatching {
                val escapedTable =
                    if (schema != null) {
                        "${driver.escapeIdentifier(schema)}.${driver.escapeIdentifier(tableName)}"
                    } else {
                        driver.escapeIdentifier(tableName)
                    }

                val columnNames = columns.joinToString(", ") { driver.escapeIdentifier(it.name) }
                val valuePlaceholders =
                    values
                        .mapIndexed { index, value ->
                            formatValueForSql(value, columns[index].jdbcType)
                        }.joinToString(", ")

                val sql = "INSERT INTO $escapedTable ($columnNames) VALUES ($valuePlaceholders)"

                logger.debug { "Executing insert: $sql" }

                connection.createStatement().use { stmt ->
                    stmt.executeUpdate(sql)
                }
            }
        }

    suspend fun deleteRows(
        tableName: String,
        schema: String?,
        columns: List<ResultColumn>,
        rows: List<List<Any?>>,
        primaryKeyColumns: List<Int>,
    ): Result<Int> =
        withContext(Dispatchers.IO) {
            val connection =
                connectionManager.activeConnection?.getConnection()
                    ?: return@withContext Result.failure(Exception("No active connection"))

            val driver =
                connectionManager.activeConnection?.driver
                    ?: return@withContext Result.failure(Exception("No driver"))

            if (primaryKeyColumns.isEmpty()) {
                return@withContext Result.failure(Exception("No primary key defined - cannot delete rows safely"))
            }

            runCatching {
                var affectedRows = 0

                val escapedTable =
                    if (schema != null) {
                        "${driver.escapeIdentifier(schema)}.${driver.escapeIdentifier(tableName)}"
                    } else {
                        driver.escapeIdentifier(tableName)
                    }

                rows.forEach { row ->
                    val whereClause =
                        primaryKeyColumns.joinToString(" AND ") { pkIndex ->
                            val column = columns[pkIndex]
                            val value = row[pkIndex]
                            "${driver.escapeIdentifier(column.name)} = ${formatValueForSql(value, column.jdbcType)}"
                        }

                    val sql = "DELETE FROM $escapedTable WHERE $whereClause"

                    logger.debug { "Executing delete: $sql" }

                    connection.createStatement().use { stmt ->
                        affectedRows += stmt.executeUpdate(sql)
                    }
                }

                affectedRows
            }
        }

    private fun buildUpdateStatement(
        tableName: String,
        schema: String?,
        edits: List<CellEdit>,
        originalRow: List<Any?>,
        columns: List<ResultColumn>,
        primaryKeyColumns: List<Int>,
        escapeIdentifier: (String) -> String,
    ): String {
        val escapedTable =
            if (schema != null) {
                "${escapeIdentifier(schema)}.${escapeIdentifier(tableName)}"
            } else {
                escapeIdentifier(tableName)
            }

        val setClauses =
            edits.joinToString(", ") { edit ->
                "${escapeIdentifier(edit.column.name)} = ${formatValueForSql(edit.newValue, edit.column.jdbcType)}"
            }

        val whereClause =
            if (primaryKeyColumns.isNotEmpty()) {
                primaryKeyColumns.joinToString(" AND ") { pkIndex ->
                    val column = columns[pkIndex]
                    val value = originalRow[pkIndex]
                    "${escapeIdentifier(column.name)} = ${formatValueForSql(value, column.jdbcType)}"
                }
            } else {
                // Fallback: use all columns in WHERE clause
                columns
                    .mapIndexed { index, column ->
                        val value = originalRow[index]
                        if (value == null) {
                            "${escapeIdentifier(column.name)} IS NULL"
                        } else {
                            "${escapeIdentifier(column.name)} = ${formatValueForSql(value, column.jdbcType)}"
                        }
                    }.joinToString(" AND ")
            }

        return "UPDATE $escapedTable SET $setClauses WHERE $whereClause"
    }

    private fun formatValueForSql(
        value: Any?,
        jdbcType: Int,
    ): String {
        if (value == null) return "NULL"

        return when (jdbcType) {
            Types.BOOLEAN, Types.BIT -> if (value as? Boolean == true) "TRUE" else "FALSE"
            Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT,
            Types.FLOAT, Types.REAL, Types.DOUBLE, Types.DECIMAL, Types.NUMERIC,
            -> value.toString()
            Types.DATE -> "'$value'"
            Types.TIME, Types.TIME_WITH_TIMEZONE -> "'$value'"
            Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "'$value'"
            else -> "'${escapeString(value.toString())}'"
        }
    }

    private fun escapeString(value: String): String = value.replace("'", "''")
}
