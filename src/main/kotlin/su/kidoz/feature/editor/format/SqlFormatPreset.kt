package su.kidoz.feature.editor.format

enum class KeywordCasing {
    UPPERCASE,
    LOWERCASE,
    CAPITALIZED,
    UNCHANGED,
}

data class SqlFormatPreset(
    val keywordCasing: KeywordCasing = KeywordCasing.UPPERCASE,
    val identifierCasing: KeywordCasing = KeywordCasing.UNCHANGED,
    val functionCasing: KeywordCasing = KeywordCasing.UPPERCASE,
    val indentSize: Int = 4,
    val useTabs: Boolean = false,
    val linesBetweenQueries: Int = 2,
    val expandCommaLists: Boolean = true, // Place each column in SELECT on a new line
    val spaceAroundOperators: Boolean = true,
) {
    val indentString: String
        get() = if (useTabs) "\t" else " ".repeat(indentSize)

    fun formatKeyword(keyword: String): String =
        when (keywordCasing) {
            KeywordCasing.UPPERCASE -> keyword.uppercase()
            KeywordCasing.LOWERCASE -> keyword.lowercase()
            KeywordCasing.CAPITALIZED -> keyword.lowercase().replaceFirstChar { it.uppercase() }
            KeywordCasing.UNCHANGED -> keyword
        }

    fun formatIdentifier(
        identifier: String,
        quoted: Boolean = false,
    ): String {
        if (quoted) return identifier
        return when (identifierCasing) {
            KeywordCasing.UPPERCASE -> identifier.uppercase()
            KeywordCasing.LOWERCASE -> identifier.lowercase()
            KeywordCasing.CAPITALIZED -> identifier.lowercase().replaceFirstChar { it.uppercase() }
            KeywordCasing.UNCHANGED -> identifier
        }
    }

    fun formatFunction(functionName: String): String =
        when (functionCasing) {
            KeywordCasing.UPPERCASE -> functionName.uppercase()
            KeywordCasing.LOWERCASE -> functionName.lowercase()
            KeywordCasing.CAPITALIZED -> functionName.lowercase().replaceFirstChar { it.uppercase() }
            KeywordCasing.UNCHANGED -> functionName
        }
}
