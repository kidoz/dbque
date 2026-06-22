package su.kidoz.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.compose.koinInject
import su.kidoz.database.ConnectionManager
import su.kidoz.feature.connection.ConnectionEffect
import su.kidoz.feature.connection.ConnectionViewModel
import su.kidoz.feature.connection.ui.ConnectionDialog
import su.kidoz.feature.connection.ui.ConnectionList
import su.kidoz.feature.diagram.DiagramEffect
import su.kidoz.feature.diagram.DiagramEvent
import su.kidoz.feature.diagram.DiagramViewModel
import su.kidoz.feature.diagram.ui.ErDiagramPanel
import su.kidoz.feature.editor.EditorEffect
import su.kidoz.feature.editor.EditorEvent
import su.kidoz.feature.editor.EditorViewModel
import su.kidoz.feature.editor.ui.EditorTabs
import su.kidoz.feature.editor.ui.IssuesPanel
import su.kidoz.feature.editor.ui.SqlEditor
import su.kidoz.feature.explorer.ExplorerEffect
import su.kidoz.feature.explorer.ExplorerEvent
import su.kidoz.feature.explorer.ExplorerViewModel
import su.kidoz.feature.explorer.ui.DatabaseTree
import su.kidoz.feature.explorer.ui.ElasticsearchIndexDialog
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
import su.kidoz.feature.settings.SettingsEffect
import su.kidoz.feature.settings.SettingsEvent
import su.kidoz.feature.settings.SettingsViewModel
import su.kidoz.feature.settings.ui.SettingsDialog
import su.kidoz.ui.components.HorizontalSplitPane
import su.kidoz.ui.components.MainToolbar
import su.kidoz.ui.components.StatusBar
import su.kidoz.ui.components.VerticalSplitPane

private enum class ResultsTab(
    val title: String,
) {
    Results("Results"),
    QueryPlan("Query Plan"),
    Issues("Issues"),
    History("History"),
}

private enum class WorkspaceMode {
    Query,
    Diagram,
}

