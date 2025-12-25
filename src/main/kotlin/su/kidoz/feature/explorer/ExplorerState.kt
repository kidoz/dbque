package su.kidoz.feature.explorer

import su.kidoz.core.model.*
import su.kidoz.mvi.UiState

data class ExplorerState(
    val connectionId: String? = null,
    val databases: List<DatabaseInfo> = emptyList(),
    val schemas: List<SchemaInfo> = emptyList(),
    val tables: List<TableInfo> = emptyList(),
    val views: List<ViewInfo> = emptyList(),
    val expandedNodes: Set<String> = emptySet(),
    val selectedNode: TreeNode? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val tableDetails: TableDetails? = null,
) : UiState

data class TableDetails(
    val table: TableInfo,
    val columns: List<ColumnInfo>,
    val primaryKey: PrimaryKeyInfo?,
    val foreignKeys: List<ForeignKeyInfo>,
    val indexes: List<IndexInfo>,
)

sealed class TreeNode(
    val id: String,
    val name: String,
    val type: TreeNodeType,
) {
    data class DatabaseNode(
        val database: DatabaseInfo,
    ) : TreeNode("db:${database.name}", database.name, TreeNodeType.DATABASE)

    data class SchemaNode(
        val schema: SchemaInfo,
        val databaseName: String?,
    ) : TreeNode("schema:${databaseName ?: ""}:${schema.name}", schema.name, TreeNodeType.SCHEMA)

    data class TablesFolder(
        val schemaName: String?,
        val databaseName: String?,
    ) : TreeNode("tables:${databaseName ?: ""}:${schemaName ?: ""}", "Tables", TreeNodeType.FOLDER)

    data class ViewsFolder(
        val schemaName: String?,
        val databaseName: String?,
    ) : TreeNode("views:${databaseName ?: ""}:${schemaName ?: ""}", "Views", TreeNodeType.FOLDER)

    data class TableNode(
        val table: TableInfo,
    ) : TreeNode("table:${table.schema ?: ""}:${table.name}", table.name, TreeNodeType.TABLE)

    data class ViewNode(
        val view: ViewInfo,
    ) : TreeNode("view:${view.schema ?: ""}:${view.name}", view.name, TreeNodeType.VIEW)

    data class ColumnNode(
        val column: ColumnInfo,
        val tableName: String,
    ) : TreeNode("column:$tableName:${column.name}", column.name, TreeNodeType.COLUMN)

    data class IndexNode(
        val index: IndexInfo,
        val tableName: String,
    ) : TreeNode("index:$tableName:${index.name}", index.name, TreeNodeType.INDEX)
}

enum class TreeNodeType {
    DATABASE,
    SCHEMA,
    FOLDER,
    TABLE,
    VIEW,
    COLUMN,
    INDEX,
}
