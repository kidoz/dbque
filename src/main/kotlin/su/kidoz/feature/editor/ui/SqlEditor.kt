package su.kidoz.feature.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
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
 * Query Editor with syntax highlighting and validation
 * Supports SQL, MongoDB, and Elasticsearch with auto-detection
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

    // Validation state
    var validationIssues by remember { mutableStateOf<List<ValidationIssue>>(emptyList()) }

    // Validate on text change (debounced) - uses auto-detection
    LaunchedEffect(tab.content) {
        if (tab.content.isNotBlank()) {
            val result = parserService.validate(tab.content)
            validationIssues = result.issues
        } else {
            validationIssues = emptyList()
        }
    }

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

    // Current query range for highlighting
    val currentQuery = tab.currentQuery

    Column(modifier = modifier) {
        // Execution toolbar
        EditorToolbar(
            queryCount = tab.queryCount,
            currentQueryNumber = tab.currentQueryNumber,
            selectedText = tab.selectedText,
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
            // Line numbers
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
                    val hasError =
                        validationIssues.any { issue ->
                            issue.severity == IssueSeverity.ERROR && getLineNumber(tab.content, issue.position.start) == i
                        }
                    val hasWarning =
                        validationIssues.any { issue ->
                            issue.severity == IssueSeverity.WARNING && getLineNumber(tab.content, issue.position.start) == i
                        }

                    val lineColor =
                        when {
                            hasError -> Color(0xFFF44747)
                            hasWarning -> Color(0xFFFFCC00)
                            else -> extendedColors.editorLineNumber
                        }

                    Text(
                        text = i.toString(),
                        style = EditorTypography,
                        color = lineColor,
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp, top = 2.dp),
                    )
                }
            }

            // Editor content - use BoxWithConstraints to get available height
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

        // Validation issues panel
        if (validationIssues.isNotEmpty()) {
            ValidationIssuesPanel(
                issues = validationIssues,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    LaunchedEffect(tab.id) {
        focusRequester.requestFocus()
    }
}

/**
 * Get line number for a given character offset
 */
private fun getLineNumber(
    text: String,
    offset: Int,
): Int {
    if (offset <= 0) return 1
    val safeOffset = offset.coerceAtMost(text.length)
    return text.substring(0, safeOffset).count { it == '\n' } + 1
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
 * Legacy transformation for backwards compatibility
 */
@Deprecated("Use QuerySyntaxHighlightTransformation instead")
class SqlSyntaxHighlightTransformation(
    private val parserService: QueryParserService,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = parserService.highlightSql(text.text, SyntaxTheme.Dark)
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

            // Keyboard shortcuts hint
            Text(
                text = "Ctrl+E: Run | Ctrl+L: Format",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

/**
 * Panel showing validation issues
 */
@Composable
private fun ValidationIssuesPanel(
    issues: List<ValidationIssue>,
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
                modifier = Modifier.padding(vertical = 2.dp),
            ) {
                Icon(
                    imageVector =
                        when (issue.severity) {
                            IssueSeverity.ERROR -> Icons.Default.Error
                            IssueSeverity.WARNING -> Icons.Default.Warning
                            else -> Icons.Default.Info
                        },
                    contentDescription = issue.severity.name,
                    tint =
                        when (issue.severity) {
                            IssueSeverity.ERROR -> Color(0xFFF44747)
                            IssueSeverity.WARNING -> Color(0xFFFFCC00)
                            IssueSeverity.INFO -> Color(0xFF3794FF)
                            IssueSeverity.HINT -> Color(0xFF6A9955)
                        },
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "[${issue.code}] ${issue.message}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                issue.suggestion?.let { suggestion ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "($suggestion)",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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

/**
 * Legacy highlight function - replaced by QueryParserService
 */
@Composable
@Deprecated("Use QueryParserService instead", replaceWith = ReplaceWith("parserService.highlightSql(text)"))
fun highlightSql(text: String): AnnotatedString {
    val extendedColors = DBQueTheme.extendedColors

    return buildAnnotatedString {
        append(text)

        val keywords =
            listOf(
                "SELECT",
                "FROM",
                "WHERE",
                "AND",
                "OR",
                "NOT",
                "IN",
                "IS",
                "NULL",
                "ORDER",
                "BY",
                "GROUP",
                "HAVING",
                "LIMIT",
                "OFFSET",
                "AS",
                "ON",
                "JOIN",
                "LEFT",
                "RIGHT",
                "INNER",
                "OUTER",
                "FULL",
                "CROSS",
                "INSERT",
                "INTO",
                "VALUES",
                "UPDATE",
                "SET",
                "DELETE",
                "CREATE",
                "ALTER",
                "DROP",
                "TABLE",
                "INDEX",
                "VIEW",
                "DATABASE",
                "PRIMARY",
                "KEY",
                "FOREIGN",
                "REFERENCES",
                "UNIQUE",
                "CHECK",
                "DEFAULT",
                "AUTO_INCREMENT",
                "CASCADE",
                "RESTRICT",
                "BEGIN",
                "COMMIT",
                "ROLLBACK",
                "TRANSACTION",
                "UNION",
                "INTERSECT",
                "EXCEPT",
                "ALL",
                "DISTINCT",
                "ASC",
                "DESC",
                "NULLS",
                "FIRST",
                "LAST",
                "CASE",
                "WHEN",
                "THEN",
                "ELSE",
                "END",
                "LIKE",
                "BETWEEN",
                "EXISTS",
                "ANY",
                "SOME",
            )

        // Highlight keywords
        keywords.forEach { keyword ->
            val regex = Regex("\\b$keyword\\b", RegexOption.IGNORE_CASE)
            regex.findAll(text).forEach { match ->
                addStyle(
                    SpanStyle(color = extendedColors.syntaxKeyword),
                    match.range.first,
                    match.range.last + 1,
                )
            }
        }

        // Highlight strings
        val stringRegex = Regex("'[^']*'")
        stringRegex.findAll(text).forEach { match ->
            addStyle(
                SpanStyle(color = extendedColors.syntaxString),
                match.range.first,
                match.range.last + 1,
            )
        }

        // Highlight numbers
        val numberRegex = Regex("\\b\\d+(\\.\\d+)?\\b")
        numberRegex.findAll(text).forEach { match ->
            addStyle(
                SpanStyle(color = extendedColors.syntaxNumber),
                match.range.first,
                match.range.last + 1,
            )
        }

        // Highlight comments
        val lineCommentRegex = Regex("--.*$", RegexOption.MULTILINE)
        lineCommentRegex.findAll(text).forEach { match ->
            addStyle(
                SpanStyle(color = extendedColors.syntaxComment),
                match.range.first,
                match.range.last + 1,
            )
        }

        val blockCommentRegex = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
        blockCommentRegex.findAll(text).forEach { match ->
            addStyle(
                SpanStyle(color = extendedColors.syntaxComment),
                match.range.first,
                match.range.last + 1,
            )
        }
    }
}
