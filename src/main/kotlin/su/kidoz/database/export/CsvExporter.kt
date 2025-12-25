package su.kidoz.database.export

import su.kidoz.core.model.ResultColumn

class CsvExporter {
    fun export(
        columns: List<ResultColumn>,
        rows: List<List<Any?>>,
    ): String =
        buildString {
            // Header
            appendLine(columns.joinToString(",") { escapeCsv(it.label) })

            // Data rows
            rows.forEach { row ->
                appendLine(
                    row.joinToString(",") { cell ->
                        escapeCsv(cell?.toString() ?: "")
                    },
                )
            }
        }

    private fun escapeCsv(value: String): String =
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
}
