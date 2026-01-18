package su.kidoz.feature.explorer

import su.kidoz.core.model.*
import su.kidoz.mvi.UiState

data class ExplorerState(
    val connectionId: String? = null,
    val databaseType: DatabaseType? = null,
    val databases: List<DatabaseInfo> = emptyList(),
    val schemas: List<SchemaInfo> = emptyList(),
    val tables: List<TableInfo> = emptyList(),
    val views: List<ViewInfo> = emptyList(),
    val expandedNodes: Set<String> = emptySet(),
    val selectedNode: TreeNode? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val tableDetails: TableDetails? = null,
    val indexDialogState: ElasticsearchIndexDialogState? = null,
    val deleteConfirmation: DeleteConfirmationState? = null,
    // Schema-organized data for JDBC databases
    val tablesBySchema: Map<String, List<TableInfo>> = emptyMap(),
    val viewsBySchema: Map<String, List<ViewInfo>> = emptyMap(),
    val loadedSchemas: Set<String> = emptySet(),
    val loadingSchemas: Set<String> = emptySet(),
    val defaultSchema: String? = null,
) : UiState {
    /** Get the terminology for the current database type */
    val terminology: DatabaseTerminology?
        get() = databaseType?.terminology()

    /** Check if database uses schemas (PostgreSQL, MySQL, etc.) */
    val usesSchemas: Boolean
        get() = databaseType in listOf(DatabaseType.POSTGRESQL, DatabaseType.MYSQL, DatabaseType.H2)

    /** Get tables for a specific schema */
    fun getTablesForSchema(schemaName: String): List<TableInfo> = tablesBySchema[schemaName] ?: emptyList()

    /** Get views for a specific schema */
    fun getViewsForSchema(schemaName: String): List<ViewInfo> = viewsBySchema[schemaName] ?: emptyList()

    /** Check if a schema's contents have been loaded */
    fun isSchemaLoaded(schemaName: String): Boolean = loadedSchemas.contains(schemaName)

    /** Check if a schema is currently loading */
    fun isSchemaLoading(schemaName: String): Boolean = loadingSchemas.contains(schemaName)
}

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

    data class ColumnsFolder(
        val tableName: String,
        val schemaName: String?,
    ) : TreeNode("columns:${schemaName ?: ""}:$tableName", "Columns", TreeNodeType.FOLDER)

    data class IndexesFolder(
        val tableName: String,
        val schemaName: String?,
    ) : TreeNode("indexes:${schemaName ?: ""}:$tableName", "Indexes", TreeNodeType.FOLDER)

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

// ==================== Elasticsearch Index Dialog ====================

/**
 * Mode for the Elasticsearch index dialog.
 */
enum class IndexDialogMode {
    CREATE,
    EDIT_SETTINGS,
    EDIT_MAPPINGS,
}

/**
 * State for the Elasticsearch index create/edit dialog.
 */
data class ElasticsearchIndexDialogState(
    val mode: IndexDialogMode = IndexDialogMode.CREATE,
    val indexName: String = "",
    val definitionJson: String = DEFAULT_INDEX_TEMPLATE,
    val isProcessing: Boolean = false,
    val error: String? = null,
) {
    val isValid: Boolean
        get() =
            indexName.isNotBlank() &&
                indexName.matches(INDEX_NAME_PATTERN) &&
                definitionJson.isNotBlank()

    val title: String
        get() =
            when (mode) {
                IndexDialogMode.CREATE -> "Create Index"
                IndexDialogMode.EDIT_SETTINGS -> "Edit Index Settings"
                IndexDialogMode.EDIT_MAPPINGS -> "Edit Index Mappings"
            }

    companion object {
        private val INDEX_NAME_PATTERN = Regex("[a-z0-9][a-z0-9_.-]*")

        val DEFAULT_INDEX_TEMPLATE =
            """
            {
              "settings": {
                "number_of_shards": 1,
                "number_of_replicas": 0,
                "analysis": {
                  "analyzer": {},
                  "tokenizer": {}
                }
              },
              "mappings": {
                "properties": {
                  "field_name": { "type": "text" }
                }
              }
            }
            """.trimIndent()
    }
}

/**
 * State for delete confirmation dialog.
 */
data class DeleteConfirmationState(
    val indexName: String,
) {
    val message: String
        get() = "Are you sure you want to delete index '$indexName'? This action cannot be undone."
}
