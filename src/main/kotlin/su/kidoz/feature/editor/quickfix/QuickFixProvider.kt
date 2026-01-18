package su.kidoz.feature.editor.quickfix

import su.kidoz.feature.parser.validation.ValidationIssue

/**
 * Provides quick-fixes for validation issues.
 * Maps issue codes to appropriate fix generators.
 */
class QuickFixProvider {
    /**
     * Get available quick-fixes for a validation issue.
     * @param issue The validation issue to fix
     * @param content The current editor content
     * @param availableColumns Optional list of available columns (for SELECT * expansion)
     * @param availableTables Optional list of available tables (for unknown table suggestions)
     */
    fun getQuickFixes(
        issue: ValidationIssue,
        content: String,
        availableColumns: List<String> = emptyList(),
        availableTables: List<String> = emptyList(),
    ): List<QuickFix> {
        val fixes = mutableListOf<QuickFix>()

        when (issue.code) {
            // SQL101: SELECT * warning
            "SQL101" -> {
                if (availableColumns.isNotEmpty()) {
                    fixes.add(
                        QuickFix.ExpandSelectStar(
                            title = "Expand to column list",
                            issueCode = "SQL101",
                            range = findSelectStarRange(content, issue.position.start),
                            columns = availableColumns,
                        ),
                    )
                }
            }

            // SQL102: SELECT without LIMIT
            "SQL102" -> {
                val insertPos = findEndOfQuery(content, issue.position.start)
                fixes.add(
                    QuickFix.InsertText(
                        title = "Add LIMIT 100",
                        issueCode = "SQL102",
                        position = insertPos,
                        text = " LIMIT 100",
                    ),
                )
                fixes.add(
                    QuickFix.InsertText(
                        title = "Add LIMIT 1000",
                        issueCode = "SQL102",
                        position = insertPos,
                        text = " LIMIT 1000",
                    ),
                )
            }

            // SQL201: Unknown table
            "SQL201" -> {
                val unknownTable = extractUnknownName(issue.message, "table")
                if (unknownTable != null && availableTables.isNotEmpty()) {
                    val similar = StringSimilarity.findSimilar(unknownTable, availableTables, maxResults = 3)
                    similar.forEach { suggestion ->
                        fixes.add(
                            QuickFix.ReplaceText(
                                title = "Change to '$suggestion'",
                                issueCode = "SQL201",
                                range = issue.position.start..issue.position.end - 1,
                                newText = suggestion,
                            ),
                        )
                    }
                }
            }

            // SQL301: = NULL instead of IS NULL
            "SQL301" -> {
                val nullCompareRange = findNullComparisonRange(content, issue.position.start)
                if (nullCompareRange != null) {
                    val isNotEqual =
                        content.substring(nullCompareRange).contains("!=") ||
                            content.substring(nullCompareRange).contains("<>")
                    fixes.add(
                        QuickFix.ReplaceText(
                            title = if (isNotEqual) "Replace with IS NOT NULL" else "Replace with IS NULL",
                            issueCode = "SQL301",
                            range = nullCompareRange,
                            newText = if (isNotEqual) "IS NOT NULL" else "IS NULL",
                        ),
                    )
                }
            }

            // SQL801: UPDATE without WHERE
            "SQL801" -> {
                val whereInsertPos = findWhereInsertPosition(content, issue.position.start, "UPDATE")
                if (whereInsertPos > 0) {
                    fixes.add(
                        QuickFix.AddWhereClause(
                            title = "Add WHERE clause",
                            issueCode = "SQL801",
                            insertPosition = whereInsertPos,
                            suggestedCondition = "id = ?",
                        ),
                    )
                    fixes.add(
                        QuickFix.AddWhereClause(
                            title = "Add WHERE 1=1 (confirm all)",
                            issueCode = "SQL801",
                            insertPosition = whereInsertPos,
                            suggestedCondition = "1 = 1 /* Intentionally updating all rows */",
                        ),
                    )
                }
            }

            // SQL901: DELETE without WHERE
            "SQL901" -> {
                val whereInsertPos = findWhereInsertPosition(content, issue.position.start, "DELETE")
                if (whereInsertPos > 0) {
                    fixes.add(
                        QuickFix.AddWhereClause(
                            title = "Add WHERE clause",
                            issueCode = "SQL901",
                            insertPosition = whereInsertPos,
                            suggestedCondition = "id = ?",
                        ),
                    )
                    fixes.add(
                        QuickFix.AddWhereClause(
                            title = "Add WHERE 1=1 (confirm all)",
                            issueCode = "SQL901",
                            insertPosition = whereInsertPos,
                            suggestedCondition = "1 = 1 /* Intentionally deleting all rows */",
                        ),
                    )
                }
            }

            // SQL103: JOIN without ON clause
            "SQL103" -> {
                val onInsertPos = findJoinOnInsertPosition(content, issue.position.start)
                if (onInsertPos > 0) {
                    fixes.add(
                        QuickFix.InsertText(
                            title = "Add ON clause",
                            issueCode = "SQL103",
                            position = onInsertPos,
                            text = " ON table1.id = table2.id",
                        ),
                    )
                }
            }

            // SQL402: COALESCE with single argument
            "SQL402" -> {
                fixes.add(
                    QuickFix.InsertText(
                        title = "Add default value",
                        issueCode = "SQL402",
                        position = issue.position.end - 1,
                        text = ", ''",
                    ),
                )
            }
        }

        return fixes
    }

