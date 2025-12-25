package su.kidoz.feature.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import su.kidoz.feature.editor.EditorEvent
import su.kidoz.feature.editor.EditorTab
import su.kidoz.ui.theme.DBQueTheme
import su.kidoz.ui.theme.EditorTypography

@Composable
fun SqlEditor(
    tab: EditorTab,
    onEvent: (EditorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var textFieldValue by remember(tab.id) {
        mutableStateOf(TextFieldValue(text = tab.content))
    }

    // Sync external changes
    LaunchedEffect(tab.content) {
        if (textFieldValue.text != tab.content) {
            textFieldValue = TextFieldValue(text = tab.content)
        }
    }

    val focusRequester = remember { FocusRequester() }
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    val extendedColors = DBQueTheme.extendedColors

    Row(modifier = modifier.background(extendedColors.editorBackground)) {
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
                Text(
                    text = i.toString(),
                    style = EditorTypography,
                    color = extendedColors.editorLineNumber,
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp, top = 2.dp),
                )
            }
        }

        // Editor content
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(verticalScroll)
                    .horizontalScroll(horizontalScroll),
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
                        .fillMaxSize()
                        .padding(vertical = 2.dp)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when {
                                    keyEvent.isCtrlPressed && keyEvent.key == Key.Enter -> {
                                        onEvent(EditorEvent.ExecuteQuery)
                                        true
                                    }
                                    keyEvent.isCtrlPressed && keyEvent.isShiftPressed && keyEvent.key == Key.Enter -> {
                                        onEvent(EditorEvent.ExecuteSelectedQuery)
                                        true
                                    }
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
                decorationBox = { innerTextField ->
                    Box {
                        if (tab.content.isEmpty()) {
                            Text(
                                text = "Enter SQL query here... (Ctrl+Enter to execute)",
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

    LaunchedEffect(tab.id) {
        focusRequester.requestFocus()
    }
}

@Composable
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
