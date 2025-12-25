package su.kidoz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.compose.koinInject
import su.kidoz.database.ConnectionManager
import su.kidoz.feature.connection.ConnectionEffect
import su.kidoz.feature.connection.ConnectionViewModel
import su.kidoz.feature.connection.ui.ConnectionDialog
import su.kidoz.feature.connection.ui.ConnectionList
import su.kidoz.feature.editor.EditorEffect
import su.kidoz.feature.editor.EditorEvent
import su.kidoz.feature.editor.EditorViewModel
import su.kidoz.feature.editor.ui.EditorTabs
import su.kidoz.feature.editor.ui.SqlEditor
import su.kidoz.feature.explorer.ExplorerEffect
import su.kidoz.feature.explorer.ExplorerViewModel
import su.kidoz.feature.explorer.ui.DatabaseTree
import su.kidoz.feature.history.HistoryEffect
import su.kidoz.feature.history.HistoryViewModel
import su.kidoz.feature.history.ui.HistoryPanel
import su.kidoz.feature.queryplan.QueryPlanEffect
import su.kidoz.feature.queryplan.QueryPlanViewModel
import su.kidoz.feature.queryplan.ui.QueryPlanPanel
import su.kidoz.feature.results.ResultsEvent
import su.kidoz.feature.results.ResultsViewModel
import su.kidoz.feature.results.ui.ResultsPanel
import su.kidoz.feature.savedqueries.SavedQueryEffect
import su.kidoz.feature.savedqueries.SavedQueryEvent
import su.kidoz.feature.savedqueries.SavedQueryViewModel
import su.kidoz.feature.savedqueries.ui.SavedQueriesPanel
import su.kidoz.ui.components.HorizontalSplitPane
import su.kidoz.ui.components.MainToolbar
import su.kidoz.ui.components.StatusBar
import su.kidoz.ui.components.VerticalSplitPane

private enum class ResultsTab(
    val title: String,
) {
    Results("Results"),
    QueryPlan("Query Plan"),
}

