package su.kidoz.feature.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.feature.editor.EditorEvent
import su.kidoz.feature.editor.EditorTab
import su.kidoz.feature.editor.QuerySplitter
import su.kidoz.feature.parser.QueryParserService
import su.kidoz.feature.parser.highlight.DatabaseType
import su.kidoz.feature.parser.highlight.SyntaxTheme
import su.kidoz.feature.parser.validation.IssueSeverity
import su.kidoz.feature.parser.validation.ValidationIssue
import su.kidoz.ui.theme.DBQueTheme
import su.kidoz.ui.theme.EditorTypography

/**
 * Query Editor with syntax highlighting, live validation, and quick-fixes.
 * Supports SQL, MongoDB, and Elasticsearch with auto-detection.
 */
@Composable
fun SqlEditor(
    tab: EditorTab,
    onEvent: (EditorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parserService = remember { QueryParserService() }

    var textFieldValue by remember(tab.id) {
        mutableStateOf(TextFieldValue(text = tab.content))
    }

    // Sync external changes
    LaunchedEffect(tab.content) {
        if (textFieldValue.text != tab.content) {
            textFieldValue =
                textFieldValue.copy(
                    text = tab.content,
                    selection = TextRange(tab.content.length.coerceAtMost(textFieldValue.selection.start)),
                )
        }
    }

    // Detect query type for UI hints
    val queryType =
        remember(tab.content) {
            parserService.detectQueryType(tab.content)
        }

    // Use validation issues from tab state (managed by ViewModel via LiveValidator)
    val validationIssues = tab.validationIssues

    val focusRequester = remember { FocusRequester() }
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    val extendedColors = DBQueTheme.extendedColors

    // Create syntax highlighting transformation - uses auto-detection
    val syntaxTheme = if (extendedColors.isDarkTheme) SyntaxTheme.Dark else SyntaxTheme.Light
    val syntaxHighlightTransformation =
        remember(syntaxTheme) {
            QuerySyntaxHighlightTransformation(parserService, syntaxTheme)
        }

    Column(modifier = modifier) {
        // Execution toolbar
        EditorToolbar(
            queryCount = tab.queryCount,
            currentQueryNumber = tab.currentQueryNumber,
            selectedText = tab.selectedText,
            isValidating = tab.isValidating,
            issueCount = validationIssues.size,
            errorCount = validationIssues.count { it.severity == IssueSeverity.ERROR },
            onExecuteCurrent = { onEvent(EditorEvent.ExecuteCurrentQuery) },
            onExecuteAll = { onEvent(EditorEvent.ExecuteAllQueries) },
            onExecuteSelected = { onEvent(EditorEvent.ExecuteSelectedQuery) },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .background(extendedColors.editorBackground),
        ) {
            // Line numbers with issue indicators
            val lineCount = tab.content.count { it == '\n' } + 1
            Column(
                modifier =
                    Modifier
                        .width(50.dp)
                        .fillMaxHeight()
                        .verticalScroll(verticalScroll)
                        .background(extendedColors.editorBackground)
                        .padding(end = 8.dp),
            ) {
                for (i in 1..lineCount) {
                    val lineIssues =
                        validationIssues.filter { issue ->
                            getLineNumber(tab.content, issue.position.start) == i
                        }
                    val hasError = lineIssues.any { it.severity == IssueSeverity.ERROR }
                    val hasWarning = lineIssues.any { it.severity == IssueSeverity.WARNING }

                    val lineColor =
                        when {
                            hasError -> IssueColors.error
                            hasWarning -> IssueColors.warning
                            else -> extendedColors.editorLineNumber
                        }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp, top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Issue indicator dot
                        if (lineIssues.isNotEmpty()) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(6.dp)
                                        .background(lineColor, androidx.compose.foundation.shape.CircleShape)
                                        .clickable {
                                            lineIssues.firstOrNull()?.let { issue ->
                                                onEvent(EditorEvent.NavigateToIssue(issue))
                                            }
                                        },
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                        }

                        Text(
                            text = i.toString(),
                            style = EditorTypography,
                            color = lineColor,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Editor content
            BoxWithConstraints(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
            ) {
                val minEditorHeight = maxHeight

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(verticalScroll),
                ) {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            textFieldValue = newValue
                            onEvent(EditorEvent.UpdateContent(newValue.text))
                            onEvent(EditorEvent.UpdateCursor(newValue.selection.start))
                            onEvent(EditorEvent.UpdateSelection(newValue.selection.start, newValue.selection.end))
                        },
                        modifier =
                            Modifier
                                .defaultMinSize(minHeight = minEditorHeight)
                                .fillMaxWidth()
                                .horizontalScroll(horizontalScroll)
                                .padding(vertical = 2.dp)
                                .focusRequester(focusRequester)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        when {
                                            // Alt+Enter: Show quick-fixes for issue at cursor
                                            keyEvent.isAltPressed &&
                                                !keyEvent.isCtrlPressed &&
                                                keyEvent.key == Key.Enter -> {
                                                val issueAtCursor = findIssueAtPosition(validationIssues, tab.cursorPosition)
                                                if (issueAtCursor != null) {
                                                    onEvent(EditorEvent.ShowQuickFixes(issueAtCursor))
                                                }
                                                true
                                            }
                                            // Ctrl+Enter: Execute current query at cursor
                                            keyEvent.isCtrlPressed &&
                                                !keyEvent.isShiftPressed &&
                                                !keyEvent.isAltPressed &&
                                                keyEvent.key == Key.Enter -> {
                                                onEvent(EditorEvent.ExecuteCurrentQuery)
                                                true
                                            }
                                            // Ctrl+Shift+Enter: Execute all queries
                                            keyEvent.isCtrlPressed &&
                                                keyEvent.isShiftPressed &&
                                                keyEvent.key == Key.Enter -> {
                                                onEvent(EditorEvent.ExecuteAllQueries)
                                                true
                                            }
                                            // Ctrl+E: Execute selected text (if any) or current query
                                            keyEvent.isCtrlPressed &&
                                                !keyEvent.isShiftPressed &&
                                                keyEvent.key == Key.E -> {
                                                if (tab.selectedText.isNotEmpty()) {
                                                    onEvent(EditorEvent.ExecuteSelectedQuery)
                                                } else {
                                                    onEvent(EditorEvent.ExecuteCurrentQuery)
                                                }
                                                true
                                            }
                                            // Ctrl+L: Format SQL
                                            keyEvent.isCtrlPressed && keyEvent.key == Key.L -> {
                                                onEvent(EditorEvent.Format)
                                                true
                                            }
                                            else -> false
                                        }
                                    } else {
                                        false
                                    }
                                },
                        textStyle = EditorTypography.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = syntaxHighlightTransformation,
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                if (tab.content.isEmpty()) {
                                    val placeholder =
                                        when (queryType) {
                                            DatabaseType.MONGODB -> "db.collection.find({...}) (Ctrl+Enter to execute)"
                                            DatabaseType.ELASTICSEARCH -> "{\"query\": {...}} (Ctrl+Enter to execute)"
                                            else -> "Enter SQL query here... (Ctrl+Enter to execute)"
                                        }
                                    Text(
                                        text = placeholder,
                                        style = EditorTypography,
                                        color = extendedColors.editorLineNumber,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                }
            }
        }

        // Validation issues panel - clickable to navigate
        if (validationIssues.isNotEmpty()) {
            ValidationIssuesPanel(
                issues = validationIssues,
                onIssueClick = { issue -> onEvent(EditorEvent.NavigateToIssue(issue)) },
                onQuickFix = { issue -> onEvent(EditorEvent.ShowQuickFixes(issue)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    LaunchedEffect(tab.id) {
        focusRequester.requestFocus()
    }
}

/**
 * Find issue at or near the given position
 */
private fun findIssueAtPosition(
    issues: List<ValidationIssue>,
    position: Int,
): ValidationIssue? =
    issues.find { issue ->
        position >= issue.position.start && position <= issue.position.end
    } ?: issues.minByOrNull { issue ->
        minOf(
            kotlin.math.abs(position - issue.position.start),
            kotlin.math.abs(position - issue.position.end),
        )
    }

/**
 * Visual transformation for syntax highlighting
 * Supports SQL, MongoDB, and Elasticsearch with auto-detection
 */
class QuerySyntaxHighlightTransformation(
    private val parserService: QueryParserService,
    private val theme: SyntaxTheme,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = parserService.highlight(text.text, theme)
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

/**
 * Toolbar for query execution with query count and execution buttons
 */
@Composable
private fun EditorToolbar(
    queryCount: Int,
    currentQueryNumber: Int,
    selectedText: String,
    isValidating: Boolean,
    issueCount: Int,
    errorCount: Int,
    onExecuteCurrent: () -> Unit,
    onExecuteAll: () -> Unit,
    onExecuteSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Calculate number of queries in selection
    val selectedQueryCount =
        remember(selectedText) {
            if (selectedText.isNotEmpty()) {
                QuerySplitter.getAllQueries(selectedText).size
            } else {
                0
            }
        }
    val hasSelection = selectedText.isNotEmpty()

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
        modifier = modifier,
    ) {
        Row(
            modifier =
                Modifier
                    .height(32.dp)
                    .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Query counter
            Text(
                text =
                    if (queryCount > 0) {
                        "Query $currentQueryNumber of $queryCount"
                    } else {
                        "No queries"
                    },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.width(16.dp))

            VerticalDivider(
                modifier = Modifier.height(20.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Execute current query button
            TextButton(
                onClick = onExecuteCurrent,
                enabled = queryCount > 0,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Execute current query",
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Run (Ctrl+Enter)",
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            // Execute all queries button
            if (queryCount > 1) {
                TextButton(
                    onClick = onExecuteAll,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.secondary,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = "Execute all queries",
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Run All (Ctrl+Shift+Enter)",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            // Execute selection button (shown when text is selected)
            if (hasSelection) {
                TextButton(
                    onClick = onExecuteSelected,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.tertiary,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Execute selection",
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text =
                            if (selectedQueryCount > 1) {
                                "Run $selectedQueryCount Queries"
                            } else {
                                "Run Selection"
                            },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Validation status
            if (isValidating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Validating...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            } else if (issueCount > 0) {
                if (errorCount > 0) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Errors",
                        tint = IssueColors.error,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$errorCount error${if (errorCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = IssueColors.error,
                    )
                }
                if (issueCount - errorCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warnings",
                        tint = IssueColors.warning,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${issueCount - errorCount} warning${if (issueCount - errorCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = IssueColors.warning,
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Keyboard shortcuts hint
            Text(
                text = "Ctrl+E: Run | Alt+Enter: Quick Fix",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

/**
 * Panel showing validation issues with click-to-navigate
 */
@Composable
private fun ValidationIssuesPanel(
    issues: List<ValidationIssue>,
    onIssueClick: (ValidationIssue) -> Unit,
    onQuickFix: (ValidationIssue) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
    ) {
        Text(
            text = "Issues (${issues.size})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        issues.take(5).forEach { issue ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onIssueClick(issue) }
                        .padding(vertical = 2.dp),
            ) {
                Icon(
                    imageVector =
                        when (issue.severity) {
                            IssueSeverity.ERROR -> Icons.Default.Error
                            IssueSeverity.WARNING -> Icons.Default.Warning
                            else -> Icons.Default.Info
                        },
                    contentDescription = issue.severity.name,
                    tint = IssueColors.forSeverity(issue.severity),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "[${issue.code}] ${issue.message}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    issue.suggestion?.let { suggestion ->
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
                // Quick-fix button
                TextButton(
                    onClick = { onQuickFix(issue) },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Text(
                        text = "Fix",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (issues.size > 5) {
            Text(
                text = "... and ${issues.size - 5} more",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
