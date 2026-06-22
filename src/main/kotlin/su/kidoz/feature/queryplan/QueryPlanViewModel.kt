package su.kidoz.feature.queryplan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import su.kidoz.core.model.DatabaseType
import su.kidoz.database.ConnectionManager
import su.kidoz.mvi.MviViewModel

class QueryPlanViewModel(
    private val connectionManager: ConnectionManager,
) : MviViewModel<QueryPlanState, QueryPlanEvent, QueryPlanEffect>(QueryPlanState()) {
    private val parser = QueryPlanParser()

    override fun onEvent(event: QueryPlanEvent) {
        when (event) {
            is QueryPlanEvent.AnalyzeQuery -> analyzeQuery(event.query, event.analyze)
            is QueryPlanEvent.SelectNode -> selectNode(event.nodeId)
            is QueryPlanEvent.SetViewMode -> setViewMode(event.mode)
            is QueryPlanEvent.Clear -> clear()
        }
    }

    private fun analyzeQuery(
        query: String,
        analyze: Boolean,
    ) {
        val activeConnection = connectionManager.activeConnection
        if (activeConnection == null) {
            sendEffect(QueryPlanEffect.ShowError("No active connection"))
            return
        }

        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null, query = query) }

            try {
                val dbType = activeConnection.config.type
                val explainQuery = buildExplainQuery(query, dbType, analyze)

                withContext(Dispatchers.IO) {
                    activeConnection.getConnection().use { conn ->
                        conn.createStatement().use { stmt ->
                            stmt.executeQuery(explainQuery).use { rs ->
                                val rawPlan = StringBuilder()
                                val rows = mutableListOf<List<Any?>>()

                                val columnCount = rs.metaData.columnCount
                                while (rs.next()) {
                                    val row = (1..columnCount).map { rs.getObject(it) }
                                    rows.add(row)
                                    rawPlan.appendLine(row.joinToString(" | ") { it?.toString() ?: "" })
                                }

                                val nodes =
                                    when (dbType) {
                                        DatabaseType.POSTGRESQL -> {
                                            // PostgreSQL with JSON format
                                            val jsonPlan = rows.firstOrNull()?.firstOrNull()?.toString() ?: "[]"
                                            parser.parsePostgresPlan(jsonPlan)
                                        }

                                        DatabaseType.MYSQL,
                                        DatabaseType.STARROCKS,
                                        -> {
                                            parser.parseMySqlPlan(rows)
                                        }

                                        else -> {
                                            parser.parseGenericPlan(rawPlan.toString())
                                        }
                                    }

                                updateState {
                                    copy(
                                        planNodes = nodes,
                                        rawPlan = rawPlan.toString(),
                                        isLoading = false,
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to get query plan" }
                updateState { copy(isLoading = false, error = e.message) }
                sendEffect(QueryPlanEffect.ShowError(e.message ?: "Failed to get query plan"))
            }
        }
    }

    private fun buildExplainQuery(
        query: String,
        dbType: DatabaseType,
        analyze: Boolean,
    ): String =
        when (dbType) {
            DatabaseType.POSTGRESQL -> {
                if (analyze) {
                    "EXPLAIN (ANALYZE, COSTS, VERBOSE, BUFFERS, FORMAT JSON) $query"
                } else {
                    "EXPLAIN (COSTS, VERBOSE, FORMAT JSON) $query"
                }
            }

            DatabaseType.MYSQL,
            DatabaseType.STARROCKS,
            -> {
                if (analyze) {
                    "EXPLAIN ANALYZE $query"
                } else {
                    "EXPLAIN $query"
                }
            }

            DatabaseType.H2 -> {
                "EXPLAIN $query"
            }

            DatabaseType.SQLITE -> {
                "EXPLAIN QUERY PLAN $query"
            }

            DatabaseType.MONGODB -> {
                throw UnsupportedOperationException(
                    "MongoDB query plans require using db.collection.explain() - not supported in SQL editor",
                )
            }

            DatabaseType.ELASTICSEARCH -> {
                throw UnsupportedOperationException(
                    "Elasticsearch query plans require using _validate/query API - not supported in SQL editor",
                )
            }
        }

    private fun selectNode(nodeId: String?) {
        updateState { copy(selectedNodeId = nodeId) }
    }

    private fun setViewMode(mode: PlanViewMode) {
        updateState { copy(viewMode = mode) }
    }

    private fun clear() {
        updateState { QueryPlanState() }
    }

    fun calculateMaxCost(): Double {
        fun maxCostRecursive(nodes: List<QueryPlanNode>): Double =
            nodes.maxOfOrNull { node ->
                maxOf(node.totalCost ?: 0.0, maxCostRecursive(node.children))
            } ?: 0.0
        return maxCostRecursive(currentState.planNodes)
    }
}
