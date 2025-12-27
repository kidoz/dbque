package su.kidoz.feature.editor

/**
 * Represents a single query with its position in the text
 */
data class QueryRange(
    val start: Int,
    val end: Int,
    val query: String,
    val index: Int,
) {
    val isEmpty: Boolean get() = query.isBlank()

    fun contains(position: Int): Boolean = position in start..end
}

/**
 * Result of splitting queries in editor content
 */
data class SplitResult(
    val queries: List<QueryRange>,
    val currentQueryIndex: Int,
) {
    val currentQuery: QueryRange? get() = queries.getOrNull(currentQueryIndex)
    val nonEmptyQueries: List<QueryRange> get() = queries.filter { !it.isEmpty }
    val queryCount: Int get() = nonEmptyQueries.size
}

/**
 * Utility to split SQL content into individual queries
 * Handles:
 * - Semicolon as statement separator
 * - Single-quoted strings ('...')
 * - Double-quoted identifiers ("...")
 * - Single-line comments (--)
 * - Multi-line comments (/* ... */)
 * - Dollar-quoted strings ($$...$$) for PostgreSQL
 */
object QuerySplitter {
    /**
     * Split content into individual queries and find which query contains the cursor
     */
    fun split(
        content: String,
        cursorPosition: Int = 0,
    ): SplitResult {
        if (content.isBlank()) {
            return SplitResult(emptyList(), -1)
        }

        val queries = mutableListOf<QueryRange>()
        var queryStart = 0
        var i = 0
        var currentQueryIndex = -1

        while (i < content.length) {
            when {
                // Single-line comment
                content.startsWith("--", i) -> {
                    i = skipToEndOfLine(content, i)
                }
                // Multi-line comment
                content.startsWith("/*", i) -> {
                    i = skipMultiLineComment(content, i)
                }
                // Single-quoted string
                content[i] == '\'' -> {
                    i = skipString(content, i, '\'')
                }
                // Double-quoted identifier
                content[i] == '"' -> {
                    i = skipString(content, i, '"')
                }
                // Dollar-quoted string (PostgreSQL)
                content.startsWith("\$\$", i) -> {
                    i = skipDollarQuotedString(content, i)
                }
                // Custom dollar tag like $tag$...$tag$
                content[i] == '$' &&
                    i + 1 < content.length &&
                    (content[i + 1].isLetter() || content[i + 1] == '_') -> {
                    i = skipCustomDollarTag(content, i)
                }
                // Semicolon - query separator
                content[i] == ';' -> {
                    val queryText = content.substring(queryStart, i).trim()
                    val range =
                        QueryRange(
                            start = queryStart,
                            end = i,
                            query = queryText,
                            index = queries.size,
                        )
                    queries.add(range)

                    if (cursorPosition in queryStart..i) {
                        currentQueryIndex = queries.size - 1
                    }

                    queryStart = i + 1
                    i++
                }
                else -> i++
            }
        }

        // Handle last query (without trailing semicolon)
        if (queryStart < content.length) {
            val queryText = content.substring(queryStart).trim()
            val range =
                QueryRange(
                    start = queryStart,
                    end = content.length,
                    query = queryText,
                    index = queries.size,
                )
            queries.add(range)

            if (cursorPosition >= queryStart) {
                currentQueryIndex = queries.size - 1
            }
        }

        // If cursor position wasn't found, default to first non-empty query
        if (currentQueryIndex == -1 && queries.isNotEmpty()) {
            currentQueryIndex = queries.indexOfFirst { !it.isEmpty }.takeIf { it >= 0 } ?: 0
        }

        return SplitResult(queries, currentQueryIndex)
    }

    /**
     * Get the query at the given cursor position
     */
    fun getQueryAtCursor(
        content: String,
        cursorPosition: Int,
    ): QueryRange? {
        val result = split(content, cursorPosition)
        return result.currentQuery?.takeIf { !it.isEmpty }
    }

    /**
     * Get all non-empty queries from content
     */
    fun getAllQueries(content: String): List<QueryRange> = split(content).nonEmptyQueries

    private fun skipToEndOfLine(
        content: String,
        start: Int,
    ): Int {
        var i = start
        while (i < content.length && content[i] != '\n') {
            i++
        }
        return if (i < content.length) i + 1 else i
    }

    private fun skipMultiLineComment(
        content: String,
        start: Int,
    ): Int {
        var i = start + 2
        while (i < content.length - 1) {
            if (content[i] == '*' && content[i + 1] == '/') {
                return i + 2
            }
            i++
        }
        return content.length
    }

    private fun skipString(
        content: String,
        start: Int,
        quote: Char,
    ): Int {
        var i = start + 1
        while (i < content.length) {
            when {
                // Escaped quote (doubled)
                content[i] == quote && i + 1 < content.length && content[i + 1] == quote -> {
                    i += 2
                }
                // End of string
                content[i] == quote -> {
                    return i + 1
                }
                // Backslash escape
                content[i] == '\\' && i + 1 < content.length -> {
                    i += 2
                }
                else -> i++
            }
        }
        return content.length
    }

    private fun skipDollarQuotedString(
        content: String,
        start: Int,
    ): Int {
        var i = start + 2
        while (i < content.length - 1) {
            if (content[i] == '$' && content[i + 1] == '$') {
                return i + 2
            }
            i++
        }
        return content.length
    }

    private fun skipCustomDollarTag(
        content: String,
        start: Int,
    ): Int {
        // Find the tag: $tag$
        var i = start + 1
        while (i < content.length && (content[i].isLetterOrDigit() || content[i] == '_')) {
            i++
        }
        if (i >= content.length || content[i] != '$') {
            return start + 1 // Not a valid dollar tag
        }

        val tag = content.substring(start, i + 1) // includes both $
        i++ // Skip closing $

        // Find matching closing tag
        while (i < content.length) {
            val remaining = content.length - i
            if (remaining >= tag.length && content.substring(i, i + tag.length) == tag) {
                return i + tag.length
            }
            i++
        }
        return content.length
    }
}
