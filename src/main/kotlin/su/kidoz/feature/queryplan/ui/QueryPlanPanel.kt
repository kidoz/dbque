package su.kidoz.feature.queryplan.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import su.kidoz.feature.queryplan.*
import su.kidoz.ui.theme.DBQueTheme

@Composable
fun QueryPlanPanel(
    state: QueryPlanState,
    onEvent: (QueryPlanEvent) -> Unit,
    maxCost: Double,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Toolbar
        QueryPlanToolbar(
            viewMode = state.viewMode,
            onViewModeChange = { onEvent(QueryPlanEvent.SetViewMode(it)) },
            onClear = { onEvent(QueryPlanEvent.Clear) },
        )

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (state.error != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else if (state.planNodes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.AccountTree,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        "No query plan",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Click 'Explain' to analyze a query",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        } else {
            when (state.viewMode) {
                PlanViewMode.TREE -> {
                    PlanTreeView(
                        nodes = state.planNodes,
                        selectedNodeId = state.selectedNodeId,
                        maxCost = maxCost,
                        onNodeClick = { onEvent(QueryPlanEvent.SelectNode(it)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                PlanViewMode.TABLE -> {
                    PlanTableView(
                        nodes = state.planNodes,
                        modifier = Modifier.weight(1f),
                    )
                }
                PlanViewMode.RAW -> {
                    PlanRawView(
                        rawPlan = state.rawPlan,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun QueryPlanToolbar(
    viewMode: PlanViewMode,
    onViewModeChange: (PlanViewMode) -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Query Plan",
                style = MaterialTheme.typography.titleSmall,
            )

            Spacer(Modifier.weight(1f))

            // View mode toggle
            SingleChoiceSegmentedButtonRow {
                PlanViewMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = viewMode == mode,
                        onClick = { onViewModeChange(mode) },
                        shape =
                            SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = PlanViewMode.entries.size,
                            ),
                    ) {
                        Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }

            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Clear, "Clear", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PlanTreeView(
    nodes: List<QueryPlanNode>,
    selectedNodeId: String?,
    maxCost: Double,
    onNodeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Box(modifier = modifier.verticalScroll(scrollState).horizontalScroll(rememberScrollState())) {
        Column(modifier = Modifier.padding(16.dp)) {
            nodes.forEach { node ->
                PlanNodeItem(
                    node = node,
                    selectedNodeId = selectedNodeId,
                    maxCost = maxCost,
                    onNodeClick = onNodeClick,
                )
            }
        }
    }
}

@Composable
private fun PlanNodeItem(
    node: QueryPlanNode,
    selectedNodeId: String?,
    maxCost: Double,
    onNodeClick: (String) -> Unit,
) {
    val isSelected = node.id == selectedNodeId
    val costRatio = if (maxCost > 0) (node.totalCost ?: 0.0) / maxCost else 0.0

    Column(
        modifier = Modifier.padding(start = (node.depth * 24).dp),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onNodeClick(node.id) },
            color =
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
            shape = MaterialTheme.shapes.small,
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Node type icon
                    Icon(
                        imageVector = getNodeIcon(node.nodeType),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = getNodeColor(node.nodeType, node.isExpensive),
                    )

                    // Node name
                    Text(
                        text = node.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    Spacer(Modifier.weight(1f))

                    // Cost
                    node.totalCost?.let { cost ->
                        Text(
                            text = "Cost: %.2f".format(cost),
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                if (node.isExpensive) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }

                    // Rows
                    node.planRows?.let { rows ->
                        Text(
                            text = "Rows: $rows",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Cost bar with gradient heat map
                if (costRatio > 0) {
                    LinearProgressIndicator(
                        progress = { costRatio.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 4.dp),
                        color = getCostGradientColor(costRatio.toFloat()),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

                // Details
                if (isSelected) {
                    Spacer(Modifier.height(8.dp))
                    PlanNodeDetails(node)
                }
            }
        }

        // Children
        node.children.forEach { child ->
            Row {
                Box(
                    modifier =
                        Modifier
                            .width(24.dp)
                            .height(32.dp),
                ) {
                    // Connector line
                    Box(
                        modifier =
                            Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .align(Alignment.TopCenter)
                                .background(MaterialTheme.colorScheme.outline),
                    )
                }
                PlanNodeItem(child, selectedNodeId, maxCost, onNodeClick)
            }
        }
    }
}

@Composable
private fun PlanNodeDetails(node: QueryPlanNode) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        node.filter?.let {
            DetailRow("Filter", it)
        }
        node.indexName?.let {
            DetailRow("Index", it)
        }
        node.indexCond?.let {
            DetailRow("Index Condition", it)
        }
        node.sortKey?.let {
            DetailRow("Sort Key", it)
        }
        node.joinType?.let {
            DetailRow("Join Type", it)
        }
        node.hashCond?.let {
            DetailRow("Hash Condition", it)
        }
        node.actualRows?.let {
            DetailRow("Actual Rows", it.toString())
        }
        node.actualTotalTime?.let {
            DetailRow("Actual Time", "%.3f ms".format(it))
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlanTableView(
    nodes: List<QueryPlanNode>,
    modifier: Modifier = Modifier,
) {
    val flatNodes = remember(nodes) { flattenNodes(nodes) }

    LazyColumn(modifier = modifier) {
        item {
            // Header
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp),
            ) {
                Text("Operation", Modifier.weight(2f), style = MaterialTheme.typography.labelMedium)
                Text("Cost", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text("Rows", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text("Width", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            }
        }
        items(flatNodes) { node ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
            ) {
                Text(
                    "${"  ".repeat(node.depth)}${node.displayName}",
                    Modifier.weight(2f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    node.totalCost?.let { "%.2f".format(it) } ?: "-",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    node.planRows?.toString() ?: "-",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    node.planWidth?.toString() ?: "-",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun PlanRawView(
    rawPlan: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        Text(
            text = rawPlan,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun flattenNodes(nodes: List<QueryPlanNode>): List<QueryPlanNode> {
    val result = mutableListOf<QueryPlanNode>()

    fun addNode(node: QueryPlanNode) {
        result.add(node)
        node.children.forEach { addNode(it) }
    }
    nodes.forEach { addNode(it) }
    return result
}

private fun getNodeIcon(nodeType: String) =
    when {
        nodeType.contains("Seq Scan", ignoreCase = true) -> Icons.Default.TableRows
        nodeType.contains("Index", ignoreCase = true) -> Icons.Default.Search
        nodeType.contains("Hash", ignoreCase = true) -> Icons.Default.Tag
        nodeType.contains("Sort", ignoreCase = true) -> Icons.Default.SwapVert
        nodeType.contains("Aggregate", ignoreCase = true) -> Icons.Default.Functions
        nodeType.contains("Join", ignoreCase = true) -> Icons.AutoMirrored.Filled.MergeType
        nodeType.contains("Nested Loop", ignoreCase = true) -> Icons.Default.Loop
        else -> Icons.Default.Circle
    }

@Composable
private fun getNodeColor(
    nodeType: String,
    isExpensive: Boolean,
): Color {
    if (isExpensive) return MaterialTheme.colorScheme.error

    return when {
        nodeType.contains("Seq Scan", ignoreCase = true) -> DBQueTheme.extendedColors.warning
        nodeType.contains("Index", ignoreCase = true) -> DBQueTheme.extendedColors.success
        else -> MaterialTheme.colorScheme.primary
    }
}

/**
 * Get a gradient color based on cost ratio for heat map visualization.
 * - Green (0-25%): Low cost, optimal performance
 * - Yellow (25-50%): Moderate cost, acceptable performance
 * - Orange (50-75%): High cost, may need optimization
 * - Red (75-100%): Critical cost, requires attention
 */
private fun getCostGradientColor(costRatio: Float): Color =
    when {
        costRatio < 0.25f -> Color(0xFF4CAF50) // Green
        costRatio < 0.50f -> Color(0xFFFFEB3B) // Yellow
        costRatio < 0.75f -> Color(0xFFFF9800) // Orange
        else -> Color(0xFFF44336) // Red
    }
