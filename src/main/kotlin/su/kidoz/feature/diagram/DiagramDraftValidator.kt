package su.kidoz.feature.diagram

object DiagramDraftValidator {
    fun validate(
        tables: List<DiagramTable>,
        relationships: List<DiagramRelationship>,
    ): List<String> {
        val issues = mutableListOf<String>()

        validateTables(tables, issues)
        validateRelationships(tables, relationships, issues)

        return issues
    }

    private fun validateTables(
        tables: List<DiagramTable>,
        issues: MutableList<String>,
    ) {
        val duplicateTables =
            tables
                .groupBy { "${it.schema.orEmpty()}.${it.name.trim().lowercase()}" }
                .filterKeys { !it.endsWith(".") }
                .filterValues { it.size > 1 }

        duplicateTables.values.forEach { duplicates ->
            val first = duplicates.first()
            issues += "Duplicate table name: ${first.displayName}"
        }

        tables.filter { it.isDraft }.forEach { table ->
            if (table.name.isBlank()) {
                issues += "Draft table name cannot be blank"
            }

            val duplicateColumns =
                table.columns
                    .groupBy { it.name.trim().lowercase() }
                    .filterKeys { it.isNotBlank() }
                    .filterValues { it.size > 1 }

            duplicateColumns.values.forEach { duplicates ->
                issues += "Duplicate column '${duplicates.first().name}' in ${table.displayName}"
            }

            table.columns.forEach { column ->
                if (column.name.isBlank()) {
                    issues += "Column name cannot be blank in ${table.displayName}"
                }
                if (column.type.isBlank()) {
                    issues += "Column '${column.name}' in ${table.displayName} needs a type"
                }
            }
        }
    }

    private fun validateRelationships(
        tables: List<DiagramTable>,
        relationships: List<DiagramRelationship>,
        issues: MutableList<String>,
    ) {
        relationships.filter { it.isDraft }.forEach { relationship ->
            val relationshipName = relationship.name ?: relationship.id
            val source = tables.firstOrNull { it.id == relationship.sourceTableId }
            val target = tables.firstOrNull { it.id == relationship.targetTableId }

            if (source == null || target == null) {
                issues += "Draft relationship '$relationshipName' references a missing table"
                return@forEach
            }

            relationship.sourceColumns.forEach { column ->
                if (source.columns.none { it.name == column }) {
                    issues += "Relationship '$relationshipName' references missing column ${source.displayName}.$column"
                }
            }

            relationship.targetColumns.forEach { column ->
                if (target.columns.none { it.name == column }) {
                    issues += "Relationship '$relationshipName' references missing column ${target.displayName}.$column"
                }
            }
        }
    }
}
