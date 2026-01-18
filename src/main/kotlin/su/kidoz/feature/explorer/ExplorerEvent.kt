package su.kidoz.feature.explorer

import su.kidoz.mvi.UiEvent

sealed interface ExplorerEvent : UiEvent {
    data class ConnectionChanged(
        val connectionId: String?,
    ) : ExplorerEvent

    data object Refresh : ExplorerEvent

    data class ToggleNode(
        val nodeId: String,
    ) : ExplorerEvent

    data class SelectNode(
        val node: TreeNode,
    ) : ExplorerEvent

    data class LoadTableDetails(
        val tableName: String,
        val schema: String?,
    ) : ExplorerEvent

    // Context menu actions
    data class CopyName(
        val name: String,
    ) : ExplorerEvent

    data class GenerateSelect(
        val tableName: String,
        val schema: String?,
    ) : ExplorerEvent

    data class GenerateInsert(
        val tableName: String,
        val schema: String?,
    ) : ExplorerEvent

    data class GenerateDdl(
        val tableName: String,
        val schema: String?,
    ) : ExplorerEvent

    // Schema navigation
    data class LoadSchemaContents(
        val schemaName: String,
    ) : ExplorerEvent

    data class ExpandSchema(
        val schemaName: String,
    ) : ExplorerEvent

    data class CollapseSchema(
        val schemaName: String,
    ) : ExplorerEvent

    // Elasticsearch index management
    data object ShowCreateIndexDialog : ExplorerEvent

    data class ShowEditIndexSettingsDialog(
        val indexName: String,
    ) : ExplorerEvent

    data class ShowEditIndexMappingsDialog(
        val indexName: String,
    ) : ExplorerEvent

    data object HideIndexDialog : ExplorerEvent

    data class UpdateIndexName(
        val name: String,
    ) : ExplorerEvent

    data class UpdateIndexDefinition(
        val json: String,
    ) : ExplorerEvent

    data object SaveIndex : ExplorerEvent

    data class ConfirmDeleteIndex(
        val indexName: String,
    ) : ExplorerEvent

    data class DeleteIndex(
        val indexName: String,
    ) : ExplorerEvent

    data object CancelDelete : ExplorerEvent
}
