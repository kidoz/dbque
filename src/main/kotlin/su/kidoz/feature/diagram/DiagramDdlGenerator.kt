package su.kidoz.feature.diagram

import su.kidoz.core.model.ForeignKeyRule

object DiagramDdlGenerator {
    fun generate(
        tables: List<DiagramTable>,
        relationships: List<DiagramRelationship>,
    ): String {
        val statements = mutableListOf<String>()
        tables.filter { it.isDraft }.forEach { table ->
            statements += createTableStatement(table)
        }
        relationships.filter { it.isDraft }.forEach { relationship ->
            val source = tables.firstOrNull { it.id == relationship.sourceTableId }
            val target = tables.firstOrNull { it.id == relationship.targetTableId }
            if (source != null && target != null) {
                statements += alterTableStatement(source, target, relationship)
            }
        }
        return statements.joinToString(separator = "\n\n")
    }

    private fun createTableStatement(table: DiagramTable): String {
        val columns =
            table.columns.ifEmpty {
                listOf(
                    DiagramColumn(
                        id = "${table.id}:id",
                        name = "id",
                        type = "INTEGER",
                        nullable = false,
                        isPrimaryKey = true,
                        isDraft = true,
                    ),
                )
            }
        val primaryKeys = columns.filter { it.isPrimaryKey }.map { it.name }
        val lines =
            columns.map { column ->
                buildString {
                    append("    ")
                    append(quote(column.name))
                    append(" ")
                    append(column.type.ifBlank { "TEXT" })
                    if (!column.nullable || column.isPrimaryKey) {
                        append(" NOT NULL")
                    }
                }
            } +
                if (primaryKeys.isNotEmpty()) {
                    listOf("    PRIMARY KEY (${primaryKeys.joinToString { quote(it) }})")
                } else {
                    emptyList()
                }

        return buildString {
            appendLine("CREATE TABLE ${qualifiedName(table)} (")
            appendLine(lines.joinToString(",\n"))
            append(");")
        }
    }

    private fun alterTableStatement(
        source: DiagramTable,
        target: DiagramTable,
        relationship: DiagramRelationship,
    ): String =
        buildString {
            val name = relationship.name?.takeIf { it.isNotBlank() } ?: "fk_${source.name}_${target.name}"
            append("ALTER TABLE ${qualifiedName(source)} ADD CONSTRAINT ${quote(name)} ")
            append("FOREIGN KEY (${relationship.sourceColumns.joinToString { quote(it) }}) ")
            append("REFERENCES ${qualifiedName(target)} (${relationship.targetColumns.joinToString { quote(it) }})")
            if (relationship.deleteRule != ForeignKeyRule.NO_ACTION) {
                append(" ON DELETE ${relationship.deleteRule.toSql()}")
            }
            append(";")
        }

    private fun qualifiedName(table: DiagramTable): String = table.schema?.let { "${quote(it)}.${quote(table.name)}" } ?: quote(table.name)

    private fun quote(identifier: String): String = "\"${identifier.replace("\"", "\"\"")}\""

    private fun ForeignKeyRule.toSql(): String = name.replace('_', ' ')
}
