package su.kidoz.feature.explorer

import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import su.kidoz.core.model.DatabaseType
import su.kidoz.database.ConnectionManager
import su.kidoz.database.driver.ElasticsearchDriver
import su.kidoz.database.driver.MongoDbDriver
import su.kidoz.database.driver.StarRocksDriver
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

            is ExplorerEvent.Refresh -> {
                loadMetadata()
            }

            is ExplorerEvent.ToggleNode -> {
                toggleNode(event.nodeId)
            }

            is ExplorerEvent.SelectNode -> {
                selectNode(event.node)
            }

            is ExplorerEvent.LoadTableDetails -> {
                loadTableDetails(event.tableName, event.schema)
            }

            is ExplorerEvent.CopyName -> {
                copyToClipboard(event.name)
            }

            is ExplorerEvent.GenerateSelect -> {
                generateSelect(event.tableName, event.schema)
            }

            is ExplorerEvent.GenerateInsert -> {
                generateInsert(event.tableName, event.schema)
            }

            is ExplorerEvent.GenerateDdl -> {
                generateDdl(event.tableName, event.schema)
            }

            // Schema navigation
            is ExplorerEvent.LoadSchemaContents -> {
                loadSchemaContents(event.schemaName)
            }

            is ExplorerEvent.ExpandSchema -> {
                expandSchema(event.schemaName)
            }

            is ExplorerEvent.CollapseSchema -> {
                collapseSchema(event.schemaName)
            }

            // StarRocks catalog navigation
            is ExplorerEvent.ExpandCatalog -> {
                expandCatalog(event.catalogName)
            }

            is ExplorerEvent.CollapseCatalog -> {
                collapseCatalog(event.catalogName)
            }

            // MongoDB database navigation
            is ExplorerEvent.ExpandDatabase -> {
                expandDatabase(event.databaseName)
            }

            is ExplorerEvent.CollapseDatabase -> {
                collapseDatabase(event.databaseName)
            }

            is ExplorerEvent.LoadDatabaseCollections -> {
                loadDatabaseCollections(event.databaseName)
            }

            // Elasticsearch index navigation
            is ExplorerEvent.ExpandIndex -> {
                expandIndex(event.indexName)
            }

            is ExplorerEvent.CollapseIndex -> {
                collapseIndex(event.indexName)
            }

            is ExplorerEvent.LoadIndexFields -> {
                loadIndexFields(event.indexName)
            }

            // Elasticsearch index management
            is ExplorerEvent.ShowCreateIndexDialog -> {
                showCreateIndexDialog()
            }

            is ExplorerEvent.ShowEditIndexSettingsDialog -> {
                showEditIndexSettingsDialog(event.indexName)
            }

            is ExplorerEvent.ShowEditIndexMappingsDialog -> {
                showEditIndexMappingsDialog(event.indexName)
            }

            is ExplorerEvent.HideIndexDialog -> {
                hideIndexDialog()
            }

            is ExplorerEvent.UpdateIndexName -> {
                updateIndexName(event.name)
            }

            is ExplorerEvent.UpdateIndexDefinition -> {
                updateIndexDefinition(event.json)
            }

            is ExplorerEvent.SaveIndex -> {
                saveIndex()
            }

            is ExplorerEvent.ConfirmDeleteIndex -> {
                confirmDeleteIndex(event.indexName)
            }

            is ExplorerEvent.DeleteIndex -> {
                deleteIndex(event.indexName)
            }

            is ExplorerEvent.CancelDelete -> {
                cancelDelete()
            }
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
                    DatabaseType.STARROCKS -> loadStarRocksMetadata(activeConnection)
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

    private suspend fun loadStarRocksMetadata(activeConnection: su.kidoz.database.ActiveConnection) {
        val driver = activeConnection.driver

        activeConnection.getConnection().use { connection ->
            val catalogs = driver.getCatalogs(connection)
            val internal = catalogs.firstOrNull { it.isInternal }

            updateState {
                copy(
                    catalogs = catalogs,
                    isLoading = false,
                    // Auto-expand the internal catalog so the user's databases are one click away.
                    expandedNodes = internal?.let { setOf("catalog:${it.name}") } ?: emptySet(),
                )
            }

            // Eagerly load the internal catalog's databases; external catalogs load on expand.
            internal?.let { loadCatalogDatabases(it.name) }
        }
    }

    private fun expandCatalog(catalogName: String) {
        updateState { copy(expandedNodes = expandedNodes + "catalog:$catalogName") }
        if (!currentState.isCatalogLoaded(catalogName)) {
            loadCatalogDatabases(catalogName)
        }
    }

    private fun collapseCatalog(catalogName: String) {
        updateState { copy(expandedNodes = expandedNodes - "catalog:$catalogName") }
    }

    private fun loadCatalogDatabases(catalogName: String) {
        val activeConnection = connectionManager.activeConnection ?: return
        val driver = activeConnection.driver as? StarRocksDriver ?: return
        val catalog = currentState.catalogs.firstOrNull { it.name == catalogName } ?: return

        if (currentState.isCatalogLoaded(catalogName) || currentState.isCatalogLoading(catalogName)) {
            return
        }

        viewModelScope.launch {
            updateState { copy(loadingCatalogs = loadingCatalogs + catalogName) }
            try {
                activeConnection.getConnection().use { connection ->
                    val databases = driver.getDatabasesInCatalog(connection, catalog)
                    updateState {
                        copy(
                            databasesByCatalog = databasesByCatalog + (catalogName to databases),
                            loadedCatalogs = loadedCatalogs + catalogName,
                            loadingCatalogs = loadingCatalogs - catalogName,
                        )
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load databases for catalog $catalogName" }
                updateState { copy(loadingCatalogs = loadingCatalogs - catalogName) }
                sendEffect(ExplorerEffect.ShowError(e.message ?: "Failed to load catalog"))
            }
        }
    }

    private suspend fun loadMongoMetadata(activeConnection: su.kidoz.database.ActiveConnection) {
        val mongoConnection = activeConnection.getMongoConnection()
        val driver = activeConnection.driver as MongoDbDriver

        // Load databases for MongoDB hierarchy
        val databases = driver.getDatabasesMongo(mongoConnection)

        updateState {
            copy(
                databases = databases,
                schemas = emptyList(),
                tables = emptyList(),
                views = emptyList(),
                collectionsByDatabase = emptyMap(),
                loadedDatabases = emptySet(),
                loadingDatabases = emptySet(),
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
                fieldsByIndex = emptyMap(),
                loadedIndices = emptySet(),
                loadingIndices = emptySet(),
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

        // Load collection details when expanding a MongoDB collection node
        if (isExpanding && nodeId.startsWith("collection:")) {
            val parts = nodeId.removePrefix("collection:").split(":", limit = 2)
            val databaseName = parts.getOrNull(0) ?: return
            val collectionName = parts.getOrNull(1) ?: return
            loadMongoCollectionDetails(collectionName, databaseName)
        }

        // Load field mappings when expanding an Elasticsearch index node
        if (isExpanding && nodeId.startsWith("esindex:")) {
            val indexName = nodeId.removePrefix("esindex:")
            if (!currentState.isIndexLoaded(indexName)) {
                loadIndexFields(indexName)
            }
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

    // ==================== MongoDB Database Navigation ====================

    private fun expandDatabase(databaseName: String) {
        val nodeId = "db:$databaseName"

        // Add to expanded nodes
        updateState {
            copy(expandedNodes = expandedNodes + nodeId)
        }

        // Load collections if not already loaded
        if (!currentState.isDatabaseLoaded(databaseName)) {
            loadDatabaseCollections(databaseName)
        }
    }

    private fun collapseDatabase(databaseName: String) {
        val nodeId = "db:$databaseName"
        updateState {
            copy(expandedNodes = expandedNodes - nodeId)
        }
    }

    private fun loadDatabaseCollections(databaseName: String) {
        val activeConnection = connectionManager.activeConnection ?: return

        // Don't reload if already loaded or currently loading
        if (currentState.isDatabaseLoaded(databaseName) || currentState.isDatabaseLoading(databaseName)) {
            return
        }

        viewModelScope.launch {
            updateState {
                copy(loadingDatabases = loadingDatabases + databaseName)
            }

            try {
                val mongoConnection = activeConnection.getMongoConnection()
                val driver = activeConnection.driver as MongoDbDriver

                val collections = driver.getCollections(mongoConnection, databaseName)

                updateState {
                    copy(
                        collectionsByDatabase = collectionsByDatabase + (databaseName to collections),
                        loadedDatabases = loadedDatabases + databaseName,
                        loadingDatabases = loadingDatabases - databaseName,
                    )
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load collections for database $databaseName" }
                updateState {
                    copy(loadingDatabases = loadingDatabases - databaseName)
                }
                sendEffect(ExplorerEffect.ShowError(e.message ?: "Failed to load collections"))
            }
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
                    // StarRocks materialized views are first-class objects shown in their own folder.
                    val materializedViews =
                        (driver as? StarRocksDriver)?.getMaterializedViews(connection, schemaName) ?: emptyList()

                    updateState {
                        copy(
                            tablesBySchema = tablesBySchema + (schemaName to tables),
                            viewsBySchema = viewsBySchema + (schemaName to views),
                            materializedViewsBySchema = materializedViewsBySchema + (schemaName to materializedViews),
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
            is TreeNode.TableNode -> {
                loadTableDetails(node.table.name, node.table.schema)
            }

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
        val indexes = driver.getIndexesMongo(mongoConnection, collectionName)

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
                        indexes = indexes,
                    ),
            )
        }
    }

    private fun loadMongoCollectionDetails(
        collectionName: String,
        databaseName: String,
    ) {
        val activeConnection = connectionManager.activeConnection ?: return

        viewModelScope.launch {
            try {
                val mongoConnection = activeConnection.getMongoConnection()
                val driver = activeConnection.driver as MongoDbDriver

                val columns = driver.getFieldsMongo(mongoConnection, collectionName, databaseName)
                val indexes = driver.getIndexesMongo(mongoConnection, collectionName, databaseName)

                val collections = currentState.collectionsByDatabase[databaseName] ?: emptyList()
                val tableInfo = collections.find { it.name == collectionName } ?: return@launch

                updateState {
                    copy(
                        tableDetails =
                            TableDetails(
                                table = tableInfo.copy(columns = columns),
                                columns = columns,
                                primaryKey = null,
                                foreignKeys = emptyList(),
                                indexes = indexes,
                            ),
                    )
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load collection details for $collectionName" }
                sendEffect(ExplorerEffect.ShowError(e.message ?: "Failed to load collection details"))
            }
        }
    }

    // ==================== Elasticsearch Index Navigation ====================

    private fun expandIndex(indexName: String) {
        val nodeId = "esindex:$indexName"

        // Add to expanded nodes
        updateState {
            copy(expandedNodes = expandedNodes + nodeId)
        }

        // Load fields if not already loaded
        if (!currentState.isIndexLoaded(indexName)) {
            loadIndexFields(indexName)
        }
    }

    private fun collapseIndex(indexName: String) {
        val nodeId = "esindex:$indexName"
        updateState {
            copy(expandedNodes = expandedNodes - nodeId)
        }
    }

    private fun loadIndexFields(indexName: String) {
        val activeConnection = connectionManager.activeConnection ?: return

        // Don't reload if already loaded or currently loading
        if (currentState.isIndexLoaded(indexName) || currentState.isIndexLoading(indexName)) {
            return
        }

        viewModelScope.launch {
            updateState {
                copy(loadingIndices = loadingIndices + indexName)
            }

            try {
                val esConnection = activeConnection.getElasticsearchConnection()
                val driver = activeConnection.driver as ElasticsearchDriver

                // Load fields with limit to handle large mappings
                val fields = driver.getFieldsElasticsearch(esConnection, indexName, currentState.indexFieldLimit)

                updateState {
                    copy(
                        fieldsByIndex = fieldsByIndex + (indexName to fields),
                        loadedIndices = loadedIndices + indexName,
                        loadingIndices = loadingIndices - indexName,
                    )
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load fields for index $indexName" }
                updateState {
                    copy(loadingIndices = loadingIndices - indexName)
                }
                sendEffect(ExplorerEffect.ShowError(e.message ?: "Failed to load index fields"))
            }
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
                        DatabaseType.MONGODB -> {
                            generateMongoSelect(tableName)
                        }

                        DatabaseType.ELASTICSEARCH -> {
                            generateElasticsearchSelect(tableName)
                        }

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
                        DatabaseType.MONGODB -> {
                            generateMongoInsert(tableName)
                        }

                        DatabaseType.ELASTICSEARCH -> {
                            generateElasticsearchInsert(tableName)
                        }

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
                        DatabaseType.MONGODB -> {
                            generateMongoDdl(tableName)
                        }

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