@Composable
fun MainWindow() {
    val connectionViewModel: ConnectionViewModel = koinInject()
    val explorerViewModel: ExplorerViewModel = koinInject()
    val editorViewModel: EditorViewModel = koinInject()
    val diagramViewModel: DiagramViewModel = koinInject()
    val resultsViewModel: ResultsViewModel = koinInject()
    val historyViewModel: HistoryViewModel = koinInject()
    val queryPlanViewModel: QueryPlanViewModel = koinInject()
    val savedQueryViewModel: SavedQueryViewModel = koinInject()
    val settingsViewModel: SettingsViewModel = koinInject()
    val connectionManager: ConnectionManager = koinInject()

    val connectionState by connectionViewModel.state.collectAsState()
    val explorerState by explorerViewModel.state.collectAsState()
    val editorState by editorViewModel.state.collectAsState()
    val diagramState by diagramViewModel.state.collectAsState()
    val resultsState by resultsViewModel.state.collectAsState()
    val historyState by historyViewModel.state.collectAsState()
    val queryPlanState by queryPlanViewModel.state.collectAsState()
    val savedQueryState by savedQueryViewModel.state.collectAsState()
    val settingsState by settingsViewModel.state.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var activeResultsTab by remember { mutableStateOf(ResultsTab.Results) }
    var activeWorkspace by remember { mutableStateOf(WorkspaceMode.Query) }

    // Handle effects
    LaunchedEffect(Unit) {
        connectionViewModel.effect
            .onEach { effect ->
                when (effect) {
                    is ConnectionEffect.ShowError -> {
                        snackbarHostState.showSnackbar(effect.message)
                    }

                    is ConnectionEffect.ShowSuccess -> {
                        snackbarHostState.showSnackbar(effect.message)
                    }

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

                    is EditorEffect.QuickFixesAvailable -> {
                        // Quick fixes are handled within SqlEditor component
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

                    is ExplorerEffect.IndexCreated -> {
                        snackbarHostState.showSnackbar("Index '${effect.indexName}' created successfully")
                    }

                    is ExplorerEffect.IndexDeleted -> {
                        snackbarHostState.showSnackbar("Index '${effect.indexName}' deleted")
                    }

                    is ExplorerEffect.IndexUpdated -> {
                        snackbarHostState.showSnackbar("Index '${effect.indexName}' updated")
                    }
                }
            }.launchIn(this)

        diagramViewModel.effect
            .onEach { effect ->
                when (effect) {
                    is DiagramEffect.ShowError -> {
                        snackbarHostState.showSnackbar(effect.message)
                    }

                    is DiagramEffect.ShowMessage -> {
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

        settingsViewModel.effect
            .onEach { effect ->
                when (effect) {
                    is SettingsEffect.SettingsSaved -> {
                        snackbarHostState.showSnackbar("Settings saved")
                    }

                    is SettingsEffect.SettingsReset -> {
                        snackbarHostState.showSnackbar("Settings reset to defaults")
                    }

                    is SettingsEffect.ShowError -> {
                        snackbarHostState.showSnackbar(effect.message)
                    }
                }
            }.launchIn(this)
    }

    val activeConnection = connectionManager.activeConnection

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        ) {
            val isCompact = maxWidth < 1100.dp
            val tabs =
                remember(isCompact) {
                    if (isCompact) {
                        listOf(ResultsTab.Results, ResultsTab.QueryPlan, ResultsTab.Issues, ResultsTab.History)
                    } else {
                        listOf(ResultsTab.Results, ResultsTab.QueryPlan, ResultsTab.Issues)
                    }
                }

            LaunchedEffect(isCompact) {
                if (!isCompact && activeResultsTab == ResultsTab.History) {
                    activeResultsTab = ResultsTab.Results
                }
            }

            val backgroundBrush =
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    ),
                )

            var contentVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                contentVisible = true
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(backgroundBrush),
            ) {
                // Toolbar
                MainToolbar(
                    connectionName = activeConnection?.config?.name,
                    isExecuting = editorState.isExecuting,
                    isDiagramActive = activeWorkspace == WorkspaceMode.Diagram,
                    onExecute = { editorViewModel.onEvent(EditorEvent.ExecuteQuery) },
                    onCancel = { editorViewModel.onEvent(EditorEvent.CancelExecution) },
                    onNewTab = { editorViewModel.onEvent(EditorEvent.NewTab) },
                    onShowQuery = { activeWorkspace = WorkspaceMode.Query },
                    onShowDiagram = {
                        activeWorkspace = WorkspaceMode.Diagram
                        diagramViewModel.onEvent(DiagramEvent.LoadDiagram)
                    },
                    onSettings = { settingsViewModel.onEvent(SettingsEvent.ShowDialog) },
                )

                val editorAndResultsPane: @Composable () -> Unit = {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface),
                    ) {
                        VerticalSplitPane(
                            splitFraction = 0.55f,
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
                                    ResultsAreaTabs(
                                        tabs = tabs,
                                        activeTab = activeResultsTab,
                                        onTabSelected = { activeResultsTab = it },
                                    )

                                    AnimatedContent(
                                        targetState = activeResultsTab,
                                        transitionSpec = {
                                            (fadeIn() + slideInVertically(initialOffsetY = { it / 6 }))
                                                .togetherWith(fadeOut() + slideOutVertically(targetOffsetY = { -it / 8 }))
                                        },
                                    ) { targetTab ->
                                        when (targetTab) {
                                            ResultsTab.Results -> {
                                                ResultsPanel(
                                                    state = resultsState,
                                                    onEvent = resultsViewModel::onEvent,
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            }

                                            ResultsTab.QueryPlan -> {
                                                val maxCost =
                                                    queryPlanState.planNodes
                                                        .mapNotNull { it.totalCost }
                                                        .maxOrNull() ?: 0.0
                                                QueryPlanPanel(
                                                    state = queryPlanState,
                                                    onEvent = queryPlanViewModel::onEvent,
                                                    maxCost = maxCost,
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            }

                                            ResultsTab.Issues -> {
                                                val activeTab = editorState.activeTab
                                                IssuesPanel(
                                                    issues = activeTab?.validationIssues ?: emptyList(),
                                                    content = activeTab?.content ?: "",
                                                    onIssueClick = { issue ->
                                                        editorViewModel.onEvent(EditorEvent.NavigateToIssue(issue))
                                                    },
                                                    onQuickFix = { issue ->
                                                        editorViewModel.onEvent(EditorEvent.ShowQuickFixes(issue))
                                                    },
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            }

                                            ResultsTab.History -> {
                                                HistoryPanel(
                                                    state = historyState,
                                                    onEvent = historyViewModel::onEvent,
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    }
                }

                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 10 }),
                    modifier = Modifier.weight(1f),
                ) {
                    // Main content
                    HorizontalSplitPane(
                        splitFraction = if (isCompact) 0.26f else 0.2f,
                        firstPane = {
                            // Left sidebar - Connections, Explorer, and Saved Queries
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface),
                            ) {
                                ConnectionList(
                                    state = connectionState,
                                    onEvent = connectionViewModel::onEvent,
                                    modifier = Modifier.weight(0.3f),
                                )

                                DatabaseTree(
                                    state = explorerState,
                                    onEvent = explorerViewModel::onEvent,
                                    modifier = Modifier.weight(0.4f),
                                )

                                SavedQueriesPanel(
                                    state = savedQueryState,
                                    onEvent = savedQueryViewModel::onEvent,
                                    modifier = Modifier.weight(0.3f),
                                )
                            }
                        },
                        secondPane = {
                            if (activeWorkspace == WorkspaceMode.Diagram) {
                                ErDiagramPanel(
                                    state = diagramState,
                                    onEvent = diagramViewModel::onEvent,
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surface),
                                )
                            } else if (isCompact) {
                                editorAndResultsPane()
                            } else {
                                HorizontalSplitPane(
                                    splitFraction = 0.8f,
                                    firstPane = { editorAndResultsPane() },
                                    secondPane = {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .fillMaxSize()
                                                    .background(MaterialTheme.colorScheme.surface),
                                        ) {
                                            HistoryPanel(
                                                state = historyState,
                                                onEvent = historyViewModel::onEvent,
                                            )
                                        }
                                    },
                                )
                            }
                        },
                    )
                }

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
    }

    // Connection dialog
    connectionState.dialogState?.let { dialogState ->
        ConnectionDialog(
            state = dialogState,
            onEvent = connectionViewModel::onEvent,
        )
    }

    // Elasticsearch Index dialog
    explorerState.indexDialogState?.let { dialogState ->
        ElasticsearchIndexDialog(
            state = dialogState,
            onEvent = explorerViewModel::onEvent,
        )
    }

    // Delete confirmation dialog
    explorerState.deleteConfirmation?.let { confirmation ->
        AlertDialog(
            onDismissRequest = { explorerViewModel.onEvent(ExplorerEvent.CancelDelete) },
            title = { Text("Confirm Delete") },
            text = { Text(confirmation.message) },
            confirmButton = {
                Button(
                    onClick = { explorerViewModel.onEvent(ExplorerEvent.DeleteIndex(confirmation.indexName)) },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { explorerViewModel.onEvent(ExplorerEvent.CancelDelete) }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Settings dialog
    SettingsDialog(
        state = settingsState,
        onEvent = settingsViewModel::onEvent,
    )
}

@Composable
private fun ResultsAreaTabs(
    tabs: List<ResultsTab>,
    activeTab: ResultsTab,
    onTabSelected: (ResultsTab) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        tabs.forEach { tab ->
            val isActive = tab == activeTab
            val containerColor by animateColorAsState(
                targetValue =
                    if (isActive) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                label = "resultsTabContainer",
            )
            val textColor by animateColorAsState(
                targetValue =
                    if (isActive) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                label = "resultsTabText",
            )
            Surface(
                modifier =
                    Modifier
                        .padding(horizontal = 4.dp)
                        .clickable { onTabSelected(tab) },
                color = containerColor,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = tab.title,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                )
            }
        }
    }
}
