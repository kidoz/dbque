package su.kidoz.feature.explorer

import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import su.kidoz.database.ConnectionManager
import su.kidoz.mvi.MviViewModel
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class ExplorerViewModel(
    private val connectionManager: ConnectionManager,
) : MviViewModel<ExplorerState, ExplorerEvent, ExplorerEffect>(ExplorerState()) {
    init {
        observeActiveConnection()
    }

    private fun observeActiveConnection() {
        connectionManager.activeConnectionId
            .onEach { connectionId ->
                if (connectionId != currentState.connectionId) {
                    updateState { copy(connectionId = connectionId) }
                    connectionId?.let { loadMetadata() }
                }
            }.launchIn(viewModelScope)
    }

    override fun onEvent(event: ExplorerEvent) {
        when (event) {
            is ExplorerEvent.ConnectionChanged -> {
                updateState { copy(connectionId = event.connectionId) }
                event.connectionId?.let { loadMetadata() }
            }
            is ExplorerEvent.Refresh -> loadMetadata()
            is ExplorerEvent.ToggleNode -> toggleNode(event.nodeId)
            is ExplorerEvent.SelectNode -> selectNode(event.node)
            is ExplorerEvent.LoadTableDetails -> loadTableDetails(event.tableName, event.schema)
            is ExplorerEvent.CopyName -> copyToClipboard(event.name)
            is ExplorerEvent.GenerateSelect -> generateSelect(event.tableName, event.schema)
            is ExplorerEvent.GenerateInsert -> generateInsert(event.tableName, event.schema)
            is ExplorerEvent.GenerateDdl -> generateDdl(event.tableName, event.schema)
        }
    }

    private fun loadMetadata() {
        val activeConnection = connectionManager.activeConnection ?: return

        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            try {
                val connection = activeConnection.getConnection()
                val driver = activeConnection.driver

                val schemas = driver.getSchemas(connection)
                val defaultSchema = driver.getDefaultSchema(connection)

                // Load tables and views for default schema
                val tables = driver.getTables(connection, defaultSchema)
                val views = driver.getViews(connection, defaultSchema)

                updateState {
                    copy(
                        schemas = schemas,
                        tables = tables,
                        views = views,
                        isLoading = false,
                        expandedNodes =
                            if (schemas.isNotEmpty()) {
                                setOf("schema::${defaultSchema ?: schemas.first().name}")
                            } else {
                                emptySet()
                            },
                    )
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load metadata" }
                updateState {
                    copy(isLoading = false, error = e.message ?: "Failed to load metadata")
                }
            }
        }
    }

    private fun toggleNode(nodeId: String) {
        updateState {
            copy(
                expandedNodes =
                    if (expandedNodes.contains(nodeId)) {
                        expandedNodes - nodeId
                    } else {
                        expandedNodes + nodeId
                    },
            )
        }
    }

    private fun selectNode(node: TreeNode) {
        updateState { copy(selectedNode = node) }

        when (node) {
            is TreeNode.TableNode -> loadTableDetails(node.table.name, node.table.schema)
            else -> {}
        }
    }

    private fun loadTableDetails(
        tableName: String,
        schema: String?,
    ) {
        val activeConnection = connectionManager.activeConnection ?: return

        viewModelScope.launch {
            try {
                val connection = activeConnection.getConnection()
                val driver = activeConnection.driver

                val columns = driver.getColumns(connection, tableName, schema)
                val pk = driver.getPrimaryKey(connection, tableName, schema)
                val fks = driver.getForeignKeys(connection, tableName, schema)
                val indexes = driver.getIndexes(connection, tableName, schema)

                val tableInfo =
                    currentState.tables.find { it.name == tableName && it.schema == schema }
                        ?: return@launch

                updateState {
                    copy(
                        tableDetails =
                            TableDetails(
                                table = tableInfo.copy(columns = columns),
                                columns = columns,
                                primaryKey = pk,
                                foreignKeys = fks,
                                indexes = indexes,
                            ),
                    )
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load table details" }
                sendEffect(ExplorerEffect.ShowError(e.message ?: "Failed to load table details"))
            }
        }
    }

    private fun copyToClipboard(text: String) {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(text), null)
            sendEffect(ExplorerEffect.CopiedToClipboard(text))
        } catch (e: Exception) {
            logger.error(e) { "Failed to copy to clipboard" }
        }
    }

    private fun generateSelect(
        tableName: String,
        schema: String?,
    ) {
        val activeConnection = connectionManager.activeConnection ?: return

        viewModelScope.launch {
            try {
                val connection = activeConnection.getConnection()
                val driver = activeConnection.driver
                val sql = driver.generateSelectStatement(connection, tableName, schema)
                sendEffect(ExplorerEffect.InsertIntoEditor(sql))
            } catch (e: Exception) {
                logger.error(e) { "Failed to generate SELECT" }
                sendEffect(ExplorerEffect.ShowError(e.message ?: "Failed to generate SELECT"))
            }
        }
    }

    private fun generateInsert(
        tableName: String,
        schema: String?,
    ) {
        val activeConnection = connectionManager.activeConnection ?: return

        viewModelScope.launch {
            try {
                val connection = activeConnection.getConnection()
                val driver = activeConnection.driver
                val sql = driver.generateInsertStatement(connection, tableName, schema)
                sendEffect(ExplorerEffect.InsertIntoEditor(sql))
            } catch (e: Exception) {
                logger.error(e) { "Failed to generate INSERT" }
                sendEffect(ExplorerEffect.ShowError(e.message ?: "Failed to generate INSERT"))
            }
        }
    }

    private fun generateDdl(
        tableName: String,
        schema: String?,
    ) {
        val activeConnection = connectionManager.activeConnection ?: return

        viewModelScope.launch {
            try {
                val connection = activeConnection.getConnection()
                val driver = activeConnection.driver
                val sql = driver.generateCreateTableDdl(connection, tableName, schema)
                sendEffect(ExplorerEffect.InsertIntoEditor(sql))
            } catch (e: Exception) {
                logger.error(e) { "Failed to generate DDL" }
                sendEffect(ExplorerEffect.ShowError(e.message ?: "Failed to generate DDL"))
            }
        }
    }
}
