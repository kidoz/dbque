package su.kidoz.feature.diagram.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import su.kidoz.feature.diagram.DiagramColumn
import su.kidoz.feature.diagram.DiagramEvent
import su.kidoz.feature.diagram.DiagramRelationship
import su.kidoz.feature.diagram.DiagramSelection
import su.kidoz.feature.diagram.DiagramState
import su.kidoz.feature.diagram.DiagramTable
import su.kidoz.ui.components.HorizontalSplitPane
import su.kidoz.ui.theme.DBQueTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

private const val TABLE_WIDTH = 260f
private const val TABLE_HEADER_HEIGHT = 46f
private const val TABLE_ROW_HEIGHT = 30f

@Composable
fun ErDiagramPanel(
    state: DiagramState,
    onEvent: (DiagramEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxSize()) {
            DiagramToolbar(state = state, onEvent = onEvent)

            if (state.isLoading || state.isApplyingDdl) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            } else if (state.error != null) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                HorizontalSplitPane(
                    splitFraction = if (state.showDdlPreview) 0.72f else 0.78f,
                    firstPane = {
                        DiagramCanvas(
                            state = state,
                            onEvent = onEvent,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    secondPane = {
                        DiagramInspector(
                            state = state,
                            onEvent = onEvent,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        state.pendingApplyDdl?.let { ddl ->
            ApplyDdlDialog(
                ddl = ddl,
                onConfirm = { onEvent(DiagramEvent.ConfirmApplyDdl) },
                onCancel = { onEvent(DiagramEvent.CancelApplyDdl) },
            )
        }
    }
}

@Composable
private fun DiagramToolbar(
    state: DiagramState,
    onEvent: (DiagramEvent) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("ER Diagram", style = MaterialTheme.typography.titleSmall)

            SchemaSelector(
                schemas = state.schemas,
                selectedSchema = state.selectedSchema,
                onSchemaSelected = { onEvent(DiagramEvent.SelectSchema(it)) },
            )

            TooltipIconButton(
                tooltip = "Load diagram",
                onClick = { onEvent(DiagramEvent.LoadDiagram) },
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Load diagram")
            }

            VerticalDivider(modifier = Modifier.height(24.dp))

            TooltipIconButton(
                tooltip = "Zoom out",
                onClick = { onEvent(DiagramEvent.ZoomOut) },
            ) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out")
            }
            Text("${(state.zoom * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            TooltipIconButton(
                tooltip = "Zoom in",
                onClick = { onEvent(DiagramEvent.ZoomIn) },
            ) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in")
            }
            TooltipIconButton(
                tooltip = "Reset zoom",
                onClick = { onEvent(DiagramEvent.ResetZoom) },
            ) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = "Reset zoom")
            }

            Spacer(Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.showKeysOnly,
                    onCheckedChange = { onEvent(DiagramEvent.ToggleKeysOnly) },
                )
                Text("Keys", style = MaterialTheme.typography.labelMedium)
            }

            OutlinedButton(onClick = { onEvent(DiagramEvent.AddTable) }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Table")
            }

            OutlinedButton(onClick = { onEvent(DiagramEvent.ToggleDdlPreview) }) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("DDL")
            }

            TooltipIconButton(
                tooltip = "Copy DDL",
                onClick = { onEvent(DiagramEvent.CopyDdlToClipboard) },
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy DDL")
            }

            Button(onClick = { onEvent(DiagramEvent.InsertDdlIntoEditor) }) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Insert")
            }

            Button(onClick = { onEvent(DiagramEvent.RequestApplyDdl) }) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Apply")
            }
        }
    }
}

