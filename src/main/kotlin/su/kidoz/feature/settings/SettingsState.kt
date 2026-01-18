package su.kidoz.feature.settings

import su.kidoz.mvi.UiState

data class SettingsState(
    val isLoading: Boolean = false,
    val dialogVisible: Boolean = false,
    val activeTab: SettingsTab = SettingsTab.EDITOR,
    // Editor settings
    val fontSize: Int = 14,
    val tabSize: Int = 4,
    val wordWrap: Boolean = false,
    val lineNumbers: Boolean = true,
    val highlightCurrentLine: Boolean = true,
    val autoComplete: Boolean = true,
    // Results settings
    val maxResultRows: Int = 1000,
    val nullDisplayText: String = "NULL",
    val dateTimeFormat: String = "yyyy-MM-dd HH:mm:ss",
    // Connection settings
    val connectionTimeout: Int = 30,
    val queryTimeout: Int = 60,
    // Appearance settings
    val theme: ThemeMode = ThemeMode.SYSTEM,
) : UiState

enum class SettingsTab {
    EDITOR,
    RESULTS,
    CONNECTION,
    APPEARANCE,
}

enum class ThemeMode(
    val displayName: String,
) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System"),
}
