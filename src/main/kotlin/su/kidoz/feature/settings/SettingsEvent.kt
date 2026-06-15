package su.kidoz.feature.settings

import su.kidoz.mvi.UiEvent

sealed interface SettingsEvent : UiEvent {
    data object ShowDialog : SettingsEvent

    data object HideDialog : SettingsEvent

    data class SelectTab(
        val tab: SettingsTab,
    ) : SettingsEvent

    // Editor settings
    data class UpdateFontSize(
        val size: Int,
    ) : SettingsEvent

    data class UpdateTabSize(
        val size: Int,
    ) : SettingsEvent

    data class UpdateWordWrap(
        val enabled: Boolean,
    ) : SettingsEvent

    data class UpdateLineNumbers(
        val enabled: Boolean,
    ) : SettingsEvent

    data class UpdateHighlightCurrentLine(
        val enabled: Boolean,
    ) : SettingsEvent

    data class UpdateAutoComplete(
        val enabled: Boolean,
    ) : SettingsEvent

    // Formatting settings
    data class UpdateFormatKeywordCasing(
        val casing: su.kidoz.feature.editor.format.KeywordCasing,
    ) : SettingsEvent

    data class UpdateFormatIdentifierCasing(
        val casing: su.kidoz.feature.editor.format.KeywordCasing,
    ) : SettingsEvent

    data class UpdateFormatIndentSize(
        val size: Int,
    ) : SettingsEvent

    data class UpdateFormatUseTabs(
        val useTabs: Boolean,
    ) : SettingsEvent

    data class UpdateFormatExpandCommaLists(
        val expand: Boolean,
    ) : SettingsEvent

    data class UpdateFormatSpaceAroundOperators(
        val space: Boolean,
    ) : SettingsEvent

    // Results settings
    data class UpdateMaxResultRows(
        val rows: Int,
    ) : SettingsEvent

    data class UpdateNullDisplayText(
        val text: String,
    ) : SettingsEvent

    data class UpdateDateTimeFormat(
        val format: String,
    ) : SettingsEvent

    // Connection settings
    data class UpdateConnectionTimeout(
        val seconds: Int,
    ) : SettingsEvent

    data class UpdateQueryTimeout(
        val seconds: Int,
    ) : SettingsEvent

    // Appearance settings
    data class UpdateTheme(
        val theme: ThemeMode,
    ) : SettingsEvent

    // Actions
    data object SaveSettings : SettingsEvent

    data object ResetToDefaults : SettingsEvent
}
