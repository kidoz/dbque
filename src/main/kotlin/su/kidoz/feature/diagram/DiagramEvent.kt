package su.kidoz.feature.diagram

import su.kidoz.mvi.UiEvent

sealed interface DiagramEvent : UiEvent {
    data object LoadDiagram : DiagramEvent

    data class SelectSchema(
        val schema: String?,
    ) : DiagramEvent

    data class SelectTable(
        val tableId: String,
    ) : DiagramEvent

    data class SelectRelationship(
        val relationshipId: String,
    ) : DiagramEvent

    data object ClearSelection : DiagramEvent

    data class MoveTable(
        val tableId: String,
        val x: Float,
        val y: Float,
    ) : DiagramEvent

    data object ZoomIn : DiagramEvent

    data object ZoomOut : DiagramEvent

    data object ResetZoom : DiagramEvent

    data object ToggleKeysOnly : DiagramEvent

    data object ToggleDdlPreview : DiagramEvent

    data object InsertDdlIntoEditor : DiagramEvent

    data object CopyDdlToClipboard : DiagramEvent

    data object AddTable : DiagramEvent

    data class AddRelationship(
        val sourceTableId: String,
        val sourceColumn: String,
        val targetTableId: String,
        val targetColumn: String,
    ) : DiagramEvent

    data class RenameSelectedTable(
        val name: String,
    ) : DiagramEvent

    data object AddColumnToSelectedTable : DiagramEvent

    data class RenameColumn(
        val columnId: String,
        val name: String,
    ) : DiagramEvent

    data class ChangeColumnType(
        val columnId: String,
        val type: String,
    ) : DiagramEvent

    data class ToggleColumnNullable(
        val columnId: String,
    ) : DiagramEvent

    data class ToggleColumnPrimaryKey(
        val columnId: String,
    ) : DiagramEvent

    data object DeleteSelected : DiagramEvent
}
