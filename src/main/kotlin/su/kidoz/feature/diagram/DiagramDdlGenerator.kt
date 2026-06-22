package su.kidoz.feature.diagram

object DiagramDdlGenerator {
    fun generate(
        tables: List<DiagramTable>,
        relationships: List<DiagramRelationship>,
        dialect: DiagramDdlDialect = DiagramDdlDialect.PostgreSql,
    ): String {
        val statements = mutableListOf<String>()
        tables.filter { it.isDraft }.forEach { table ->
            statements += createTableStatement(table, tables, relationships, dialect)
        }
        relationships.filter { it.isDraft }.forEach { relationship ->
            val source = tables.firstOrNull { it.id == relationship.sourceTableId }
            val target = tables.firstOrNull { it.id == relationship.targetTableId }
            if (source != null && target != null) {
                if (dialect.supportsAlterTableAddForeignKey) {
                    statements += dialect.alterTableAddForeignKey(source, target, relationship)
                } else if (!source.isDraft) {
                    statements += dialect.unsupportedAlterTableForeignKeyComment(source, target, relationship)
                }
            }
        }
        return statements.joinToString(separator = "\n\n")
    }

    private fun createTableStatement(
        table: DiagramTable,
        tables: List<DiagramTable>,
        relationships: List<DiagramRelationship>,
        dialect: DiagramDdlDialect,
    ): String {
        val columns =
            table.columns.ifEmpty {
                listOf(
                    DiagramColumn(
                        id = "${table.id}:id",
                        name = "id",
                        type = "INTEGER",
                        nullable = false,
                        isPrimaryKey = true,
                        autoIncrement = true,
                        isDraft = true,
                    ),
                )
            }
        val primaryKeys = columns.filter { it.isPrimaryKey }.map { it.name }
        val inlinePrimaryKey = dialect == DiagramDdlDialect.Sqlite && primaryKeys.size == 1
        val primaryKeyConstraint =
            if (inlinePrimaryKey) {
                null
            } else {
                dialect.primaryKeyConstraint(primaryKeys)
            }
        val inlineForeignKeys =
            if (dialect.supportsAlterTableAddForeignKey) {
                emptyList()
            } else {
                relationships
                    .filter { it.isDraft && it.sourceTableId == table.id }
                    .mapNotNull { relationship ->
                        val target = tables.firstOrNull { it.id == relationship.targetTableId }
                        target?.let {
                            val constraintName =
                                relationship.name
                                    ?.takeIf { name -> name.isNotBlank() }
                                    ?: "fk_${table.name}_${target.name}"
                            "    ${dialect.foreignKeyConstraint(
                                name = constraintName,
                                sourceColumns = relationship.sourceColumns,
                                target = target,
                                targetColumns = relationship.targetColumns,
                                deleteRule = relationship.deleteRule,
                            )}"
                        }
                    }
            }
        val lines =
            columns.map { column ->
                dialect.columnDefinition(column, primaryKeys)
            } +
                listOfNotNull(primaryKeyConstraint) +
                inlineForeignKeys

        return buildString {
            appendLine("CREATE TABLE ${dialect.qualifiedName(table)} (")
            appendLine(lines.joinToString(",\n"))
            append(");")
        }
    }
}
