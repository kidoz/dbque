package su.kidoz.feature.diagram

import su.kidoz.core.model.DatabaseType
import su.kidoz.core.model.ForeignKeyRule
import su.kidoz.mvi.UiState

data class DiagramState(
    val connectionId: String? = null,
    val databaseType: DatabaseType? = null,
    val schemas: List<String> = emptyList(),
    val selectedSchema: String? = null,
    val tables: List<DiagramTable> = emptyList(),
    val relationships: List<DiagramRelationship> = emptyList(),
    val selectedElement: DiagramSelection = DiagramSelection.None,
    val isLoading: Boolean = false,
    val error: String? = null,
    val zoom: Float = 1f,
    val showKeysOnly: Boolean = false,
    val showDdlPreview: Boolean = false,
    val ddlPreview: String = "",
    val maxTables: Int = DEFAULT_MAX_TABLES,
) : UiState {
    val selectedTable: DiagramTable?
        get() =
            (selectedElement as? DiagramSelection.Table)?.let { selection ->
                tables.firstOrNull { it.id == selection.tableId }
            }

    val selectedRelationship: DiagramRelationship?
        get() =
            (selectedElement as? DiagramSelection.Relationship)?.let { selection ->
                relationships.firstOrNull { it.id == selection.relationshipId }
            }

    companion object {
        const val DEFAULT_MAX_TABLES = 40
    }
}

data class DiagramTable(
    val id: String,
    val name: String,
    val schema: String?,
    val x: Float,
    val y: Float,
    val columns: List<DiagramColumn>,
    val isDraft: Boolean = false,
) {
    val displayName: String
        get() = schema?.let { "$it.$name" } ?: name
}

data class DiagramColumn(
    val id: String,
    val name: String,
    val type: String,
    val nullable: Boolean,
    val isPrimaryKey: Boolean = false,
    val isForeignKey: Boolean = false,
    val isDraft: Boolean = false,
)

data class DiagramRelationship(
    val id: String,
    val name: String?,
    val sourceTableId: String,
    val sourceColumns: List<String>,
    val targetTableId: String,
    val targetColumns: List<String>,
    val deleteRule: ForeignKeyRule = ForeignKeyRule.NO_ACTION,
    val isDraft: Boolean = false,
)

sealed interface DiagramSelection {
    data object None : DiagramSelection

    data class Table(
        val tableId: String,
    ) : DiagramSelection

    data class Relationship(
        val relationshipId: String,
    ) : DiagramSelection
}
