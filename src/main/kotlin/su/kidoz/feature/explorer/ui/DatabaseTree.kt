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
import su.kidoz.core.model.DatabaseInfo
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
                // Render MongoDB database hierarchy
                if (state.usesDatabaseHierarchy && state.databases.isNotEmpty()) {
                    state.databases.forEach { database ->
                        val dbNodeId = "db:${database.name}"
                        val isDatabaseExpanded = state.expandedNodes.contains(dbNodeId)
                        val isDatabaseLoading = state.isDatabaseLoading(database.name)

                        item(key = dbNodeId) {
                            DatabaseNodeItem(
                                database = database,
                                isExpanded = isDatabaseExpanded,
                                isLoading = isDatabaseLoading,
                                onToggle = {
                                    if (isDatabaseExpanded) {
                                        onEvent(ExplorerEvent.CollapseDatabase(database.name))
                                    } else {
                                        onEvent(ExplorerEvent.ExpandDatabase(database.name))
                                    }
                                },
                            )
                        }

                        if (isDatabaseExpanded) {
                            val collections = state.getCollectionsForDatabase(database.name)

                            items(collections, key = { "collection:${database.name}:${it.name}" }) { collection ->
                                val collectionNodeId = "collection:${database.name}:${collection.name}"
                                val isCollectionExpanded = state.expandedNodes.contains(collectionNodeId)

                                TreeNodeItem(
                                    node = TreeNode.CollectionNode(collection, database.name),
                                    isExpanded = isCollectionExpanded,
                                    isSelected = (state.selectedNode as? TreeNode.CollectionNode)?.collection?.name == collection.name,
                                    level = 1,
                                    onToggle = { onEvent(ExplorerEvent.ToggleNode(collectionNodeId)) },
                                    onSelect = { onEvent(ExplorerEvent.SelectNode(TreeNode.CollectionNode(collection, database.name))) },
                                    onEvent = onEvent,
                                    terminology = terminology,
                                )

                                // Show Fields and Indexes folders when collection is expanded
                                if (isCollectionExpanded) {
                                    val details = state.tableDetails?.takeIf { it.table.name == collection.name }

                                    // Fields folder
                                    val fieldsFolderId = "fields:${database.name}:${collection.name}"
                                    TreeNodeItem(
                                        node = TreeNode.FieldsFolder(collection.name, database.name),
                                        isExpanded = state.expandedNodes.contains(fieldsFolderId),
                                        isSelected = false,
                                        level = 2,
                                        onToggle = { onEvent(ExplorerEvent.ToggleNode(fieldsFolderId)) },
                                        onSelect = {},
                                        onEvent = onEvent,
                                        terminology = terminology,
                                        itemCount = details?.columns?.size,
                                    )

                                    // Show fields when Fields folder is expanded
                                    if (state.expandedNodes.contains(fieldsFolderId)) {
                                        details?.columns?.forEach { column ->
                                            TreeNodeItem(
                                                node = TreeNode.ColumnNode(column, collection.name),
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
                                        val indexesFolderId = "indexes:${database.name}:${collection.name}"
                                        TreeNodeItem(
                                            node = TreeNode.IndexesFolder(collection.name, null),
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
                                                    node = TreeNode.IndexNode(index, collection.name),
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
                    }
                } else if (state.usesIndexHierarchy && state.tables.isNotEmpty()) {
                    // Elasticsearch index hierarchy: Indices folder -> Index -> Fields
                    item(key = "indices:") {
                        TreeNodeItem(
                            node = TreeNode.TablesFolder(null, null),
                            isExpanded = state.expandedNodes.contains("indices:"),
                            isSelected = false,
                            level = 0,
                            onToggle = { onEvent(ExplorerEvent.ToggleNode("indices:")) },
                            onSelect = {},
                            onEvent = onEvent,
                            terminology = terminology,
                            itemCount = state.tables.size,
                        )
                    }

                    if (state.expandedNodes.contains("indices:")) {
                        state.tables.forEach { index ->
                            val indexNodeId = "esindex:${index.name}"
                            val isIndexExpanded = state.expandedNodes.contains(indexNodeId)
                            val isIndexLoading = state.isIndexLoading(index.name)

                            item(key = indexNodeId) {
                                IndexNodeItem(
                                    index = index,
                                    isExpanded = isIndexExpanded,
                                    isLoading = isIndexLoading,
                                    isSelected = (state.selectedNode as? TreeNode.IndexNodeElasticsearch)?.index?.name == index.name,
                                    onToggle = {
                                        if (isIndexExpanded) {
                                            onEvent(ExplorerEvent.CollapseIndex(index.name))
                                        } else {
                                            onEvent(ExplorerEvent.ExpandIndex(index.name))
                                        }
                                    },
                                    onSelect = { onEvent(ExplorerEvent.SelectNode(TreeNode.IndexNodeElasticsearch(index))) },
                                    onEvent = onEvent,
                                    terminology = terminology,
                                )
                            }

                            if (isIndexExpanded) {
                                val fields = state.getFieldsForIndex(index.name)
                                val hasMoreFields = state.hasMoreFields(index.name)

                                // Fields folder
                                val fieldsFolderId = "esfields:${index.name}"
                                item(key = fieldsFolderId) {
                                    TreeNodeItem(
                                        node = TreeNode.IndexFieldsFolder(index.name),
                                        isExpanded = state.expandedNodes.contains(fieldsFolderId),
                                        isSelected = false,
                                        level = 2,
                                        onToggle = { onEvent(ExplorerEvent.ToggleNode(fieldsFolderId)) },
                                        onSelect = {},
                                        onEvent = onEvent,
                                        terminology = terminology,
                                        itemCount = fields.size,
                                        hasMore = hasMoreFields,
                                    )
                                }

                                // Show fields when Fields folder is expanded
                                if (state.expandedNodes.contains(fieldsFolderId)) {
                                    fields.forEach { field ->
                                        item(key = "esfield:${index.name}:${field.name}") {
                                            TreeNodeItem(
                                                node = TreeNode.ColumnNode(field, index.name),
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

                                    // Show truncation message if there are more fields
                                    if (hasMoreFields) {
                                        item(key = "esfields-more:${index.name}") {
                                            MoreFieldsIndicator(
                                                level = 3,
                                                limit = state.indexFieldLimit,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (state.usesSchemas && state.schemas.isNotEmpty()) {
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
private fun DatabaseNodeItem(
    database: DatabaseInfo,
    isExpanded: Boolean,
    isLoading: Boolean,
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

        // Database icon
        Icon(
            imageVector = Icons.Default.Storage,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        // Database name
        Text(
            text = database.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
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
private fun IndexNodeItem(
    index: su.kidoz.core.model.TableInfo,
    isExpanded: Boolean,
    isLoading: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit,
    onEvent: (ExplorerEvent) -> Unit,
    terminology: DatabaseTerminology?,
) {
    val contextMenuItems =
        remember(index, terminology) {
            buildList {
                add(ContextMenuItem("Copy Name") { onEvent(ExplorerEvent.CopyName(index.name)) })
                add(
                    ContextMenuItem(terminology?.selectAction ?: "Search...") {
                        onEvent(ExplorerEvent.GenerateSelect(index.name, null))
                    },
                )
                add(
                    ContextMenuItem(terminology?.insertAction ?: "Index Document...") {
                        onEvent(ExplorerEvent.GenerateInsert(index.name, null))
                    },
                )
                add(
                    ContextMenuItem(terminology?.ddlAction ?: "Get Mappings") {
                        onEvent(ExplorerEvent.GenerateDdl(index.name, null))
                    },
                )
                add(
                    ContextMenuItem("Edit Settings...") {
                        onEvent(ExplorerEvent.ShowEditIndexSettingsDialog(index.name))
                    },
                )
                add(
                    ContextMenuItem("Edit Mappings...") {
                        onEvent(ExplorerEvent.ShowEditIndexMappingsDialog(index.name))
                    },
                )
                add(
                    ContextMenuItem("Delete Index") {
                        onEvent(ExplorerEvent.ConfirmDeleteIndex(index.name))
                    },
                )
            }
        }

    ContextMenuArea(items = { contextMenuItems }) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .then(
                        if (isSelected) {
                            Modifier.background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            )
                        } else {
                            Modifier
                        },
                    ).padding(start = 28.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Expand/collapse or loading icon
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

            // Index icon
            Icon(
                imageVector = Icons.Default.Inventory,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = DBQueTheme.extendedColors.treeTable,
            )

            // Index name
            Text(
                text = index.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // Index info (doc count, size)
            index.comment?.let { comment ->
                Text(
                    text = comment,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MoreFieldsIndicator(
    level: Int,
    limit: Int,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = (12 + level * 16).dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Spacer(Modifier.width(16.dp))

        Icon(
            imageVector = Icons.Default.MoreHoriz,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.tertiary,
        )

        Text(
            text = "Limited to $limit fields",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
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
    hasMore: Boolean = false,
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
                    text = if (hasMore) "($itemCount+)" else "($itemCount)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasMore) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
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
        is TreeNode.TableNode, is TreeNode.CollectionNode,
        is TreeNode.FieldsFolder, is TreeNode.IndexNodeElasticsearch,
        is TreeNode.IndexFieldsFolder,
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
        is TreeNode.FieldsFolder -> Icons.Default.ViewColumn
        is TreeNode.IndexFieldsFolder -> Icons.Default.ViewColumn
        is TreeNode.IndexNodeElasticsearch -> Icons.Default.Inventory
        is TreeNode.TableNode ->
            when (terminology?.category) {
                DatabaseCategory.DOCUMENT -> Icons.Default.Description
                DatabaseCategory.SEARCH_ENGINE -> Icons.Default.Inventory
                else -> Icons.Default.TableRows
            }
        is TreeNode.CollectionNode -> Icons.Default.Description
        is TreeNode.ViewNode -> Icons.Default.RemoveRedEye
        is TreeNode.ColumnNode -> Icons.Default.ViewColumn
        is TreeNode.IndexNode -> Icons.AutoMirrored.Filled.Sort
    }

@Composable
private fun getNodeColor(node: TreeNode): androidx.compose.ui.graphics.Color =
    when (node) {
        is TreeNode.TablesFolder, is TreeNode.ViewsFolder,
        is TreeNode.ColumnsFolder, is TreeNode.IndexesFolder,
        is TreeNode.FieldsFolder, is TreeNode.IndexFieldsFolder,
        -> DBQueTheme.extendedColors.treeFolder
        is TreeNode.TableNode -> DBQueTheme.extendedColors.treeTable
        is TreeNode.CollectionNode -> DBQueTheme.extendedColors.treeTable
        is TreeNode.IndexNodeElasticsearch -> DBQueTheme.extendedColors.treeTable
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
            is TreeNode.CollectionNode -> {
                add(
                    ContextMenuItem("db.find({})...") {
                        onEvent(ExplorerEvent.GenerateSelect(node.collection.name, null))
                    },
                )
                add(
                    ContextMenuItem("db.insertOne({})...") {
                        onEvent(ExplorerEvent.GenerateInsert(node.collection.name, null))
                    },
                )
                add(
                    ContextMenuItem("Collection DDL") {
                        onEvent(ExplorerEvent.GenerateDdl(node.collection.name, null))
                    },
                )
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
