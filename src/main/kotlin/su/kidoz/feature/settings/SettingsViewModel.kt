package su.kidoz.feature.settings

import kotlinx.coroutines.launch
import su.kidoz.core.repository.SettingsRepository
import su.kidoz.mvi.MviViewModel

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : MviViewModel<SettingsState, SettingsEvent, SettingsEffect>(SettingsState()) {
    init {
        loadSettings()
    }

    override fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.ShowDialog -> showDialog()

            is SettingsEvent.HideDialog -> hideDialog()

            is SettingsEvent.SelectTab -> selectTab(event.tab)

            // Editor settings
            is SettingsEvent.UpdateFontSize -> updateState { copy(fontSize = event.size) }

            is SettingsEvent.UpdateTabSize -> updateState { copy(tabSize = event.size) }

            is SettingsEvent.UpdateWordWrap -> updateState { copy(wordWrap = event.enabled) }

            is SettingsEvent.UpdateLineNumbers -> updateState { copy(lineNumbers = event.enabled) }

            is SettingsEvent.UpdateHighlightCurrentLine -> updateState { copy(highlightCurrentLine = event.enabled) }

            is SettingsEvent.UpdateAutoComplete -> updateState { copy(autoComplete = event.enabled) }

            // Formatting settings
            is SettingsEvent.UpdateFormatKeywordCasing -> updateState { copy(formatKeywordCasing = event.casing) }

            is SettingsEvent.UpdateFormatIdentifierCasing -> updateState { copy(formatIdentifierCasing = event.casing) }

            is SettingsEvent.UpdateFormatIndentSize -> updateState { copy(formatIndentSize = event.size) }

            is SettingsEvent.UpdateFormatUseTabs -> updateState { copy(formatUseTabs = event.useTabs) }

            is SettingsEvent.UpdateFormatExpandCommaLists -> updateState { copy(formatExpandCommaLists = event.expand) }

            is SettingsEvent.UpdateFormatSpaceAroundOperators -> updateState { copy(formatSpaceAroundOperators = event.space) }

            // Results settings
            is SettingsEvent.UpdateMaxResultRows -> updateState { copy(maxResultRows = event.rows) }

            is SettingsEvent.UpdateNullDisplayText -> updateState { copy(nullDisplayText = event.text) }

            is SettingsEvent.UpdateDateTimeFormat -> updateState { copy(dateTimeFormat = event.format) }

            // Connection settings
            is SettingsEvent.UpdateConnectionTimeout -> updateState { copy(connectionTimeout = event.seconds) }

            is SettingsEvent.UpdateQueryTimeout -> updateState { copy(queryTimeout = event.seconds) }

            // Appearance settings
            is SettingsEvent.UpdateTheme -> updateState { copy(theme = event.theme) }

            // Actions
            is SettingsEvent.SaveSettings -> saveSettings()

            is SettingsEvent.ResetToDefaults -> resetToDefaults()
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            updateState { copy(isLoading = true) }
            try {
                val fontSize = settingsRepository.getSetting(SettingsRepository.FONT_SIZE)?.toIntOrNull() ?: 14
                val tabSize = settingsRepository.getSetting(SettingsRepository.TAB_SIZE)?.toIntOrNull() ?: 4
                val wordWrap = settingsRepository.getSetting(SettingsRepository.WORD_WRAP)?.toBoolean() ?: false
                val lineNumbers = settingsRepository.getSetting(SettingsRepository.LINE_NUMBERS)?.toBoolean() ?: true
                val highlightCurrentLine =
                    settingsRepository.getSetting(SettingsRepository.HIGHLIGHT_CURRENT_LINE)?.toBoolean() ?: true
                val autoComplete = settingsRepository.getSetting(SettingsRepository.AUTO_COMPLETE)?.toBoolean() ?: true

                // Formatting settings
                val formatKeywordCasingStr = settingsRepository.getSetting(SettingsRepository.FORMAT_KEYWORD_CASING)
                val formatKeywordCasing =
                    su.kidoz.feature.editor.format.KeywordCasing.entries
                        .find { it.name == formatKeywordCasingStr }
                        ?: su.kidoz.feature.editor.format.KeywordCasing.UPPERCASE

                val formatIdentifierCasingStr = settingsRepository.getSetting(SettingsRepository.FORMAT_IDENTIFIER_CASING)
                val formatIdentifierCasing =
                    su.kidoz.feature.editor.format.KeywordCasing.entries
                        .find { it.name == formatIdentifierCasingStr }
                        ?: su.kidoz.feature.editor.format.KeywordCasing.UNCHANGED

                val formatIndentSize = settingsRepository.getSetting(SettingsRepository.FORMAT_INDENT_SIZE)?.toIntOrNull() ?: 4
                val formatUseTabs = settingsRepository.getSetting(SettingsRepository.FORMAT_USE_TABS)?.toBoolean() ?: false
                val formatExpandCommaLists =
                    settingsRepository.getSetting(SettingsRepository.FORMAT_EXPAND_COMMA_LISTS)?.toBoolean() ?: true
                val formatSpaceAroundOperators =
                    settingsRepository.getSetting(SettingsRepository.FORMAT_SPACE_AROUND_OPERATORS)?.toBoolean() ?: true

                val maxResultRows =
                    settingsRepository.getSetting(SettingsRepository.MAX_RESULT_ROWS)?.toIntOrNull() ?: 1000
                val queryTimeout =
                    settingsRepository.getSetting(SettingsRepository.QUERY_TIMEOUT)?.toIntOrNull() ?: 60
                val themeMode =
                    settingsRepository.getSetting(SettingsRepository.THEME_MODE)?.let {
                        ThemeMode.entries.find { mode -> mode.name == it }
                    } ?: ThemeMode.SYSTEM

                updateState {
                    copy(
                        isLoading = false,
                        fontSize = fontSize,
                        tabSize = tabSize,
                        wordWrap = wordWrap,
                        lineNumbers = lineNumbers,
                        highlightCurrentLine = highlightCurrentLine,
                        autoComplete = autoComplete,
                        formatKeywordCasing = formatKeywordCasing,
                        formatIdentifierCasing = formatIdentifierCasing,
                        formatIndentSize = formatIndentSize,
                        formatUseTabs = formatUseTabs,
                        formatExpandCommaLists = formatExpandCommaLists,
                        formatSpaceAroundOperators = formatSpaceAroundOperators,
                        maxResultRows = maxResultRows,
                        queryTimeout = queryTimeout,
                        theme = themeMode,
                    )
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load settings" }
                updateState { copy(isLoading = false) }
            }
        }
    }

    private fun showDialog() {
        updateState { copy(dialogVisible = true) }
    }

    private fun hideDialog() {
        updateState { copy(dialogVisible = false) }
    }

    private fun selectTab(tab: SettingsTab) {
        updateState { copy(activeTab = tab) }
    }

    private fun saveSettings() {
        viewModelScope.launch {
            try {
                val state = currentState
                settingsRepository.setSetting(SettingsRepository.FONT_SIZE, state.fontSize.toString())
                settingsRepository.setSetting(SettingsRepository.TAB_SIZE, state.tabSize.toString())
                settingsRepository.setSetting(SettingsRepository.WORD_WRAP, state.wordWrap.toString())
                settingsRepository.setSetting(SettingsRepository.LINE_NUMBERS, state.lineNumbers.toString())
                settingsRepository.setSetting(
                    SettingsRepository.HIGHLIGHT_CURRENT_LINE,
                    state.highlightCurrentLine.toString(),
                )
                settingsRepository.setSetting(SettingsRepository.AUTO_COMPLETE, state.autoComplete.toString())

                settingsRepository.setSetting(SettingsRepository.FORMAT_KEYWORD_CASING, state.formatKeywordCasing.name)
                settingsRepository.setSetting(SettingsRepository.FORMAT_IDENTIFIER_CASING, state.formatIdentifierCasing.name)
                settingsRepository.setSetting(SettingsRepository.FORMAT_INDENT_SIZE, state.formatIndentSize.toString())
                settingsRepository.setSetting(SettingsRepository.FORMAT_USE_TABS, state.formatUseTabs.toString())
                settingsRepository.setSetting(SettingsRepository.FORMAT_EXPAND_COMMA_LISTS, state.formatExpandCommaLists.toString())
                settingsRepository.setSetting(SettingsRepository.FORMAT_SPACE_AROUND_OPERATORS, state.formatSpaceAroundOperators.toString())

                settingsRepository.setSetting(SettingsRepository.MAX_RESULT_ROWS, state.maxResultRows.toString())
                settingsRepository.setSetting(SettingsRepository.QUERY_TIMEOUT, state.queryTimeout.toString())
                settingsRepository.setSetting(SettingsRepository.THEME_MODE, state.theme.name)

                sendEffect(SettingsEffect.SettingsSaved)
                hideDialog()
            } catch (e: Exception) {
                logger.error(e) { "Failed to save settings" }
                sendEffect(SettingsEffect.ShowError(e.message ?: "Failed to save settings"))
            }
        }
    }

    private fun resetToDefaults() {
        updateState {
            copy(
                fontSize = 14,
                tabSize = 4,
                wordWrap = false,
                lineNumbers = true,
                highlightCurrentLine = true,
                autoComplete = true,
                formatKeywordCasing = su.kidoz.feature.editor.format.KeywordCasing.UPPERCASE,
                formatIdentifierCasing = su.kidoz.feature.editor.format.KeywordCasing.UNCHANGED,
                formatIndentSize = 4,
                formatUseTabs = false,
                formatExpandCommaLists = true,
                formatSpaceAroundOperators = true,
                maxResultRows = 1000,
                nullDisplayText = "NULL",
                dateTimeFormat = "yyyy-MM-dd HH:mm:ss",
                connectionTimeout = 30,
                queryTimeout = 60,
                theme = ThemeMode.SYSTEM,
            )
        }
        sendEffect(SettingsEffect.SettingsReset)
    }
}
