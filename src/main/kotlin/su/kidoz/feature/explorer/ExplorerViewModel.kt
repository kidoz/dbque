package su.kidoz.feature.explorer

import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import su.kidoz.core.model.DatabaseType
import su.kidoz.database.ConnectionManager
import su.kidoz.database.driver.ElasticsearchDriver
import su.kidoz.database.driver.MongoDbDriver
import su.kidoz.mvi.MviViewModel
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Suppress("LargeClass") // ViewModel consolidates all explorer operations
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
            // Schema navigation
            is ExplorerEvent.LoadSchemaContents -> loadSchemaContents(event.schemaName)
            is ExplorerEvent.ExpandSchema -> expandSchema(event.schemaName)
            is ExplorerEvent.CollapseSchema -> collapseSchema(event.schemaName)
            // Elasticsearch index management
            is ExplorerEvent.ShowCreateIndexDialog -> showCreateIndexDialog()
            is ExplorerEvent.ShowEditIndexSettingsDialog -> showEditIndexSettingsDialog(event.indexName)
            is ExplorerEvent.ShowEditIndexMappingsDialog -> showEditIndexMappingsDialog(event.indexName)
            is ExplorerEvent.HideIndexDialog -> hideIndexDialog()
            is ExplorerEvent.UpdateIndexName -> updateIndexName(event.name)
            is ExplorerEvent.UpdateIndexDefinition -> updateIndexDefinition(event.json)
            is ExplorerEvent.SaveIndex -> saveIndex()
            is ExplorerEvent.ConfirmDeleteIndex -> confirmDeleteIndex(event.indexName)
            is ExplorerEvent.DeleteIndex -> deleteIndex(event.indexName)
            is ExplorerEvent.CancelDelete -> cancelDelete()
        }
    }

    private fun loadMetadata() {
        val activeConnection = connectionManager.activeConnection ?: return
        val dbType = activeConnection.config.type

        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null, databaseType = dbType) }
            try {
                when (dbType) {
                    DatabaseType.MONGODB -> loadMongoMetadata(activeConnection)
                    DatabaseType.ELASTICSEARCH -> loadElasticsearchMetadata(activeConnection)
                    else -> loadJdbcMetadata(activeConnection)
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load metadata" }
                updateState {
                    copy(isLoading = false, error = e.message ?: "Failed to load metadata")
                }
            }
        }
    }

    private suspend fun loadJdbcMetadata(activeConnection: su.kidoz.database.ActiveConnection) {
        val driver = activeConnection.driver

        activeConnection.getConnection().use { connection ->
            val schemas = driver.getSchemas(connection)
            val defaultSchema = driver.getDefaultSchema(connection)

            // For databases with schemas, set up the schema list and load default schema
            if (schemas.isNotEmpty()) {
                // Load tables and views for default schema
                val schemaToLoad = defaultSchema ?: schemas.first().name
                val tables = driver.getTables(connection, schemaToLoad)
                val views = driver.getViews(connection, schemaToLoad)

                updateState {
                    copy(
                        schemas = schemas,
                        tables = tables, // Keep for backward compatibility
                        views = views,
                        tablesBySchema = mapOf(schemaToLoad to tables),
                        viewsBySchema = mapOf(schemaToLoad to views),
                        loadedSchemas = setOf(schemaToLoad),
                        defaultSchema = schemaToLoad,
                        isLoading = false,
                        expandedNodes = setOf("schema::$schemaToLoad"),
                    )
                }
            } else {
                // No schemas (e.g., SQLite) - load all tables directly
                val tables = driver.getTables(connection, null)
                val views = driver.getViews(connection, null)

                updateState {
                    copy(
                        schemas = emptyList(),
                        tables = tables,
                        views = views,
                        tablesBySchema = emptyMap(),
                        viewsBySchema = emptyMap(),
                        loadedSchemas = emptySet(),
                        defaultSchema = null,
                        isLoading = false,
                        expandedNodes = emptySet(),
                    )
                }
            }
        }
    }

    private suspend fun loadMongoMetadata(activeConnection: su.kidoz.database.ActiveConnection) {
        val mongoConnection = activeConnection.getMongoConnection()
        val driver = activeConnection.driver as MongoDbDriver

        val collections = driver.getCollections(mongoConnection)

        updateState {
            copy(
                schemas = emptyList(),
                tables = collections,
                views = emptyList(),
                isLoading = false,
                expandedNodes = emptySet(),
            )
        }
    }

    private suspend fun loadElasticsearchMetadata(activeConnection: su.kidoz.database.ActiveConnection) {
        val esConnection = activeConnection.getElasticsearchConnection()
        val driver = activeConnection.driver as ElasticsearchDriver

        val indices = driver.getIndicesAsTableInfo(esConnection)

        updateState {
            copy(
                schemas = emptyList(),
                tables = indices,
                views = emptyList(),
                isLoading = false,
                expandedNodes = emptySet(),
            )
        }
    }

    private fun toggleNode(nodeId: String) {
        val isExpanding = !currentState.expandedNodes.contains(nodeId)

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

        // Load table details when expanding a table node
        if (isExpanding && nodeId.startsWith("table:")) {
            val parts = nodeId.removePrefix("table:").split(":", limit = 2)
            val schema = parts.getOrNull(0)?.takeIf { it.isNotEmpty() }
            val tableName = parts.getOrNull(1) ?: return
            loadTableDetails(tableName, schema)
        }
    }

    private fun expandSchema(schemaName: String) {
        val nodeId = "schema::$schemaName"

        // Add to expanded nodes
        updateState {
            copy(expandedNodes = expandedNodes + nodeId)
        }

        // Load contents if not already loaded
        if (!currentState.isSchemaLoaded(schemaName)) {
            loadSchemaContents(schemaName)
        }
    }

    private fun collapseSchema(schemaName: String) {
        val nodeId = "schema::$schemaName"
        updateState {
            copy(expandedNodes = expandedNodes - nodeId)
        }
    }

    private fun loadSchemaContents(schemaName: String) {
        val activeConnection = connectionManager.activeConnection ?: return

        // Don't reload if already loaded or currently loading
        if (currentState.isSchemaLoaded(schemaName) || currentState.isSchemaLoading(schemaName)) {
            return
        }

        viewModelScope.launch {
            updateState {
                copy(loadingSchemas = loadingSchemas + schemaName)
            }

            try {
                val driver = activeConnection.driver

                activeConnection.getConnection().use { connection ->
                    val tables = driver.getTables(connection, schemaName)
                    val views = driver.getViews(connection, schemaName)

                    updateState {
                        copy(
                            tablesBySchema = tablesBySchema + (schemaName to tables),
                            viewsBySchema = viewsBySchema + (schemaName to views),
                            loadedSchemas = loadedSchemas + schemaName,
                            loadingSchemas = loadingSchemas - schemaName,
                            // Also update flat lists for backward compatibility
                            tables = (this.tables + tables).distinctBy { "${it.schema}:${it.name}" },
                            views = (this.views + views).distinctBy { "${it.schema}:${it.name}" },
                        )
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load schema contents for $schemaName" }
                updateState {
                    copy(loadingSchemas = loadingSchemas - schemaName)
                }
                sendEffect(ExplorerEffect.ShowError(e.message ?: "Failed to load schema"))
            }
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
                when (activeConnection.config.type) {
                    DatabaseType.MONGODB -> loadMongoTableDetails(activeConnection, tableName)
                    DatabaseType.ELASTICSEARCH -> loadElasticsearchTableDetails(activeConnection, tableName)
                    else -> loadJdbcTableDetails(activeConnection, tableName, schema)
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load table details" }
                sendEffect(ExplorerEffect.ShowError(e.message ?: "Failed to load table details"))
            }
        }
    }

    private suspend fun loadJdbcTableDetails(
        activeConnection: su.kidoz.database.ActiveConnection,
        tableName: String,
        schema: String?,
    ) {
        val driver = activeConnection.driver

        activeConnection.getConnection().use { connection ->
            val columns = driver.getColumns(connection, tableName, schema)
            val pk = driver.getPrimaryKey(connection, tableName, schema)
            val fks = driver.getForeignKeys(connection, tableName, schema)
            val indexes = driver.getIndexes(connection, tableName, schema)

            val tableInfo =
                currentState.tables.find { it.name == tableName && it.schema == schema }
                    ?: return

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
        }
    }

    private suspend fun loadMongoTableDetails(
        activeConnection: su.kidoz.database.ActiveConnection,
        collectionName: String,
    ) {
        val mongoConnection = activeConnection.getMongoConnection()
        val driver = activeConnection.driver as MongoDbDriver

        val columns = driver.getFieldsMongo(mongoConnection, collectionName)

        val tableInfo =
            currentState.tables.find { it.name == collectionName }
                ?: return

        updateState {
            copy(
                tableDetails =
                    TableDetails(
                        table = tableInfo.copy(columns = columns),
                        columns = columns,
                        primaryKey = null,
                        foreignKeys = emptyList(),
                        indexes = emptyList(),
                    ),
            )
        }
    }

    private suspend fun loadElasticsearchTableDetails(
        activeConnection: su.kidoz.database.ActiveConnection,
        indexName: String,
    ) {
        val esConnection = activeConnection.getElasticsearchConnection()
        val driver = activeConnection.driver as ElasticsearchDriver

        val columns = driver.getFieldsElasticsearch(esConnection, indexName)

        val tableInfo =
            currentState.tables.find { it.name == indexName }
                ?: return

        updateState {
            copy(
                tableDetails =
                    TableDetails(
                        table = tableInfo.copy(columns = columns),
                        columns = columns,
                        primaryKey = null,
                        foreignKeys = emptyList(),
                        indexes = emptyList(),
                    ),
            )
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
                val sql =
                    when (activeConnection.config.type) {
                        DatabaseType.MONGODB -> generateMongoSelect(tableName)
                        DatabaseType.ELASTICSEARCH -> generateElasticsearchSelect(tableName)
                        else -> {
                            activeConnection.getConnection().use { connection ->
                                activeConnection.driver.generateSelectStatement(connection, tableName, schema)
                            }
                        }
                    }
                sendEffect(ExplorerEffect.InsertIntoEditor(sql))
            } catch (e: Exception) {
                logger.error(e) { "Failed to generate SELECT" }
                sendEffect(ExplorerEffect.ShowError(e.message ?: "Failed to generate SELECT"))
            }
        }
    }

    private fun generateMongoSelect(collectionName: String): String =
        """
        db.$collectionName.find({}).limit(100)
        """.trimIndent()

    private fun generateElasticsearchSelect(indexName: String): String =
        """
        GET /$indexName/_search
        {
          "size": 100,
          "query": {
            "match_all": {}
          }
        }
        """.trimIndent()

    private fun generateInsert(
        tableName: String,
        schema: String?,
    ) {
        val activeConnection = connectionManager.activeConnection ?: return

        viewModelScope.launch {
            try {
                val sql =
                    when (activeConnection.config.type) {
                        DatabaseType.MONGODB -> generateMongoInsert(tableName)
                        DatabaseType.ELASTICSEARCH -> generateElasticsearchInsert(tableName)
                        else -> {
                            activeConnection.getConnection().use { connection ->
                                activeConnection.driver.generateInsertStatement(connection, tableName, schema)
                            }
                        }
                    }
                sendEffect(ExplorerEffect.InsertIntoEditor(sql))
            } catch (e: Exception) {
                logger.error(e) { "Failed to generate INSERT" }
                sendEffect(ExplorerEffect.ShowError(e.message ?: "Failed to generate INSERT"))
            }
        }
    }

    private fun generateMongoInsert(collectionName: String): String =
        """
        db.$collectionName.insertOne({
          // Document content here
        })
        """.trimIndent()

    private fun generateElasticsearchInsert(indexName: String): String =
        """
        POST /$indexName/_doc
        {
          // Document content here
        }
        """.trimIndent()

    private fun generateDdl(
        tableName: String,
        schema: String?,
    ) {
        val activeConnection = connectionManager.activeConnection ?: return

        viewModelScope.launch {
            try {
                val sql =
                    when (activeConnection.config.type) {
                        DatabaseType.MONGODB -> generateMongoDdl(tableName)
                        DatabaseType.ELASTICSEARCH -> {
                            val esConnection = activeConnection.getElasticsearchConnection()
                            val driver = activeConnection.driver as ElasticsearchDriver
                            driver.getIndexMappingElasticsearch(esConnection, tableName)
                        }
                        else -> {
                            activeConnection.getConnection().use { connection ->
                                activeConnection.driver.generateCreateTableDdl(connection, tableName, schema)
                            }
                        }
                    }
                sendEffect(ExplorerEffect.InsertIntoEditor(sql))
            } catch (e: Exception) {
                logger.error(e) { "Failed to generate DDL" }
                sendEffect(ExplorerEffect.ShowError(e.message ?: "Failed to generate DDL"))
            }
        }
    }

    private fun generateMongoDdl(collectionName: String): String =
        """
        // MongoDB collections are schemaless
        // Create collection:
        db.createCollection("$collectionName")

        // Optional: Add validation rules
        db.runCommand({
          collMod: "$collectionName",
          validator: {
            ${"$"}jsonSchema: {
              bsonType: "object",
              required: [],
              properties: {
                // Define your fields here
              }
            }
          }
        })
        """.trimIndent()

    // ==================== Elasticsearch Index Management ====================

    private fun showCreateIndexDialog() {
        updateState {
            copy(
                indexDialogState =
                    ElasticsearchIndexDialogState(
                        mode = IndexDialogMode.CREATE,
                        indexName = "",
                        definitionJson = ElasticsearchIndexDialogState.DEFAULT_INDEX_TEMPLATE,
                    ),
            )
        }
    }

    private fun showEditIndexSettingsDialog(indexName: String) {
        val activeConnection = connectionManager.activeConnection ?: return

        viewModelScope.launch {
            try {
                updateState {
                    copy(
                        indexDialogState =
                            ElasticsearchIndexDialogState(
                                mode = IndexDialogMode.EDIT_SETTINGS,
                                indexName = indexName,
                                definitionJson = "Loading...",
                                isProcessing = true,
                            ),
                    )
                }

                val esConnection = activeConnection.getElasticsearchConnection()
                val driver = activeConnection.driver as ElasticsearchDriver

                val settingsResult = driver.getIndexSettings(esConnection, indexName)
                settingsResult.fold(
                    onSuccess = { settings ->
                        updateState {
                            copy(
                                indexDialogState =
                                    indexDialogState?.copy(
                                        definitionJson = settings,
                                        isProcessing = false,
                                    ),
                            )
                        }
                    },
                    onFailure = { e ->
                        updateState {
                            copy(
                                indexDialogState =
                                    indexDialogState?.copy(
                                        error = e.message ?: "Failed to load settings",
                                        isProcessing = false,
                                    ),
                            )
                        }
                    },
                )
            } catch (e: Exception) {
                logger.error(e) { "Failed to load index settings for $indexName" }
                updateState {
                    copy(
                        indexDialogState =
                            indexDialogState?.copy(
                                error = e.message ?: "Failed to load settings",
                                isProcessing = false,
                            ),
                    )
                }
            }
        }
    }

    private fun showEditIndexMappingsDialog(indexName: String) {
        val activeConnection = connectionManager.activeConnection ?: return

        viewModelScope.launch {
            try {
                updateState {
                    copy(
                        indexDialogState =
                            ElasticsearchIndexDialogState(
                                mode = IndexDialogMode.EDIT_MAPPINGS,
                                indexName = indexName,
                                definitionJson = "Loading...",
                                isProcessing = true,
                            ),
                    )
                }

                val esConnection = activeConnection.getElasticsearchConnection()
                val driver = activeConnection.driver as ElasticsearchDriver

                val mappingsResult = driver.getIndexMappings(esConnection, indexName)
                mappingsResult.fold(
                    onSuccess = { mappings ->
                        updateState {
                            copy(
                                indexDialogState =
                                    indexDialogState?.copy(
                                        definitionJson = mappings,
                                        isProcessing = false,
                                    ),
                            )
                        }
                    },
                    onFailure = { e ->
                        updateState {
                            copy(
                                indexDialogState =
                                    indexDialogState?.copy(
                                        error = e.message ?: "Failed to load mappings",
                                        isProcessing = false,
                                    ),
                            )
                        }
                    },
                )
            } catch (e: Exception) {
                logger.error(e) { "Failed to load index mappings for $indexName" }
                updateState {
                    copy(
                        indexDialogState =
                            indexDialogState?.copy(
                                error = e.message ?: "Failed to load mappings",
                                isProcessing = false,
                            ),
                    )
                }
            }
        }
    }

    private fun hideIndexDialog() {
        updateState { copy(indexDialogState = null) }
    }

    private fun updateIndexName(name: String) {
        updateState {
            copy(
                indexDialogState =
                    indexDialogState?.copy(
                        indexName = name,
                        error = null,
                    ),
            )
        }
    }

    private fun updateIndexDefinition(json: String) {
        updateState {
            copy(
                indexDialogState =
                    indexDialogState?.copy(
                        definitionJson = json,
                        error = null,
                    ),
            )
        }
    }

    private fun saveIndex() {
        val dialogState = currentState.indexDialogState ?: return
        val activeConnection = connectionManager.activeConnection ?: return

        if (!dialogState.isValid) {
            updateState {
                copy(
                    indexDialogState =
                        indexDialogState?.copy(
                            error = "Invalid index name or definition",
                        ),
                )
            }
            return
        }

        viewModelScope.launch {
            try {
                updateState {
                    copy(indexDialogState = indexDialogState?.copy(isProcessing = true, error = null))
                }

                val esConnection = activeConnection.getElasticsearchConnection()
                val driver = activeConnection.driver as ElasticsearchDriver

                val result =
                    when (dialogState.mode) {
                        IndexDialogMode.CREATE -> {
                            driver.createIndex(esConnection, dialogState.indexName, dialogState.definitionJson)
                        }
                        IndexDialogMode.EDIT_SETTINGS -> {
                            driver.updateIndexSettings(
                                esConnection,
                                dialogState.indexName,
                                dialogState.definitionJson,
                            )
                        }
                        IndexDialogMode.EDIT_MAPPINGS -> {
                            driver.updateIndexMappings(
                                esConnection,
                                dialogState.indexName,
                                dialogState.definitionJson,
                            )
                        }
                    }

                result.fold(
                    onSuccess = {
                        val effect =
                            when (dialogState.mode) {
                                IndexDialogMode.CREATE -> ExplorerEffect.IndexCreated(dialogState.indexName)
                                else -> ExplorerEffect.IndexUpdated(dialogState.indexName)
                            }
                        sendEffect(effect)
                        hideIndexDialog()
                        loadMetadata() // Refresh the tree
                    },
                    onFailure = { e ->
                        updateState {
                            copy(
                                indexDialogState =
                                    indexDialogState?.copy(
                                        error = e.message ?: "Operation failed",
                                        isProcessing = false,
                                    ),
                            )
                        }
                    },
                )
            } catch (e: Exception) {
                logger.error(e) { "Failed to save index" }
                updateState {
                    copy(
                        indexDialogState =
                            indexDialogState?.copy(
                                error = e.message ?: "Operation failed",
                                isProcessing = false,
                            ),
                    )
                }
            }
        }
    }

    private fun confirmDeleteIndex(indexName: String) {
        updateState { copy(deleteConfirmation = DeleteConfirmationState(indexName)) }
    }

    private fun deleteIndex(indexName: String) {
        val activeConnection = connectionManager.activeConnection ?: return

        viewModelScope.launch {
            try {
                val esConnection = activeConnection.getElasticsearchConnection()
                val driver = activeConnection.driver as ElasticsearchDriver

                val result = driver.deleteIndex(esConnection, indexName)
                result.fold(
                    onSuccess = {
                        sendEffect(ExplorerEffect.IndexDeleted(indexName))
                        cancelDelete()
                        loadMetadata() // Refresh the tree
                    },
                    onFailure = { e ->
                        sendEffect(ExplorerEffect.ShowError(e.message ?: "Failed to delete index"))
                        cancelDelete()
                    },
                )
            } catch (e: Exception) {
                logger.error(e) { "Failed to delete index $indexName" }
                sendEffect(ExplorerEffect.ShowError(e.message ?: "Failed to delete index"))
                cancelDelete()
            }
        }
    }

    private fun cancelDelete() {
        updateState { copy(deleteConfirmation = null) }
    }
}
