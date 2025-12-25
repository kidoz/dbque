package su.kidoz.feature.queryplan

import kotlinx.serialization.json.*
import mu.KotlinLogging
import java.util.UUID

class QueryPlanParser {
    private val logger = KotlinLogging.logger {}

    fun parsePostgresPlan(jsonPlan: String): List<QueryPlanNode> =
        try {
            val json = Json.parseToJsonElement(jsonPlan)
            val plans = json.jsonArray
            plans.mapNotNull { planElement ->
                val plan = planElement.jsonObject["Plan"]?.jsonObject
                plan?.let { parseNode(it, 0) }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse PostgreSQL plan" }
            emptyList()
        }

    fun parseMySqlPlan(explainOutput: List<List<Any?>>): List<QueryPlanNode> {
        // MySQL EXPLAIN returns tabular data
        return explainOutput.mapIndexed { index, row ->
            QueryPlanNode(
                id = UUID.randomUUID().toString(),
                nodeType = row.getOrNull(3)?.toString() ?: "UNKNOWN",
                operation = row.getOrNull(1)?.toString() ?: "",
                relationName = row.getOrNull(2)?.toString(),
                indexName = row.getOrNull(5)?.toString(),
                planRows = row.getOrNull(8)?.toString()?.toLongOrNull(),
                filter = row.getOrNull(9)?.toString(),
                depth = 0,
                properties =
                    mapOf(
                        "id" to (row.getOrNull(0)?.toString() ?: ""),
                        "select_type" to (row.getOrNull(1)?.toString() ?: ""),
                        "type" to (row.getOrNull(3)?.toString() ?: ""),
                        "possible_keys" to (row.getOrNull(4)?.toString() ?: ""),
                        "key" to (row.getOrNull(5)?.toString() ?: ""),
                        "key_len" to (row.getOrNull(6)?.toString() ?: ""),
                        "ref" to (row.getOrNull(7)?.toString() ?: ""),
                        "rows" to (row.getOrNull(8)?.toString() ?: ""),
                        "Extra" to (row.getOrNull(9)?.toString() ?: ""),
                    ),
            )
        }
    }

    fun parseGenericPlan(textPlan: String): List<QueryPlanNode> {
        // Parse text-based EXPLAIN output (generic)
        val lines = textPlan.lines().filter { it.isNotBlank() }
        val nodes = mutableListOf<QueryPlanNode>()

        lines.forEach { line ->
            val indent = line.takeWhile { it == ' ' || it == '-' || it == '>' }.length
            val depth = indent / 2
            val content = line.trim().removePrefix("->").trim()

            if (content.isNotEmpty()) {
                nodes.add(
                    QueryPlanNode(
                        id = UUID.randomUUID().toString(),
                        nodeType = extractNodeType(content),
                        operation = content,
                        depth = depth,
                    ),
                )
            }
        }

        return buildTree(nodes)
    }

    private fun parseNode(
        json: JsonObject,
        depth: Int,
    ): QueryPlanNode {
        val nodeType = json["Node Type"]?.jsonPrimitive?.content ?: "Unknown"

        val children =
            json["Plans"]?.jsonArray?.map {
                parseNode(it.jsonObject, depth + 1)
            } ?: emptyList()

        return QueryPlanNode(
            id = UUID.randomUUID().toString(),
            nodeType = nodeType,
            operation = nodeType,
            relationName = json["Relation Name"]?.jsonPrimitive?.contentOrNull,
            alias = json["Alias"]?.jsonPrimitive?.contentOrNull,
            startupCost = json["Startup Cost"]?.jsonPrimitive?.doubleOrNull,
            totalCost = json["Total Cost"]?.jsonPrimitive?.doubleOrNull,
            planRows = json["Plan Rows"]?.jsonPrimitive?.longOrNull,
            planWidth = json["Plan Width"]?.jsonPrimitive?.intOrNull,
            actualStartupTime = json["Actual Startup Time"]?.jsonPrimitive?.doubleOrNull,
            actualTotalTime = json["Actual Total Time"]?.jsonPrimitive?.doubleOrNull,
            actualRows = json["Actual Rows"]?.jsonPrimitive?.longOrNull,
            actualLoops = json["Actual Loops"]?.jsonPrimitive?.longOrNull,
            filter = json["Filter"]?.jsonPrimitive?.contentOrNull,
            indexName = json["Index Name"]?.jsonPrimitive?.contentOrNull,
            indexCond = json["Index Cond"]?.jsonPrimitive?.contentOrNull,
            sortKey = json["Sort Key"]?.jsonArray?.joinToString(", ") { it.jsonPrimitive.content },
            joinType = json["Join Type"]?.jsonPrimitive?.contentOrNull,
            hashCond = json["Hash Cond"]?.jsonPrimitive?.contentOrNull,
            children = children,
            depth = depth,
            properties =
                json.entries
                    .filter { it.key !in listOf("Plans", "Node Type") }
                    .associate { it.key to it.value.toString() },
        )
    }

    private fun extractNodeType(content: String): String {
        // Extract node type from content like "Seq Scan on users  (cost=...)"
        val parenIndex = content.indexOf('(')
        return if (parenIndex > 0) {
            content.substring(0, parenIndex).trim()
        } else {
            content.split(" ").take(2).joinToString(" ")
        }
    }

    private fun buildTree(flatNodes: List<QueryPlanNode>): List<QueryPlanNode> {
        if (flatNodes.isEmpty()) return emptyList()

        // Simple tree building based on depth
        val stack = mutableListOf<MutableList<QueryPlanNode>>()
        stack.add(mutableListOf())

        flatNodes.forEach { node ->
            while (stack.size <= node.depth) {
                stack.add(mutableListOf())
            }
            while (stack.size > node.depth + 1) {
                val children = stack.removeLast()
                if (stack.last().isNotEmpty()) {
                    val parent = stack.last().last()
                    stack.last()[stack.last().lastIndex] = parent.copy(children = children)
                }
            }
            stack[node.depth].add(node)
        }

        while (stack.size > 1) {
            val children = stack.removeLast()
            if (stack.last().isNotEmpty()) {
                val parent = stack.last().last()
                stack.last()[stack.last().lastIndex] = parent.copy(children = children)
            }
        }

        return stack.first()
    }
}