@Composable
fun MainWindow() {
    val connectionViewModel: ConnectionViewModel = koinInject()
    val explorerViewModel: ExplorerViewModel = koinInject()
    val editorViewModel: EditorViewModel = koinInject()
    val resultsViewModel: ResultsViewModel = koinInject()
    val historyViewModel: HistoryViewModel = koinInject()
    val queryPlanViewModel: QueryPlanViewModel = koinInject()
    val savedQueryViewModel: SavedQueryViewModel = koinInject()
    val connectionManager: ConnectionManager = koinInject()

    val connectionState by connectionViewModel.state.collectAsState()
    val explorerState by explorerViewModel.state.collectAsState()
    val editorState by editorViewModel.state.collectAsState()
    val resultsState by resultsViewModel.state.collectAsState()
    val historyState by historyViewModel.state.collectAsState()
    val queryPlanState by queryPlanViewModel.state.collectAsState()
    val savedQueryState by savedQueryViewModel.state.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var activeResultsTab by remember { mutableStateOf(ResultsTab.Results) }

    // Handle effects
    LaunchedEffect(Unit) {
        connectionViewModel.effect
            .onEach { effect ->
                when (effect) {
                    is ConnectionEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                    is ConnectionEffect.ShowSuccess -> snackbarHostState.showSnackbar(effect.message)
                    is ConnectionEffect.ConnectionEstablished -> {}
                    is ConnectionEffect.ConnectionClosed -> {}
                }
            }.launchIn(this)

        editorViewModel.effect
            .onEach { effect ->
                when (effect) {
                    is EditorEffect.QueryExecuted -> {
                        resultsViewModel.onEvent(ResultsEvent.SetResults(effect.results))
                    }
                    is EditorEffect.QueryError -> {
                        snackbarHostState.showSnackbar(effect.message)
                    }
                    is EditorEffect.ShowMessage -> {
                        snackbarHostState.showSnackbar(effect.message)
                    }
                }
            }.launchIn(this)

        explorerViewModel.effect
            .onEach { effect ->
                when (effect) {
                    is ExplorerEffect.InsertIntoEditor -> {
                        editorViewModel.insertText(effect.sql)
                    }
                    is ExplorerEffect.CopiedToClipboard -> {
                        snackbarHostState.showSnackbar("Copied to clipboard")
                    }
                    is ExplorerEffect.ShowError -> {
                        snackbarHostState.showSnackbar(effect.message)
                    }
                }
            }.launchIn(this)

        historyViewModel.effect
            .onEach { effect ->
                when (effect) {
                    is HistoryEffect.InsertQuery -> {
                        editorViewModel.insertText(effect.query)
                    }
                    is HistoryEffect.CopiedToClipboard -> {
                        snackbarHostState.showSnackbar("Copied to clipboard")
                    }
                    is HistoryEffect.ShowError -> {
                        snackbarHostState.showSnackbar(effect.message)
                    }
                    is HistoryEffect.ShowMessage -> {
                        snackbarHostState.showSnackbar(effect.message)
                    }
                }
            }.launchIn(this)

        queryPlanViewModel.effect
            .onEach { effect ->
                when (effect) {
                    is QueryPlanEffect.ShowError -> {
                        snackbarHostState.showSnackbar(effect.message)
                    }
                }
            }.launchIn(this)

        savedQueryViewModel.effect
            .onEach { effect ->
                when (effect) {
                    is SavedQueryEffect.InsertQuery -> {
                        editorViewModel.insertText(effect.query)
                    }
                    is SavedQueryEffect.CopiedToClipboard -> {
                        snackbarHostState.showSnackbar("Copied to clipboard")
                    }
                    is SavedQueryEffect.ShowError -> {
                        snackbarHostState.showSnackbar(effect.message)
                    }
                    is SavedQueryEffect.ShowMessage -> {
                        snackbarHostState.showSnackbar(effect.message)
                    }
                }
            }.launchIn(this)
    }

    val activeConnection = connectionManager.activeConnection

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        ) {
            // Toolbar
            MainToolbar(
                connectionName = activeConnection?.config?.name,
                isExecuting = editorState.isExecuting,
                onExecute = { editorViewModel.onEvent(EditorEvent.ExecuteQuery) },
                onCancel = { editorViewModel.onEvent(EditorEvent.CancelExecution) },
                onNewTab = { editorViewModel.onEvent(EditorEvent.NewTab) },
            )

            // Main content
            HorizontalSplitPane(
                modifier = Modifier.weight(1f),
                splitFraction = 0.2f,
                firstPane = {
                    // Left sidebar - Connections, Explorer, and Saved Queries
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Connections
                        ConnectionList(
                            state = connectionState,
                            onEvent = connectionViewModel::onEvent,
                            modifier = Modifier.weight(0.3f),
                        )

                        // Database Explorer
                        DatabaseTree(
                            state = explorerState,
                            onEvent = explorerViewModel::onEvent,
                            modifier = Modifier.weight(0.4f),
                        )

                        // Saved Queries
                        SavedQueriesPanel(
                            state = savedQueryState,
                            onEvent = savedQueryViewModel::onEvent,
                            modifier = Modifier.weight(0.3f),
                        )
                    }
                },
                secondPane = {
                    HorizontalSplitPane(
                        splitFraction = 0.8f,
                        firstPane = {
                            // Center - Editor and Results
                            VerticalSplitPane(
                                splitFraction = 0.5f,
                                firstPane = {
                                    // Editor
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        EditorTabs(
                                            state = editorState,
                                            onEvent = editorViewModel::onEvent,
                                            onSaveQuery = { query ->
                                                savedQueryViewModel.onEvent(
                                                    SavedQueryEvent.ShowSaveCurrentQueryDialog(query),
                                                )
                                            },
                                        )
                                        editorState.activeTab?.let { tab ->
                                            SqlEditor(
                                                tab = tab,
                                                onEvent = editorViewModel::onEvent,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                },
                                secondPane = {
                                    // Results area with tabs
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        // Result tabs (Results / Query Plan)
                                        ResultsAreaTabs(
                                            activeTab = activeResultsTab,
                                            onTabSelected = { activeResultsTab = it },
                                        )

                                        when (activeResultsTab) {
                                            ResultsTab.Results -> {
                                                ResultsPanel(
                                                    state = resultsState,
                                                    onEvent = resultsViewModel::onEvent,
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                            ResultsTab.QueryPlan -> {
                                                // Calculate max cost from plan nodes for visualization
                                                val maxCost =
                                                    queryPlanState.planNodes
                                                        .mapNotNull { it.totalCost }
                                                        .maxOrNull() ?: 0.0
                                                QueryPlanPanel(
                                                    state = queryPlanState,
                                                    onEvent = queryPlanViewModel::onEvent,
                                                    maxCost = maxCost,
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                    }
                                },
                            )
                        },
                        secondPane = {
                            // Right sidebar - History
                            HistoryPanel(
                                state = historyState,
                                onEvent = historyViewModel::onEvent,
                            )
                        },
                    )
                },
            )

            // Status bar
            StatusBar(
                connectionStatus = activeConnection?.config?.toDisplayString(),
                queryStatus = if (editorState.isExecuting) "Executing..." else null,
                cursorPosition =
                    editorState.activeTab?.let {
                        val lines = it.content.substring(0, it.cursorPosition).count { c -> c == '\n' } + 1
                        val col = it.cursorPosition - it.content.lastIndexOf('\n', it.cursorPosition - 1)
                        "Ln $lines, Col $col"
                    },
            )
        }
    }

    // Connection dialog
    connectionState.dialogState?.let { dialogState ->
        ConnectionDialog(
            state = dialogState,
            onEvent = connectionViewModel::onEvent,
        )
    }
}

@Composable
private fun ResultsAreaTabs(
    activeTab: ResultsTab,
    onTabSelected: (ResultsTab) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        ResultsTab.entries.forEach { tab ->
            Surface(
                modifier =
                    Modifier
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 1.dp),
                color =
                    if (tab == activeTab) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ) {
                Text(
                    text = tab.title,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        if (tab == activeTab) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}
