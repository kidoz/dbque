package su.kidoz.feature.diagram

import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import su.kidoz.core.model.DatabaseType
import su.kidoz.database.ConnectionManager
import su.kidoz.mvi.MviViewModel

class DiagramViewModel(
    private val connectionManager: ConnectionManager,
) : MviViewModel<DiagramState, DiagramEvent, DiagramEffect>(DiagramState()) {
    init {
        connectionManager.activeConnectionId
            .onEach { connectionId ->
                if (connectionId != currentState.connectionId) {
                    updateState {
                        DiagramState(connectionId = connectionId)
                    }
                }
            }.launchIn(viewModelScope)
    }

    override fun onEvent(event: DiagramEvent) {
        when (event) {
            DiagramEvent.LoadDiagram -> {
                loadDiagram()
            }

            is DiagramEvent.SelectSchema -> {
                updateState { copy(selectedSchema = event.schema) }
                loadDiagram()
            }

            is DiagramEvent.SelectTable -> {
                updateState {
                    copy(selectedElement = DiagramSelection.Table(event.tableId))
                }
            }

            is DiagramEvent.SelectRelationship -> {
                updateState {
                    copy(selectedElement = DiagramSelection.Relationship(event.relationshipId))
                }
            }

            DiagramEvent.ClearSelection -> {
                updateState { copy(selectedElement = DiagramSelection.None) }
            }

            is DiagramEvent.MoveTable -> {
                moveTable(event.tableId, event.x, event.y)
            }

            DiagramEvent.ZoomIn -> {
                updateState { copy(zoom = (zoom + 0.1f).coerceAtMost(1.8f)) }
            }

            DiagramEvent.ZoomOut -> {
                updateState { copy(zoom = (zoom - 0.1f).coerceAtLeast(0.6f)) }
            }

            DiagramEvent.ResetZoom -> {
                updateState { copy(zoom = 1f) }
            }

            DiagramEvent.ToggleKeysOnly -> {
                updateState { copy(showKeysOnly = !showKeysOnly) }
            }

            DiagramEvent.ToggleDdlPreview -> {
                updateState { copy(showDdlPreview = !showDdlPreview) }
            }

            DiagramEvent.AddTable -> {
                addTable()
            }

            is DiagramEvent.RenameSelectedTable -> {
                renameSelectedTable(event.name)
            }

            DiagramEvent.AddColumnToSelectedTable -> {
                addColumnToSelectedTable()
            }

            is DiagramEvent.RenameColumn -> {
                updateColumn(event.columnId) { copy(name = event.name) }
            }

            is DiagramEvent.ChangeColumnType -> {
                updateColumn(event.columnId) { copy(type = event.type) }
            }

            is DiagramEvent.ToggleColumnNullable -> {
                updateColumn(event.columnId) { copy(nullable = !nullable) }
            }

            is DiagramEvent.ToggleColumnPrimaryKey -> {
                updateColumn(event.columnId) {
                    copy(isPrimaryKey = !isPrimaryKey, nullable = if (!isPrimaryKey) false else nullable)
                }
            }

            DiagramEvent.DeleteSelected -> {
                deleteSelected()
            }
        }
    }

    private fun loadDiagram() {
        val activeConnection = connectionManager.activeConnection
        if (activeConnection == null) {
            updateState {
                copy(
                    isLoading = false,
                    error = "Connect to a database to generate an ER diagram.",
                    tables = emptyList(),
                    relationships = emptyList(),
                )
            }
            return
        }

        if (activeConnection.config.type in listOf(DatabaseType.MONGODB, DatabaseType.ELASTICSEARCH)) {
            updateState {
                copy(
                    connectionId = activeConnection.config.id,
                    databaseType = activeConnection.config.type,
                    isLoading = false,
                    error = "ER relationship diagrams are currently available for JDBC databases.",
                    tables = emptyList(),
                    relationships = emptyList(),
                )
            }
            return
        }

        viewModelScope.launch {
            updateState {
                copy(
                    connectionId = activeConnection.config.id,
                    databaseType = activeConnection.config.type,
                    isLoading = true,
                    error = null,
                )
            }

            try {
                activeConnection.getConnection().use { connection ->
                    val driver = activeConnection.driver
                    val schemas = driver.getSchemas(connection).map { it.name }
                    val defaultSchema = driver.getDefaultSchema(connection)
                    val schema =
                        currentState.selectedSchema
                            ?: defaultSchema?.takeIf { it in schemas }
                            ?: schemas.firstOrNull()
                    val tables = driver.getTables(connection, schema).take(currentState.maxTables)
                    val metadata =
                        tables.map { table ->
                            val columns = driver.getColumns(connection, table.name, table.schema)
                            TableMetadata(
                                table = table,
                                columns = columns,
                                primaryKey = driver.getPrimaryKey(connection, table.name, table.schema),
                                foreignKeys = driver.getForeignKeys(connection, table.name, table.schema),
                            )
                        }
                    val model = DiagramModelBuilder.build(metadata)

                    updateState {
                        copy(
                            schemas = schemas,
                            selectedSchema = schema,
                            tables = model.tables,
                            relationships = model.relationships,
                            selectedElement = DiagramSelection.None,
                            isLoading = false,
                            error = null,
                            ddlPreview = DiagramDdlGenerator.generate(model.tables, model.relationships),
                        )
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to generate ER diagram" }
                updateState {
                    copy(
                        isLoading = false,
                        error = e.message ?: "Failed to generate ER diagram",
                    )
                }
                sendEffect(DiagramEffect.ShowError(e.message ?: "Failed to generate ER diagram"))
            }
        }
    }

    private fun moveTable(
        tableId: String,
        x: Float,
        y: Float,
    ) {
        updateState {
            copy(
                tables =
                    tables.map {
                        if (it.id == tableId) {
                            it.copy(x = x.coerceAtLeast(0f), y = y.coerceAtLeast(0f))
                        } else {
                            it
                        }
                    },
            )
        }
    }

    private fun addTable() {
        val draftNumber = currentState.tables.count { it.isDraft } + 1
        val tableId = "draft.table_$draftNumber"
        val table =
            DiagramTable(
                id = tableId,
                name = "table_$draftNumber",
                schema = currentState.selectedSchema,
                x = 48f + draftNumber * 28f,
                y = 48f + draftNumber * 28f,
                columns =
                    listOf(
                        DiagramColumn(
                            id = "$tableId:id",
                            name = "id",
                            type = "INTEGER",
                            nullable = false,
                            isPrimaryKey = true,
                            isDraft = true,
                        ),
                    ),
                isDraft = true,
            )
        updateState {
            val nextTables = tables + table
            copy(
                tables = nextTables,
                selectedElement = DiagramSelection.Table(table.id),
                ddlPreview = DiagramDdlGenerator.generate(nextTables, relationships),
            )
        }
    }

    private fun renameSelectedTable(name: String) {
        val selectedTableId = (currentState.selectedElement as? DiagramSelection.Table)?.tableId ?: return
        updateState {
            val nextTables =
                tables.map {
                    if (it.id == selectedTableId) {
                        it.copy(name = name)
                    } else {
                        it
                    }
                }
            copy(tables = nextTables, ddlPreview = DiagramDdlGenerator.generate(nextTables, relationships))
        }
    }

    private fun addColumnToSelectedTable() {
        val selectedTableId = (currentState.selectedElement as? DiagramSelection.Table)?.tableId ?: return
        updateState {
            val nextTables =
                tables.map { table ->
                    if (table.id == selectedTableId) {
                        val columnNumber = table.columns.size + 1
                        table.copy(
                            columns =
                                table.columns +
                                    DiagramColumn(
                                        id = "${table.id}:column_$columnNumber",
                                        name = "column_$columnNumber",
                                        type = "TEXT",
                                        nullable = true,
                                        isDraft = true,
                                    ),
                        )
                    } else {
                        table
                    }
                }
            copy(tables = nextTables, ddlPreview = DiagramDdlGenerator.generate(nextTables, relationships))
        }
    }

    private fun updateColumn(
        columnId: String,
        reducer: DiagramColumn.() -> DiagramColumn,
    ) {
        updateState {
            val nextTables =
                tables.map { table ->
                    table.copy(
                        columns =
                            table.columns.map { column ->
                                if (column.id == columnId) {
                                    column.reducer()
                                } else {
                                    column
                                }
                            },
                    )
                }
            copy(tables = nextTables, ddlPreview = DiagramDdlGenerator.generate(nextTables, relationships))
        }
    }

    private fun deleteSelected() {
        when (val selected = currentState.selectedElement) {
            DiagramSelection.None -> {
                return
            }

            is DiagramSelection.Relationship -> {
                updateState {
                    val nextRelationships = relationships.filterNot { it.id == selected.relationshipId }
                    copy(
                        relationships = nextRelationships,
                        selectedElement = DiagramSelection.None,
                        ddlPreview = DiagramDdlGenerator.generate(tables, nextRelationships),
                    )
                }
            }

            is DiagramSelection.Table -> {
                updateState {
                    val nextTables = tables.filterNot { it.id == selected.tableId }
                    val nextRelationships =
                        relationships.filterNot {
                            it.sourceTableId == selected.tableId || it.targetTableId == selected.tableId
                        }
                    copy(
                        tables = nextTables,
                        relationships = nextRelationships,
                        selectedElement = DiagramSelection.None,
                        ddlPreview = DiagramDdlGenerator.generate(nextTables, nextRelationships),
                    )
                }
            }
        }
    }
}
