package su.kidoz.feature.diagram

import su.kidoz.core.model.DatabaseType
import su.kidoz.core.model.ForeignKeyRule

sealed class DiagramDdlDialect {
    abstract val name: String
    abstract val defaultTextType: String
    abstract val defaultIntegerType: String

    open val supportsAlterTableAddForeignKey: Boolean = true

    abstract fun quoteIdentifier(identifier: String): String

    fun qualifiedName(table: DiagramTable): String =
        table.schema
            ?.takeIf { it.isNotBlank() }
            ?.let { "${quoteIdentifier(it)}.${quoteIdentifier(table.name)}" }
            ?: quoteIdentifier(table.name)

    fun columnDefinition(
        column: DiagramColumn,
        primaryKeys: List<String>,
    ): String {
        val autoIncrement = column.usesAutoIncrement(primaryKeys)
        val inlinePrimaryKey = usesInlinePrimaryKey(column, primaryKeys, autoIncrement)
        return buildString {
            append("    ")
            append(quoteIdentifier(column.name))
            append(" ")
            append(columnType(column, autoIncrement))
            if (inlinePrimaryKey) {
                append(" PRIMARY KEY")
            }
            if ((!column.nullable || column.isPrimaryKey) && (!inlinePrimaryKey || !autoIncrement)) {
                append(" NOT NULL")
            }
            append(columnSuffix(column, autoIncrement))
        }
    }

    fun primaryKeyConstraint(primaryKeys: List<String>): String? =
        primaryKeys
            .takeIf { it.isNotEmpty() }
            ?.joinToString(
                prefix = "    PRIMARY KEY (",
                postfix = ")",
                transform = ::quoteIdentifier,
            )

    fun foreignKeyConstraint(
        name: String,
        sourceColumns: List<String>,
        target: DiagramTable,
        targetColumns: List<String>,
        deleteRule: ForeignKeyRule,
    ): String =
        buildString {
            append("CONSTRAINT ${quoteIdentifier(name)} ")
            append("FOREIGN KEY (${sourceColumns.joinToString(transform = ::quoteIdentifier)}) ")
            append("REFERENCES ${qualifiedName(target)} (${targetColumns.joinToString(transform = ::quoteIdentifier)})")
            foreignKeyDeleteRule(deleteRule)?.let { append(" ON DELETE $it") }
        }

