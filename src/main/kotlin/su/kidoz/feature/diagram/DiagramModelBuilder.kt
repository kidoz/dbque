package su.kidoz.feature.diagram

import su.kidoz.core.model.ColumnInfo
import su.kidoz.core.model.ForeignKeyInfo
import su.kidoz.core.model.PrimaryKeyInfo
import su.kidoz.core.model.TableInfo

data class TableMetadata(
    val table: TableInfo,
    val columns: List<ColumnInfo>,
    val primaryKey: PrimaryKeyInfo?,
    val foreignKeys: List<ForeignKeyInfo>,
)

data class DiagramModel(
    val tables: List<DiagramTable>,
    val relationships: List<DiagramRelationship>,
)

object DiagramModelBuilder {
    private const val CARD_WIDTH = 260f
    private const val COLUMN_GAP = 96f
    private const val ROW_GAP = 86f
    private const val COLUMNS_PER_ROW = 4

    fun build(metadata: List<TableMetadata>): DiagramModel {
        val tables =
            metadata.mapIndexed { index, item ->
                val row = index / COLUMNS_PER_ROW
                val column = index % COLUMNS_PER_ROW
                val primaryKeyColumns =
                    item.primaryKey
                        ?.columns
                        .orEmpty()
                        .toSet()
                val foreignKeyColumns = item.foreignKeys.flatMap { it.columns }.toSet()
                val visibleColumns =
                    item.columns.ifEmpty {
                        item.table.columns
                    }

                DiagramTable(
                    id = tableId(item.table.schema, item.table.name),
                    name = item.table.name,
                    schema = item.table.schema,
                    x = 32f + column * (CARD_WIDTH + COLUMN_GAP),
                    y = 32f + row * (260f + ROW_GAP),
                    columns =
                        visibleColumns.map { columnInfo ->
                            DiagramColumn(
                                id = "${tableId(item.table.schema, item.table.name)}:${columnInfo.name}",
                                name = columnInfo.name,
                                type = columnInfo.typeDisplay,
                                nullable = columnInfo.nullable,
                                isPrimaryKey = columnInfo.name in primaryKeyColumns,
                                isForeignKey = columnInfo.name in foreignKeyColumns,
                                autoIncrement = columnInfo.autoIncrement,
                            )
                        },
                )
            }

        val tableIdsByName = tables.associateBy { it.name }.mapValues { it.value.id }
        val relationships =
            metadata.flatMap { item ->
                val sourceId = tableId(item.table.schema, item.table.name)
                item.foreignKeys.mapNotNull { foreignKey ->
                    val targetId = tableIdsByName[foreignKey.referencedTable]
                    targetId?.let {
                        DiagramRelationship(
                            id = relationshipId(sourceId, foreignKey),
                            name = foreignKey.name,
                            sourceTableId = sourceId,
                            sourceColumns = foreignKey.columns,
                            targetTableId = it,
                            targetColumns = foreignKey.referencedColumns,
                            deleteRule = foreignKey.deleteRule,
                        )
                    }
                }
            }

        return DiagramModel(tables = tables, relationships = relationships)
    }

    fun tableId(
        schema: String?,
        table: String,
    ): String = "${schema.orEmpty()}.$table"

    private fun relationshipId(
        sourceId: String,
        foreignKey: ForeignKeyInfo,
    ): String {
        val keyName = foreignKey.name ?: foreignKey.columns.joinToString("_")
        return "$sourceId->$keyName"
    }
}