@Composable
private fun ApplyDdlDialog(
    ddl: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Apply DDL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("This will execute the generated DDL in a transaction and refresh the diagram.")
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                ) {
                    Text(
                        text = ddl,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SchemaSelector(
    schemas: List<String>,
    selectedSchema: String?,
    onSchemaSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selectedSchema ?: "Default schema", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Default schema") },
                onClick = {
                    expanded = false
                    onSchemaSelected(null)
                },
            )
            schemas.forEach { schema ->
                DropdownMenuItem(
                    text = { Text(schema) },
                    onClick = {
                        expanded = false
                        onSchemaSelected(schema)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TooltipIconButton(
    tooltip: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}

@Composable
private fun DiagramCanvas(
    state: DiagramState,
    onEvent: (DiagramEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    val canvasWidth = max(1200f, (state.tables.maxOfOrNull { it.x + TABLE_WIDTH + 120f } ?: 1200f))
    val canvasHeight =
        max(
            760f,
            (
                state.tables.maxOfOrNull {
                    it.y + TABLE_HEADER_HEIGHT + visibleColumns(it, state.showKeysOnly).size * TABLE_ROW_HEIGHT + 140f
                } ?: 760f
            ),
        )
    val selectedRelationshipId = (state.selectedElement as? DiagramSelection.Relationship)?.relationshipId

    Box(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.background)
                .horizontalScroll(horizontalScroll)
                .verticalScroll(verticalScroll)
                .clickable { onEvent(DiagramEvent.ClearSelection) },
    ) {
        Box(
            modifier =
                Modifier
                    .size(canvasWidth.dp, canvasHeight.dp)
                    .graphicsLayer(
                        scaleX = state.zoom,
                        scaleY = state.zoom,
                        transformOrigin =
                            androidx.compose.ui.graphics
                                .TransformOrigin(0f, 0f),
                    ),
        ) {
            Canvas(
                modifier =
                    Modifier
                        .matchParentSize()
                        .pointerInput(state.relationships, state.tables) {
                            detectTapGestures { offset ->
                                findRelationshipAt(offset, state.tables, state.relationships)?.let { relationship ->
                                    onEvent(DiagramEvent.SelectRelationship(relationship.id))
                                }
                            }
                        },
            ) {
                state.relationships.forEach { relationship ->
                    val source = state.tables.firstOrNull { it.id == relationship.sourceTableId }
                    val target = state.tables.firstOrNull { it.id == relationship.targetTableId }
                    if (source != null && target != null) {
                        val selected = relationship.id == selectedRelationshipId
                        drawLine(
                            color =
                                if (selected) {
                                    Color(0xFFF5A524)
                                } else {
                                    Color(0xFF42C0B6)
                                },
                            start = Offset(source.x + TABLE_WIDTH, source.y + TABLE_HEADER_HEIGHT),
                            end = Offset(target.x, target.y + TABLE_HEADER_HEIGHT),
                            strokeWidth = if (selected) 4f else 2f,
                            pathEffect =
                                if (relationship.isDraft) {
                                    PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                                } else {
                                    null
                                },
                        )
                    }
                }
            }

            state.tables.forEach { table ->
                DiagramTableCard(
                    table = table,
                    selected = (state.selectedElement as? DiagramSelection.Table)?.tableId == table.id,
                    showKeysOnly = state.showKeysOnly,
                    zoom = state.zoom,
                    onSelect = { onEvent(DiagramEvent.SelectTable(table.id)) },
                    onMove = { x, y -> onEvent(DiagramEvent.MoveTable(table.id, x, y)) },
                )
            }
        }
    }
}

@Composable
private fun DiagramTableCard(
    table: DiagramTable,
    selected: Boolean,
    showKeysOnly: Boolean,
    zoom: Float,
    onSelect: () -> Unit,
    onMove: (Float, Float) -> Unit,
) {
    val colors = DBQueTheme.extendedColors
    val borderColor =
        when {
            selected -> MaterialTheme.colorScheme.primary
            table.isDraft -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.outline
        }
    val columns = visibleColumns(table, showKeysOnly)

    Surface(
        modifier =
            Modifier
                .offset(table.x.dp, table.y.dp)
                .width(TABLE_WIDTH.dp)
                .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                .clickable { onSelect() }
                .pointerInput(table.id, table.x, table.y, zoom) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onMove(
                            table.x + dragAmount.x / zoom,
                            table.y + dragAmount.y / zoom,
                        )
                    }
                },
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (selected) 4.dp else 1.dp,
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(TABLE_HEADER_HEIGHT.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.TableChart,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = table.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            columns.forEach { column ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(TABLE_ROW_HEIGHT.dp)
                            .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (column.isPrimaryKey) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = colors.treeIndex,
                        )
                    } else {
                        Spacer(Modifier.size(14.dp))
                    }
                    Text(
                        text = column.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = column.type,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (column.isForeignKey) {
                                colors.treeTable
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagramInspector(
    state: DiagramState,
    onEvent: (DiagramEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (val selection = state.selectedElement) {
            DiagramSelection.None -> {
                DiagramSummary(state)
            }

            is DiagramSelection.Relationship -> {
                val relationship = state.relationships.firstOrNull { it.id == selection.relationshipId }
                if (relationship != null) {
                    RelationshipInspector(relationship = relationship, tables = state.tables, onEvent = onEvent)
                } else {
                    DiagramSummary(state)
                }
            }

            is DiagramSelection.Table -> {
                val table = state.tables.firstOrNull { it.id == selection.tableId }
                if (table != null) {
                    TableInspector(table = table, tables = state.tables, onEvent = onEvent)
                } else {
                    DiagramSummary(state)
                }
            }
        }

        if (state.validationIssues.isNotEmpty()) {
            ValidationIssues(state.validationIssues)
        }

        if (state.showDdlPreview) {
            HorizontalDivider()
            Text("DDL Preview", style = MaterialTheme.typography.titleSmall)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(6.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
            ) {
                Text(
                    text = state.ddlPreview.ifBlank { "-- No draft changes" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun DiagramSummary(state: DiagramState) {
    Text("Schema", style = MaterialTheme.typography.titleSmall)
    Text(
        text = state.selectedSchema ?: "Default schema",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Metric(label = "Tables", value = state.tables.size.toString())
        Metric(label = "Links", value = state.relationships.size.toString())
    }
}

@Composable
private fun Metric(
    label: String,
    value: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RelationshipInspector(
    relationship: DiagramRelationship,
    tables: List<DiagramTable>,
    onEvent: (DiagramEvent) -> Unit,
) {
    val source = tables.firstOrNull { it.id == relationship.sourceTableId }
    val target = tables.firstOrNull { it.id == relationship.targetTableId }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Relationship", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        if (relationship.isDraft) {
            IconButton(onClick = { onEvent(DiagramEvent.DeleteSelected) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
    Text(relationship.name ?: "Foreign key", style = MaterialTheme.typography.bodyMedium)
    Text(
        text =
            "${source?.displayName ?: relationship.sourceTableId} " +
                "(${relationship.sourceColumns.joinToString()})",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text =
            "${target?.displayName ?: relationship.targetTableId} " +
                "(${relationship.targetColumns.joinToString()})",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TableInspector(
    table: DiagramTable,
    tables: List<DiagramTable>,
    onEvent: (DiagramEvent) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Table", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        if (table.isDraft) {
            IconButton(onClick = { onEvent(DiagramEvent.DeleteSelected) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
    OutlinedTextField(
        value = table.name,
        onValueChange = { onEvent(DiagramEvent.RenameSelectedTable(it)) },
        enabled = table.isDraft,
        label = { Text("Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Columns", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = { onEvent(DiagramEvent.AddColumnToSelectedTable) }, enabled = table.isDraft) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Column")
        }
    }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        table.columns.forEach { column ->
            ColumnEditor(column = column, enabled = table.isDraft || column.isDraft, onEvent = onEvent)
        }

        DraftRelationshipEditor(table = table, tables = tables, onEvent = onEvent)
    }
}

@Composable
private fun ValidationIssues(issues: List<String>) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(6.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Draft issues",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            issues.take(4).forEach { issue ->
                Text(
                    text = issue,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun DraftRelationshipEditor(
    table: DiagramTable,
    tables: List<DiagramTable>,
    onEvent: (DiagramEvent) -> Unit,
) {
    val targetTables = tables.filter { it.id != table.id }
    if (targetTables.isEmpty() || table.columns.isEmpty()) return

    val targetKey = targetTables.joinToString("|") { it.id }
    var sourceColumn by remember(table.id, table.columns.joinToString("|") { it.name }) {
        mutableStateOf(table.columns.firstOrNull { !it.isPrimaryKey }?.name ?: table.columns.first().name)
    }
    var targetTableId by remember(table.id, targetKey) {
        mutableStateOf(targetTables.first().id)
    }
    val targetTable = targetTables.firstOrNull { it.id == targetTableId } ?: targetTables.first()
    var targetColumn by remember(table.id, targetTable.id, targetTable.columns.joinToString("|") { it.name }) {
        val defaultTargetColumn =
            targetTable.columns.firstOrNull { it.isPrimaryKey }
                ?: targetTable.columns.firstOrNull()
        mutableStateOf(
            defaultTargetColumn?.name.orEmpty(),
        )
    }

    HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
    Text("Draft Relationship", style = MaterialTheme.typography.titleSmall)

    OptionDropdown(
        label = "Source column",
        value = sourceColumn,
        options = table.columns.map { it.name },
        onSelected = { sourceColumn = it },
    )
    OptionDropdown(
        label = "Target table",
        value = targetTable.displayName,
        options = targetTables.map { it.id },
        optionLabel = { id -> targetTables.firstOrNull { it.id == id }?.displayName ?: id },
        onSelected = { targetTableId = it },
    )
    OptionDropdown(
        label = "Target column",
        value = targetColumn,
        options = targetTable.columns.map { it.name },
        onSelected = { targetColumn = it },
    )
    Button(
        onClick = {
            onEvent(
                DiagramEvent.AddRelationship(
                    sourceTableId = table.id,
                    sourceColumn = sourceColumn,
                    targetTableId = targetTable.id,
                    targetColumn = targetColumn,
                ),
            )
        },
        enabled = sourceColumn.isNotBlank() && targetColumn.isNotBlank(),
    ) {
        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Add Relationship")
    }
}

@Composable
private fun OptionDropdown(
    label: String,
    value: String,
    options: List<String>,
    optionLabel: (String) -> String = { it },
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(optionLabel(value), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnEditor(
    column: DiagramColumn,
    enabled: Boolean,
    onEvent: (DiagramEvent) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = column.name,
                onValueChange = { onEvent(DiagramEvent.RenameColumn(column.id, it)) },
                enabled = enabled,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = column.type,
                onValueChange = { onEvent(DiagramEvent.ChangeColumnType(column.id, it)) },
                enabled = enabled,
                label = { Text("Type") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = column.isPrimaryKey,
                    onCheckedChange = { onEvent(DiagramEvent.ToggleColumnPrimaryKey(column.id)) },
                    enabled = enabled,
                )
                Text("PK", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Checkbox(
                    checked = column.nullable,
                    onCheckedChange = { onEvent(DiagramEvent.ToggleColumnNullable(column.id)) },
                    enabled = enabled && !column.isPrimaryKey,
                )
                Text("Nullable", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun visibleColumns(
    table: DiagramTable,
    showKeysOnly: Boolean,
): List<DiagramColumn> =
    if (showKeysOnly) {
        table.columns.filter { it.isPrimaryKey || it.isForeignKey }
    } else {
        table.columns
    }

private fun findRelationshipAt(
    point: Offset,
    tables: List<DiagramTable>,
    relationships: List<DiagramRelationship>,
): DiagramRelationship? =
    relationships.firstOrNull { relationship ->
        val source = tables.firstOrNull { it.id == relationship.sourceTableId }
        val target = tables.firstOrNull { it.id == relationship.targetTableId }
        source != null &&
            target != null &&
            distanceToSegment(
                point = point,
                start = Offset(source.x + TABLE_WIDTH, source.y + TABLE_HEADER_HEIGHT),
                end = Offset(target.x, target.y + TABLE_HEADER_HEIGHT),
            ) <= 8f
    }

private fun distanceToSegment(
    point: Offset,
    start: Offset,
    end: Offset,
): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    if (abs(dx) < 0.001f && abs(dy) < 0.001f) {
        return distance(point, start)
    }

    val t =
        (((point.x - start.x) * dx + (point.y - start.y) * dy) / (dx * dx + dy * dy))
            .coerceIn(0f, 1f)
    val projection = Offset(start.x + t * dx, start.y + t * dy)
    return distance(point, projection)
}

private fun distance(
    first: Offset,
    second: Offset,
): Float {
    val dx = first.x - second.x
    val dy = first.y - second.y
    return sqrt(dx * dx + dy * dy)
}
