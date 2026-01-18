package su.kidoz.feature.explorer.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import su.kidoz.core.model.DatabaseCategory
import su.kidoz.core.model.DatabaseTerminology
import su.kidoz.core.model.SchemaInfo
import su.kidoz.feature.explorer.*
import su.kidoz.ui.theme.DBQueTheme

@Composable
fun DatabaseTree(
    state: ExplorerState,
    onEvent: (ExplorerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Header
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Database Explorer",
                    style = MaterialTheme.typography.titleSmall,
                )
                IconButton(
                    onClick = { onEvent(ExplorerEvent.Refresh) },
                    modifier = Modifier.size(24.dp),
                    enabled = !state.isLoading,
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // Error message
        state.error?.let { error ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }

        // Tree content
        if (state.connectionId == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No connection selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val terminology = state.terminology
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Render schema-based hierarchy if schemas exist
                if (state.usesSchemas && state.schemas.isNotEmpty()) {
                    // Schema-based tree for PostgreSQL, MySQL, etc.
                    state.schemas.forEach { schema ->
                        val schemaNodeId = "schema::${schema.name}"
                        val isSchemaExpanded = state.expandedNodes.contains(schemaNodeId)
                        val isSchemaLoading = state.isSchemaLoading(schema.name)

                        item(key = schemaNodeId) {
                            SchemaNodeItem(
                                schema = schema,
                                isExpanded = isSchemaExpanded,
                                isLoading = isSchemaLoading,
                                isDefault = schema.name == state.defaultSchema,
                                onToggle = {
                                    if (isSchemaExpanded) {
                                        onEvent(ExplorerEvent.CollapseSchema(schema.name))
                                    } else {
                                        onEvent(ExplorerEvent.ExpandSchema(schema.name))
                                    }
                                },
                            )
                        }

                        if (isSchemaExpanded) {
                            val schemaTables = state.getTablesForSchema(schema.name)
                            val schemaViews = state.getViewsForSchema(schema.name)

                            // Tables folder under schema
                            item(key = "tables:${schema.name}") {
                                TreeNodeItem(
                                    node = TreeNode.TablesFolder(schema.name, null),
                                    isExpanded = state.expandedNodes.contains("tables::${schema.name}"),
                                    isSelected = false,
                                    level = 1,
                                    onToggle = { onEvent(ExplorerEvent.ToggleNode("tables::${schema.name}")) },
                                    onSelect = {},
                                    onEvent = onEvent,
                                    terminology = terminology,
                                    itemCount = schemaTables.size,
                                )
                            }

                            if (state.expandedNodes.contains("tables::${schema.name}")) {
                                items(schemaTables, key = { "table:${it.schema}:${it.name}" }) { table ->
                                    val selectedTable = (state.selectedNode as? TreeNode.TableNode)?.table
                                    TreeNodeItem(
                                        node = TreeNode.TableNode(table),
                                        isExpanded = state.expandedNodes.contains("table:${table.schema}:${table.name}"),
                                        isSelected =
                                            selectedTable != null &&
                                                selectedTable.name == table.name &&
                                                selectedTable.schema == table.schema,
                                        level = 2,
                                        onToggle = { onEvent(ExplorerEvent.ToggleNode("table:${table.schema}:${table.name}")) },
                                        onSelect = { onEvent(ExplorerEvent.SelectNode(TreeNode.TableNode(table))) },
                                        onEvent = onEvent,
                                        terminology = terminology,
                                    )

                                    // Show Columns and Indexes folders when table is expanded
                                    if (state.expandedNodes.contains("table:${table.schema}:${table.name}")) {
                                        val details =
                                            state.tableDetails?.takeIf {
                                                it.table.name == table.name && it.table.schema == table.schema
                                            }

                                        // Columns folder
                                        val columnsFolderId = "columns:${table.schema}:${table.name}"
                                        TreeNodeItem(
                                            node = TreeNode.ColumnsFolder(table.name, table.schema),
                                            isExpanded = state.expandedNodes.contains(columnsFolderId),
                                            isSelected = false,
                                            level = 3,
                                            onToggle = { onEvent(ExplorerEvent.ToggleNode(columnsFolderId)) },
                                            onSelect = {},
                                            onEvent = onEvent,
                                            terminology = terminology,
                                            itemCount = details?.columns?.size,
                                        )

                                        // Show columns when Columns folder is expanded
                                        if (state.expandedNodes.contains(columnsFolderId)) {
                                            details?.columns?.forEach { column ->
                                                TreeNodeItem(
                                                    node = TreeNode.ColumnNode(column, table.name),
                                                    isExpanded = false,
                                                    isSelected = false,
                                                    level = 4,
                                                    onToggle = {},
                                                    onSelect = {},
                                                    onEvent = onEvent,
                                                    terminology = terminology,
                                                )
                                            }
                                        }

                                        // Indexes folder (only if there are indexes)
                                        if (details?.indexes?.isNotEmpty() == true) {
                                            val indexesFolderId = "indexes:${table.schema}:${table.name}"
                                            TreeNodeItem(
                                                node = TreeNode.IndexesFolder(table.name, table.schema),
                                                isExpanded = state.expandedNodes.contains(indexesFolderId),
                                                isSelected = false,
                                                level = 3,
                                                onToggle = { onEvent(ExplorerEvent.ToggleNode(indexesFolderId)) },
                                                onSelect = {},
                                                onEvent = onEvent,
                                                terminology = terminology,
                                                itemCount = details.indexes.size,
                                            )

                                            // Show indexes when Indexes folder is expanded
                                            if (state.expandedNodes.contains(indexesFolderId)) {
                                                details.indexes.forEach { index ->
                                                    TreeNodeItem(
                                                        node = TreeNode.IndexNode(index, table.name),
                                                        isExpanded = false,
                                                        isSelected = false,
                                                        level = 4,
                                                        onToggle = {},
                                                        onSelect = {},
                                                        onEvent = onEvent,
                                                        terminology = terminology,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Views folder under schema (if views exist)
                            if (schemaViews.isNotEmpty() && terminology?.supportsViews != false) {
                                item(key = "views:${schema.name}") {
                                    TreeNodeItem(
                                        node = TreeNode.ViewsFolder(schema.name, null),
                                        isExpanded = state.expandedNodes.contains("views::${schema.name}"),
                                        isSelected = false,
                                        level = 1,
                                        onToggle = { onEvent(ExplorerEvent.ToggleNode("views::${schema.name}")) },
                                        onSelect = {},
                                        onEvent = onEvent,
                                        terminology = terminology,
                                        itemCount = schemaViews.size,
                                    )
                                }

                                if (state.expandedNodes.contains("views::${schema.name}")) {
                                    items(schemaViews, key = { "view:${it.schema}:${it.name}" }) { view ->
                                        val selectedView = (state.selectedNode as? TreeNode.ViewNode)?.view
                                        TreeNodeItem(
                                            node = TreeNode.ViewNode(view),
                                            isExpanded = false,
                                            isSelected =
                                                selectedView != null &&
                                                    selectedView.name == view.name &&
                                                    selectedView.schema == view.schema,
                                            level = 2,
                                            onToggle = {},
                                            onSelect = { onEvent(ExplorerEvent.SelectNode(TreeNode.ViewNode(view))) },
                                            onEvent = onEvent,
                                            terminology = terminology,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Flat tree for SQLite, MongoDB, Elasticsearch
                    // Tables/Collections/Indices folder
                    item {
                        TreeNodeItem(
                            node = TreeNode.TablesFolder(null, null),
                            isExpanded = state.expandedNodes.contains("tables::"),
                            isSelected = false,
                            level = 0,
                            onToggle = { onEvent(ExplorerEvent.ToggleNode("tables::")) },
                            onSelect = {},
                            onEvent = onEvent,
                            terminology = terminology,
                            itemCount = state.tables.size,
                        )
                    }

                    if (state.expandedNodes.contains("tables::")) {
                        items(state.tables, key = { "table:${it.schema}:${it.name}" }) { table ->
                            TreeNodeItem(
                                node = TreeNode.TableNode(table),
                                isExpanded = state.expandedNodes.contains("table:${table.schema}:${table.name}"),
                                isSelected = (state.selectedNode as? TreeNode.TableNode)?.table?.name == table.name,
                                level = 1,
                                onToggle = { onEvent(ExplorerEvent.ToggleNode("table:${table.schema}:${table.name}")) },
                                onSelect = { onEvent(ExplorerEvent.SelectNode(TreeNode.TableNode(table))) },
                                onEvent = onEvent,
                                terminology = terminology,
                            )

                            // Show Columns and Indexes folders when table is expanded
                            if (state.expandedNodes.contains("table:${table.schema}:${table.name}")) {
                                val details = state.tableDetails?.takeIf { it.table.name == table.name }

                                // Columns folder
                                val columnsFolderId = "columns:${table.schema ?: ""}:${table.name}"
                                TreeNodeItem(
                                    node = TreeNode.ColumnsFolder(table.name, table.schema),
                                    isExpanded = state.expandedNodes.contains(columnsFolderId),
                                    isSelected = false,
                                    level = 2,
                                    onToggle = { onEvent(ExplorerEvent.ToggleNode(columnsFolderId)) },
                                    onSelect = {},
                                    onEvent = onEvent,
                                    terminology = terminology,
                                    itemCount = details?.columns?.size,
                                )

                                // Show columns when Columns folder is expanded
                                if (state.expandedNodes.contains(columnsFolderId)) {
                                    details?.columns?.forEach { column ->
                                        TreeNodeItem(
                                            node = TreeNode.ColumnNode(column, table.name),
                                            isExpanded = false,
                                            isSelected = false,
                                            level = 3,
                                            onToggle = {},
                                            onSelect = {},
                                            onEvent = onEvent,
                                            terminology = terminology,
                                        )
                                    }
                                }

                                // Indexes folder (only if there are indexes)
                                if (details?.indexes?.isNotEmpty() == true) {
                                    val indexesFolderId = "indexes:${table.schema ?: ""}:${table.name}"
                                    TreeNodeItem(
                                        node = TreeNode.IndexesFolder(table.name, table.schema),
                                        isExpanded = state.expandedNodes.contains(indexesFolderId),
                                        isSelected = false,
                                        level = 2,
                                        onToggle = { onEvent(ExplorerEvent.ToggleNode(indexesFolderId)) },
                                        onSelect = {},
                                        onEvent = onEvent,
                                        terminology = terminology,
                                        itemCount = details.indexes.size,
                                    )

                                    // Show indexes when Indexes folder is expanded
                                    if (state.expandedNodes.contains(indexesFolderId)) {
                                        details.indexes.forEach { index ->
                                            TreeNodeItem(
                                                node = TreeNode.IndexNode(index, table.name),
                                                isExpanded = false,
                                                isSelected = false,
                                                level = 3,
                                                onToggle = {},
                                                onSelect = {},
                                                onEvent = onEvent,
                                                terminology = terminology,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Views folder (only for databases that support views)
                    if (state.views.isNotEmpty() && terminology?.supportsViews != false) {
                        item {
                            TreeNodeItem(
                                node = TreeNode.ViewsFolder(null, null),
                                isExpanded = state.expandedNodes.contains("views::"),
                                isSelected = false,
                                level = 0,
                                onToggle = { onEvent(ExplorerEvent.ToggleNode("views::")) },
                                onSelect = {},
                                onEvent = onEvent,
                                terminology = terminology,
                                itemCount = state.views.size,
                            )
                        }

                        if (state.expandedNodes.contains("views::")) {
                            items(state.views, key = { "view:${it.schema}:${it.name}" }) { view ->
                                TreeNodeItem(
                                    node = TreeNode.ViewNode(view),
                                    isExpanded = false,
                                    isSelected = (state.selectedNode as? TreeNode.ViewNode)?.view?.name == view.name,
                                    level = 1,
                                    onToggle = {},
                                    onSelect = { onEvent(ExplorerEvent.SelectNode(TreeNode.ViewNode(view))) },
                                    onEvent = onEvent,
                                    terminology = terminology,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SchemaNodeItem(
    schema: SchemaInfo,
    isExpanded: Boolean,
    isLoading: Boolean,
    isDefault: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Expand/collapse icon
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 1.5.dp,
            )
        } else {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Schema icon
        Icon(
            imageVector = Icons.Default.Schema,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        // Schema name
        Text(
            text = schema.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isDefault) androidx.compose.ui.text.font.FontWeight.Bold else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Default indicator
        if (isDefault) {
            Text(
                text = "default",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun TreeNodeItem(
    node: TreeNode,
    isExpanded: Boolean,
    isSelected: Boolean,
    level: Int,
    onToggle: () -> Unit,
    onSelect: () -> Unit,
    onEvent: (ExplorerEvent) -> Unit,
    terminology: DatabaseTerminology? = null,
    itemCount: Int? = null,
) {
    val contextMenuItems =
        remember(node, terminology) {
            buildContextMenuItems(node, onEvent, terminology)
        }

    ContextMenuArea(items = { contextMenuItems }) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { if (canExpand(node)) onToggle() else onSelect() }
                    .then(
                        if (isSelected) {
                            Modifier.background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            )
                        } else {
                            Modifier
                        },
                    ).padding(start = (12 + level * 16).dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Expand/collapse icon
            if (canExpand(node)) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.width(16.dp))
            }

            // Node icon
            Icon(
                imageVector = getNodeIcon(node, terminology),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = getNodeColor(node),
            )

            // Node name
            Text(
                text = getNodeDisplayText(node, terminology),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // Item count for folders
            if (itemCount != null && node.type == TreeNodeType.FOLDER) {
                Text(
                    text = "($itemCount)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Additional info
            getNodeSuffix(node)?.let { suffix ->
                Text(
                    text = suffix,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun canExpand(node: TreeNode): Boolean =
    when (node) {
        is TreeNode.DatabaseNode, is TreeNode.SchemaNode,
        is TreeNode.TablesFolder, is TreeNode.ViewsFolder,
        is TreeNode.ColumnsFolder, is TreeNode.IndexesFolder,
        is TreeNode.TableNode,
        -> true
        else -> false
    }

private fun getNodeIcon(
    node: TreeNode,
    terminology: DatabaseTerminology?,
): ImageVector =
    when (node) {
        is TreeNode.DatabaseNode -> Icons.Default.Storage
        is TreeNode.SchemaNode -> Icons.Default.Schema
        is TreeNode.TablesFolder ->
            when (terminology?.category) {
                DatabaseCategory.DOCUMENT -> Icons.Default.Folder
                DatabaseCategory.SEARCH_ENGINE -> Icons.Default.Search
                else -> Icons.Default.TableChart
            }
        is TreeNode.ViewsFolder -> Icons.AutoMirrored.Filled.ViewList
        is TreeNode.ColumnsFolder -> Icons.Default.ViewColumn
        is TreeNode.IndexesFolder -> Icons.AutoMirrored.Filled.Sort
        is TreeNode.TableNode ->
            when (terminology?.category) {
                DatabaseCategory.DOCUMENT -> Icons.Default.Description
                DatabaseCategory.SEARCH_ENGINE -> Icons.Default.Inventory
                else -> Icons.Default.TableRows
            }
        is TreeNode.ViewNode -> Icons.Default.RemoveRedEye
        is TreeNode.ColumnNode -> Icons.Default.ViewColumn
        is TreeNode.IndexNode -> Icons.AutoMirrored.Filled.Sort
    }

@Composable
private fun getNodeColor(node: TreeNode): androidx.compose.ui.graphics.Color =
    when (node) {
        is TreeNode.TablesFolder, is TreeNode.ViewsFolder,
        is TreeNode.ColumnsFolder, is TreeNode.IndexesFolder,
        -> DBQueTheme.extendedColors.treeFolder
        is TreeNode.TableNode -> DBQueTheme.extendedColors.treeTable
        is TreeNode.ViewNode -> DBQueTheme.extendedColors.treeView
        is TreeNode.ColumnNode -> DBQueTheme.extendedColors.treeColumn
        is TreeNode.IndexNode -> DBQueTheme.extendedColors.treeIndex
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

private fun getNodeDisplayText(
    node: TreeNode,
    terminology: DatabaseTerminology?,
): String =
    when (node) {
        is TreeNode.TablesFolder -> terminology?.tableLabel ?: "Tables"
        is TreeNode.ColumnNode -> {
            val pk = if (node.column.autoIncrement) " PK" else ""
            val nullable = if (node.column.nullable) "" else " NOT NULL"
            "${node.name} : ${node.column.typeDisplay}$pk$nullable"
        }
        is TreeNode.IndexNode -> {
            val columns = node.index.columns.joinToString(", ")
            "${node.name} ($columns)"
        }
        else -> node.name
    }

private fun getNodeSuffix(node: TreeNode): String? =
    when (node) {
        is TreeNode.IndexNode -> if (node.index.unique) "UNIQUE" else null
        else -> null
    }

private fun buildContextMenuItems(
    node: TreeNode,
    onEvent: (ExplorerEvent) -> Unit,
    terminology: DatabaseTerminology?,
): List<ContextMenuItem> =
    buildList {
        add(ContextMenuItem("Copy Name") { onEvent(ExplorerEvent.CopyName(node.name)) })

        when (node) {
            is TreeNode.TablesFolder -> {
                // Add "Create Index..." for Elasticsearch
                if (terminology?.category == DatabaseCategory.SEARCH_ENGINE) {
                    add(
                        ContextMenuItem("Create Index...") {
                            onEvent(ExplorerEvent.ShowCreateIndexDialog)
                        },
                    )
                }
            }
            is TreeNode.TableNode -> {
                add(
                    ContextMenuItem(terminology?.selectAction ?: "SELECT * FROM...") {
                        onEvent(ExplorerEvent.GenerateSelect(node.table.name, node.table.schema))
                    },
                )
                add(
                    ContextMenuItem(terminology?.insertAction ?: "INSERT INTO...") {
                        onEvent(ExplorerEvent.GenerateInsert(node.table.name, node.table.schema))
                    },
                )
                add(
                    ContextMenuItem(terminology?.ddlAction ?: "Generate DDL") {
                        onEvent(ExplorerEvent.GenerateDdl(node.table.name, node.table.schema))
                    },
                )

                // Add Elasticsearch-specific index management options
                if (terminology?.category == DatabaseCategory.SEARCH_ENGINE) {
                    add(
                        ContextMenuItem("Edit Settings...") {
                            onEvent(ExplorerEvent.ShowEditIndexSettingsDialog(node.table.name))
                        },
                    )
                    add(
                        ContextMenuItem("Edit Mappings...") {
                            onEvent(ExplorerEvent.ShowEditIndexMappingsDialog(node.table.name))
                        },
                    )
                    add(
                        ContextMenuItem("Delete Index") {
                            onEvent(ExplorerEvent.ConfirmDeleteIndex(node.table.name))
                        },
                    )
                }
            }
            is TreeNode.ViewNode -> {
                add(
                    ContextMenuItem(terminology?.selectAction ?: "SELECT * FROM...") {
                        onEvent(ExplorerEvent.GenerateSelect(node.view.name, node.view.schema))
                    },
                )
            }
            else -> {}
        }
    }
