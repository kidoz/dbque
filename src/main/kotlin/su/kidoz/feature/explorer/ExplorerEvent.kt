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
}
