package su.kidoz.feature.editor.quickfix

/**
 * Represents a quick-fix action that can be applied to resolve a validation issue.
 */
sealed interface QuickFix {
    /** Display title for the quick-fix */
    val title: String

    /** Issue code this fix addresses (e.g., "SQL101", "SQL301") */
    val issueCode: String

    /**
     * Replace a range of text with new text.
     * Used for fixes like "Replace = NULL with IS NULL".
     */
    data class ReplaceText(
        override val title: String,
        override val issueCode: String,
        val range: IntRange,
        val newText: String,
    ) : QuickFix

    /**
     * Insert text at a specific position.
     * Used for fixes like "Add LIMIT clause".
     */
    data class InsertText(
        override val title: String,
        override val issueCode: String,
        val position: Int,
        val text: String,
    ) : QuickFix

    /**
     * Expand SELECT * to explicit column list.
     */
    data class ExpandSelectStar(
        override val title: String,
        override val issueCode: String,
        val range: IntRange,
        val columns: List<String>,
    ) : QuickFix

    /**
     * Add WHERE clause template.
     * Used for UPDATE/DELETE without WHERE warnings.
     */
    data class AddWhereClause(
        override val title: String,
        override val issueCode: String,
        val insertPosition: Int,
        val suggestedCondition: String = "1 = 1",
    ) : QuickFix

    /**
     * Delete a range of text.
     * Used for removing unnecessary elements.
     */
    data class DeleteText(
        override val title: String,
        override val issueCode: String,
        val range: IntRange,
    ) : QuickFix

    /**
     * Multiple text edits to apply atomically.
     */
    data class MultiEdit(
        override val title: String,
        override val issueCode: String,
        val edits: List<TextEdit>,
    ) : QuickFix

    /**
     * A single text edit within a MultiEdit
     */
    data class TextEdit(
        val range: IntRange,
        val newText: String,
    )
}

/**
 * Apply a quick-fix to content and return the modified content with new cursor position.
 */
fun QuickFix.apply(content: String): Pair<String, Int> =
    when (this) {
        is QuickFix.ReplaceText -> {
            val before = content.substring(0, range.first)
            val after = content.substring(range.last + 1)
            val newContent = before + newText + after
            val newCursor = range.first + newText.length
            newContent to newCursor
        }

        is QuickFix.InsertText -> {
            val before = content.substring(0, position)
            val after = content.substring(position)
            val newContent = before + text + after
            val newCursor = position + text.length
            newContent to newCursor
        }

        is QuickFix.ExpandSelectStar -> {
            val columnList = columns.joinToString(", ")
            val before = content.substring(0, range.first)
            val after = content.substring(range.last + 1)
            val newContent = before + columnList + after
            val newCursor = range.first + columnList.length
            newContent to newCursor
        }

        is QuickFix.AddWhereClause -> {
            val whereClause = " WHERE $suggestedCondition"
            val before = content.substring(0, insertPosition)
            val after = content.substring(insertPosition)
            val newContent = before + whereClause + after
            // Position cursor at the condition (after "WHERE ")
            val newCursor = insertPosition + 7
            newContent to newCursor
        }

        is QuickFix.DeleteText -> {
            val before = content.substring(0, range.first)
            val after = content.substring(range.last + 1)
            val newContent = before + after
            newContent to range.first
        }

        is QuickFix.MultiEdit -> {
            // Apply edits in reverse order to preserve positions
            var result = content
            var lastEditEnd = content.length
            val sortedEdits = edits.sortedByDescending { it.range.first }

            sortedEdits.forEach { edit ->
                val before = result.substring(0, edit.range.first)
                val after = result.substring(edit.range.last + 1)
                result = before + edit.newText + after
                lastEditEnd = edit.range.first + edit.newText.length
            }

            result to lastEditEnd
        }
    }
