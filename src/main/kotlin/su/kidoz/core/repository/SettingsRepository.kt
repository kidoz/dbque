package su.kidoz.core.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.kidoz.storage.AppDatabase

class SettingsRepository(
    private val database: AppDatabase,
) {
    private val queries = database.appDatabaseQueries

    suspend fun getSetting(key: String): String? =
        withContext(Dispatchers.IO) {
            queries.getSetting(key).executeAsOneOrNull()
        }

    suspend fun setSetting(
        key: String,
        value: String,
    ) = withContext(Dispatchers.IO) {
        queries.setSetting(key, value)
    }

    suspend fun deleteSetting(key: String) =
        withContext(Dispatchers.IO) {
            queries.deleteSetting(key)
        }

    suspend fun getAllSettings(): Map<String, String> =
        withContext(Dispatchers.IO) {
            queries.getAllSettings().executeAsList().associate { it.key to it.value_ }
        }

    // Common settings
    companion object {
        const val THEME_MODE = "theme_mode"
        const val FONT_SIZE = "font_size"
        const val TAB_SIZE = "tab_size"
        const val AUTO_COMPLETE = "auto_complete"
        const val WORD_WRAP = "word_wrap"
        const val LINE_NUMBERS = "line_numbers"
        const val HIGHLIGHT_CURRENT_LINE = "highlight_current_line"
        const val MAX_RESULT_ROWS = "max_result_rows"
        const val QUERY_TIMEOUT = "query_timeout"
    }
}
