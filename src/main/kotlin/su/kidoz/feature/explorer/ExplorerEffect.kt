package su.kidoz.feature.explorer

import su.kidoz.mvi.UiEffect

sealed interface ExplorerEffect : UiEffect {
    data class CopiedToClipboard(
        val text: String,
    ) : ExplorerEffect

    data class InsertIntoEditor(
        val sql: String,
    ) : ExplorerEffect

    data class ShowError(
        val message: String,
    ) : ExplorerEffect

    // Elasticsearch index management effects
    data class IndexCreated(
        val indexName: String,
    ) : ExplorerEffect

    data class IndexDeleted(
        val indexName: String,
    ) : ExplorerEffect

    data class IndexUpdated(
        val indexName: String,
    ) : ExplorerEffect
}
