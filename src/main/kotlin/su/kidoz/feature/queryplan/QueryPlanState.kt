package su.kidoz.feature.queryplan

import su.kidoz.mvi.UiState

data class QueryPlanState(
    val planNodes: List<QueryPlanNode> = emptyList(),
    val rawPlan: String = "",
    val query: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedNodeId: String? = null,
    val viewMode: PlanViewMode = PlanViewMode.TREE,
) : UiState

data class QueryPlanNode(
    val id: String,
    val nodeType: String,
    val operation: String,
    val relationName: String? = null,
    val alias: String? = null,
    val startupCost: Double? = null,
    val totalCost: Double? = null,
    val planRows: Long? = null,
    val planWidth: Int? = null,
    val actualStartupTime: Double? = null,
    val actualTotalTime: Double? = null,
    val actualRows: Long? = null,
    val actualLoops: Long? = null,
    val filter: String? = null,
    val indexName: String? = null,
    val indexCond: String? = null,
    val sortKey: String? = null,
    val joinType: String? = null,
    val hashCond: String? = null,
    val children: List<QueryPlanNode> = emptyList(),
    val depth: Int = 0,
    val properties: Map<String, String> = emptyMap(),
) {
    val costPercentage: Double
        get() = totalCost ?: 0.0

    val isExpensive: Boolean
        get() = (totalCost ?: 0.0) > 1000

    val displayName: String
        get() =
            buildString {
                append(nodeType)
                relationName?.let { append(" on $it") }
                alias?.let { if (it != relationName) append(" ($it)") }
            }
}

enum class PlanViewMode {
    TREE,
    TABLE,
    RAW,
}