    fun alterTableAddForeignKey(
        source: DiagramTable,
        target: DiagramTable,
        relationship: DiagramRelationship,
    ): String {
        val name = relationship.constraintName(source, target)
        return "ALTER TABLE ${qualifiedName(source)} ADD ${foreignKeyConstraint(
            name = name,
            sourceColumns = relationship.sourceColumns,
            target = target,
            targetColumns = relationship.targetColumns,
            deleteRule = relationship.deleteRule,
        )};"
    }

    open fun unsupportedAlterTableForeignKeyComment(
        source: DiagramTable,
        target: DiagramTable,
        relationship: DiagramRelationship,
    ): String =
        "-- $name cannot add foreign keys to existing tables with ALTER TABLE. " +
            "Rebuild ${qualifiedName(source)} to add ${relationship.constraintName(source, target)}."

    protected open fun usesInlinePrimaryKey(
        column: DiagramColumn,
        primaryKeys: List<String>,
        autoIncrement: Boolean,
    ): Boolean = false

    protected open fun columnType(
        column: DiagramColumn,
        autoIncrement: Boolean,
    ): String =
        normalizedType(column.type).ifBlank {
            if (column.isPrimaryKey || autoIncrement) {
                defaultIntegerType
            } else {
                defaultTextType
            }
        }

    protected open fun columnSuffix(
        column: DiagramColumn,
        autoIncrement: Boolean,
    ): String = ""

    protected open fun foreignKeyDeleteRule(rule: ForeignKeyRule): String? =
        when (rule) {
            ForeignKeyRule.NO_ACTION -> null
            ForeignKeyRule.SET_NULL -> "SET NULL"
            ForeignKeyRule.SET_DEFAULT -> "SET DEFAULT"
            else -> rule.name.replace('_', ' ')
        }

    protected open fun normalizedType(type: String): String = type.trim()

    private fun DiagramColumn.usesAutoIncrement(primaryKeys: List<String>): Boolean =
        autoIncrement &&
            isPrimaryKey &&
            primaryKeys.size == 1 &&
            primaryKeys.single() == name &&
            normalizedType(type).isIntegerType()

    protected fun String.isIntegerType(): Boolean =
        trim()
            .substringBefore('(')
            .uppercase()
            .let { it in setOf("INT", "INTEGER", "BIGINT", "SMALLINT", "TINYINT", "MEDIUMINT") }

    data object PostgreSql : DiagramDdlDialect() {
        override val name: String = "PostgreSQL"
        override val defaultTextType: String = "TEXT"
        override val defaultIntegerType: String = "INTEGER"

        override fun quoteIdentifier(identifier: String): String = "\"${identifier.replace("\"", "\"\"")}\""

        override fun columnType(
            column: DiagramColumn,
            autoIncrement: Boolean,
        ): String {
            val type = normalizedType(column.type)
            return if (autoIncrement) {
                "${type.ifBlank { defaultIntegerType }} GENERATED BY DEFAULT AS IDENTITY"
            } else {
                type.ifBlank {
                    if (column.isPrimaryKey) defaultIntegerType else defaultTextType
                }
            }
        }
    }

    data object MySql : DiagramDdlDialect() {
        override val name: String = "MySQL"
        override val defaultTextType: String = "VARCHAR(255)"
        override val defaultIntegerType: String = "INT"

        override fun quoteIdentifier(identifier: String): String = "`${identifier.replace("`", "``")}`"

        override fun columnSuffix(
            column: DiagramColumn,
            autoIncrement: Boolean,
        ): String = if (autoIncrement) " AUTO_INCREMENT" else ""

        override fun normalizedType(type: String): String =
            when (val normalized = type.trim()) {
                "INTEGER" -> "INT"
                else -> normalized
            }

        override fun foreignKeyDeleteRule(rule: ForeignKeyRule): String? =
            when (rule) {
                ForeignKeyRule.SET_DEFAULT -> null
                else -> super.foreignKeyDeleteRule(rule)
            }
    }

    data object Sqlite : DiagramDdlDialect() {
        override val name: String = "SQLite"
        override val defaultTextType: String = "TEXT"
        override val defaultIntegerType: String = "INTEGER"
        override val supportsAlterTableAddForeignKey: Boolean = false

        override fun quoteIdentifier(identifier: String): String = "\"${identifier.replace("\"", "\"\"")}\""

        override fun usesInlinePrimaryKey(
            column: DiagramColumn,
            primaryKeys: List<String>,
            autoIncrement: Boolean,
        ): Boolean = autoIncrement || primaryKeys == listOf(column.name)

        override fun columnType(
            column: DiagramColumn,
            autoIncrement: Boolean,
        ): String =
            if (autoIncrement) {
                "INTEGER"
            } else {
                super.columnType(column, autoIncrement)
            }

        override fun columnSuffix(
            column: DiagramColumn,
            autoIncrement: Boolean,
        ): String = if (autoIncrement) " AUTOINCREMENT" else ""
    }

    data object H2 : DiagramDdlDialect() {
        override val name: String = "H2"
        override val defaultTextType: String = "VARCHAR(255)"
        override val defaultIntegerType: String = "INTEGER"

        override fun quoteIdentifier(identifier: String): String = "\"${identifier.replace("\"", "\"\"")}\""

        override fun columnType(
            column: DiagramColumn,
            autoIncrement: Boolean,
        ): String {
            val type = normalizedType(column.type)
            return if (autoIncrement) {
                "${type.ifBlank { defaultIntegerType }} GENERATED BY DEFAULT AS IDENTITY"
            } else {
                type.ifBlank {
                    if (column.isPrimaryKey) defaultIntegerType else defaultTextType
                }
            }
        }
    }

    companion object {
        fun forDatabaseType(databaseType: DatabaseType?): DiagramDdlDialect =
            when (databaseType) {
                DatabaseType.MYSQL,
                DatabaseType.STARROCKS,
                -> MySql

                DatabaseType.SQLITE -> Sqlite

                DatabaseType.H2 -> H2

                else -> PostgreSql
            }
    }
}

private fun DiagramRelationship.constraintName(
    source: DiagramTable,
    target: DiagramTable,
): String = name?.takeIf { it.isNotBlank() } ?: "fk_${source.name}_${target.name}"