    /**
     * Find the range of SELECT * in content
     */
    private fun findSelectStarRange(
        content: String,
        startPosition: Int,
    ): IntRange {
        // Look for SELECT ... * pattern
        val searchStart = maxOf(0, startPosition - 20)
        val searchEnd = minOf(content.length, startPosition + 20)
        val searchArea = content.substring(searchStart, searchEnd)

        val starIndex = searchArea.indexOf('*')
        return if (starIndex >= 0) {
            val absoluteStart = searchStart + starIndex
            absoluteStart..absoluteStart
        } else {
            startPosition..startPosition
        }
    }

    /**
     * Find the end position of a query for inserting LIMIT
     */
    private fun findEndOfQuery(
        content: String,
        startPosition: Int,
    ): Int {
        // Find the end of the current query (semicolon or end of content)
        var pos = startPosition
        var parenDepth = 0

        while (pos < content.length) {
            when (content[pos]) {
                '(' -> parenDepth++
                ')' -> parenDepth--
                ';' -> if (parenDepth == 0) return pos
            }
            pos++
        }
        return content.length
    }

    /**
     * Extract unknown name from validation message
     */
    private fun extractUnknownName(
        message: String,
        type: String,
    ): String? {
        // Pattern: "Unknown table: tablename" or "Unknown column: columnname"
        val pattern = Regex("Unknown $type: (\\w+)", RegexOption.IGNORE_CASE)
        return pattern.find(message)?.groupValues?.get(1)
    }

    /**
     * Find the range of NULL comparison (e.g., "= NULL", "!= NULL")
     */
    private fun findNullComparisonRange(
        content: String,
        position: Int,
    ): IntRange? {
        // Search around the position for patterns like "= NULL", "<> NULL", "!= NULL"
        val searchStart = maxOf(0, position - 10)
        val searchEnd = minOf(content.length, position + 10)
        val searchArea = content.substring(searchStart, searchEnd).uppercase()

        val patterns = listOf("= NULL", "!= NULL", "<> NULL")
        for (pattern in patterns) {
            val index = searchArea.indexOf(pattern)
            if (index >= 0) {
                val absoluteStart = searchStart + index
                val absoluteEnd = absoluteStart + pattern.length - 1
                return absoluteStart..absoluteEnd
            }
        }
        return null
    }

    /**
     * Find position to insert WHERE clause for UPDATE/DELETE
     */
    private fun findWhereInsertPosition(
        content: String,
        startPosition: Int,
        statementType: String,
    ): Int {
        val upperContent = content.uppercase()

        return when (statementType) {
            "UPDATE" -> {
                // Find SET clause, then find end of assignments
                val setIndex = upperContent.indexOf("SET", startPosition)
                if (setIndex < 0) return -1

                var pos = setIndex + 3
                var parenDepth = 0

                while (pos < content.length) {
                    when {
                        content[pos] == '(' -> parenDepth++
                        content[pos] == ')' -> parenDepth--
                        content[pos] == ';' && parenDepth == 0 -> return pos
                        upperContent.substring(pos).startsWith("WHERE") && parenDepth == 0 -> return -1
                    }
                    pos++
                }
                content.length
            }

            "DELETE" -> {
                // Find FROM clause, then find table name end
                val fromIndex = upperContent.indexOf("FROM", startPosition)
                if (fromIndex < 0) return -1

                var pos = fromIndex + 4
                // Skip whitespace
                while (pos < content.length && content[pos].isWhitespace()) pos++
                // Skip table name
                while (pos < content.length && (content[pos].isLetterOrDigit() || content[pos] == '_' || content[pos] == '.')) pos++

                if (pos < content.length && upperContent.substring(pos).trimStart().startsWith("WHERE")) {
                    return -1
                }
                pos
            }

            else -> -1
        }
    }

    /**
     * Find position to insert ON clause for JOIN
     */
    private fun findJoinOnInsertPosition(
        content: String,
        startPosition: Int,
    ): Int {
        val upperContent = content.uppercase()
        val joinIndex = upperContent.lastIndexOf("JOIN", startPosition + 10)
        if (joinIndex < 0) return -1

        var pos = joinIndex + 4
        // Skip whitespace
        while (pos < content.length && content[pos].isWhitespace()) pos++
        // Skip table name and optional alias
        while (pos < content.length && (content[pos].isLetterOrDigit() || content[pos] == '_' || content[pos] == '.')) pos++
        // Skip whitespace
        while (pos < content.length && content[pos].isWhitespace()) pos++
        // Skip optional alias
        if (!upperContent.substring(pos).trimStart().startsWith("ON")) {
            // Check for AS keyword
            if (upperContent.substring(pos).trimStart().startsWith("AS ")) {
                pos += upperContent.substring(pos).indexOf("AS ") + 3
                while (pos < content.length && content[pos].isWhitespace()) pos++
            }
            // Skip alias name
            while (pos < content.length && (content[pos].isLetterOrDigit() || content[pos] == '_')) pos++
        }

        return pos
    }
}
