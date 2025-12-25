package su.kidoz.database.export

import su.kidoz.core.model.ResultColumn
import java.sql.Types

class SqlExporter {
    fun exportInsert(
        tableName: String,
        columns: List<ResultColumn>,
        rows: List<List<Any?>>,
    ): String =
        buildString {
            rows.forEach { row ->
                append("INSERT INTO ")
                append(escapeIdentifier(tableName))
                append(" (")
                append(columns.joinToString(", ") { escapeIdentifier(it.label) })
                append(") VALUES (")
                append(
                    row
                        .mapIndexed { index, value ->
                            formatValue(value, columns[index].jdbcType)
                        }.joinToString(", "),
                )
                appendLine(");")
            }
        }

    fun exportUpdate(
        tableName: String,
        columns: List<ResultColumn>,
        rows: List<List<Any?>>,
        keyColumnIndices: List<Int>,
    ): String =
        buildString {
            rows.forEach { row ->
                append("UPDATE ")
                append(escapeIdentifier(tableName))
                append(" SET ")

                val nonKeyColumns = columns.filterIndexed { index, _ -> index !in keyColumnIndices }
                val setClauses =
                    nonKeyColumns.mapIndexed { _, column ->
                        val colIndex = columns.indexOf(column)
                        "${escapeIdentifier(column.label)} = ${formatValue(row[colIndex], column.jdbcType)}"
                    }
                append(setClauses.joinToString(", "))

                append(" WHERE ")
                val whereClauses =
                    keyColumnIndices.map { index ->
                        "${escapeIdentifier(columns[index].label)} = ${formatValue(row[index], columns[index].jdbcType)}"
                    }
                append(whereClauses.joinToString(" AND "))
                appendLine(";")
            }
        }

    private fun formatValue(
        value: Any?,
        jdbcType: Int,
    ): String {
        if (value == null) return "NULL"

        return when (jdbcType) {
            Types.BOOLEAN, Types.BIT -> if (value as? Boolean == true) "TRUE" else "FALSE"
            Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT,
            Types.FLOAT, Types.REAL, Types.DOUBLE, Types.DECIMAL, Types.NUMERIC,
            -> value.toString()
            Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> "NULL /* BINARY */"
            else -> "'${escapeString(value.toString())}'"
        }
    }

    private fun escapeIdentifier(identifier: String): String = "\"$identifier\""

    private fun escapeString(value: String): String = value.replace("'", "''")
}
