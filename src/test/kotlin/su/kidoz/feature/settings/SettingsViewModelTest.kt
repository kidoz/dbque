package su.kidoz.feature.settings

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for SettingsState and related enums.
 * Note: SettingsViewModel integration tests are complex due to SQLDelight dependencies.
 * These unit tests focus on the state classes and enums.
 */
class SettingsStateTest {
    @Test
    fun defaultState_hasExpectedValues() {
        val state = SettingsState()

        assertFalse(state.isLoading)
        assertFalse(state.dialogVisible)
        assertEquals(SettingsTab.EDITOR, state.activeTab)
        assertEquals(14, state.fontSize)
        assertEquals(4, state.tabSize)
        assertFalse(state.wordWrap)
        assertTrue(state.lineNumbers)
        assertTrue(state.highlightCurrentLine)
        assertTrue(state.autoComplete)
        assertEquals(1000, state.maxResultRows)
        assertEquals("NULL", state.nullDisplayText)
        assertEquals("yyyy-MM-dd HH:mm:ss", state.dateTimeFormat)
        assertEquals(30, state.connectionTimeout)
        assertEquals(60, state.queryTimeout)
        assertEquals(ThemeMode.SYSTEM, state.theme)
    }

    @Test
    fun state_copyUpdatesFontSize() {
        val state = SettingsState()
        val updated = state.copy(fontSize = 18)

        assertEquals(18, updated.fontSize)
        assertEquals(14, state.fontSize) // Original unchanged
    }

    @Test
    fun state_copyUpdatesDialogVisible() {
        val state = SettingsState()
        val updated = state.copy(dialogVisible = true)

        assertTrue(updated.dialogVisible)
        assertFalse(state.dialogVisible)
    }

    @Test
    fun state_copyUpdatesActiveTab() {
        val state = SettingsState()
        val updated = state.copy(activeTab = SettingsTab.APPEARANCE)

        assertEquals(SettingsTab.APPEARANCE, updated.activeTab)
        assertEquals(SettingsTab.EDITOR, state.activeTab)
    }

    @Test
    fun state_copyUpdatesTheme() {
        val state = SettingsState()
        val updated = state.copy(theme = ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, updated.theme)
        assertEquals(ThemeMode.SYSTEM, state.theme)
    }

    @Test
    fun state_copyUpdatesMultipleFields() {
        val state = SettingsState()
        val updated =
            state.copy(
                fontSize = 20,
                tabSize = 2,
                wordWrap = true,
                theme = ThemeMode.LIGHT,
            )

        assertEquals(20, updated.fontSize)
        assertEquals(2, updated.tabSize)
        assertTrue(updated.wordWrap)
        assertEquals(ThemeMode.LIGHT, updated.theme)
    }

    @Test
    fun state_copyPreservesUnchangedFields() {
        val state =
            SettingsState(
                fontSize = 16,
                tabSize = 2,
                wordWrap = true,
            )
        val updated = state.copy(fontSize = 18)

        assertEquals(2, updated.tabSize)
        assertTrue(updated.wordWrap)
    }
}

class ThemeModeTest {
    @Test
    fun themeMode_hasCorrectDisplayNames() {
        assertEquals("Light", ThemeMode.LIGHT.displayName)
        assertEquals("Dark", ThemeMode.DARK.displayName)
        assertEquals("System", ThemeMode.SYSTEM.displayName)
    }

    @Test
    fun themeMode_hasThreeEntries() {
        assertEquals(3, ThemeMode.entries.size)
    }

    @Test
    fun themeMode_valuesAreUnique() {
        val names = ThemeMode.entries.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun themeMode_displayNamesAreUnique() {
        val displayNames = ThemeMode.entries.map { it.displayName }
        assertEquals(displayNames.size, displayNames.toSet().size)
    }
}

class SettingsTabTest {
    @Test
    fun settingsTab_hasFourEntries() {
        assertEquals(4, SettingsTab.entries.size)
    }

    @Test
    fun settingsTab_containsExpectedValues() {
        val tabs = SettingsTab.entries
        assertTrue(tabs.contains(SettingsTab.EDITOR))
        assertTrue(tabs.contains(SettingsTab.RESULTS))
        assertTrue(tabs.contains(SettingsTab.CONNECTION))
        assertTrue(tabs.contains(SettingsTab.APPEARANCE))
    }

    @Test
    fun settingsTab_valuesAreUnique() {
        val names = SettingsTab.entries.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }
}

class SettingsEventTest {
    @Test
    fun showDialog_isSingleton() {
        val event1 = SettingsEvent.ShowDialog
        val event2 = SettingsEvent.ShowDialog

        assertEquals(event1, event2)
    }

    @Test
    fun hideDialog_isSingleton() {
        val event1 = SettingsEvent.HideDialog
        val event2 = SettingsEvent.HideDialog

        assertEquals(event1, event2)
    }

    @Test
    fun selectTab_storesTab() {
        val event = SettingsEvent.SelectTab(SettingsTab.APPEARANCE)

        assertEquals(SettingsTab.APPEARANCE, event.tab)
    }

    @Test
    fun updateFontSize_storesSize() {
        val event = SettingsEvent.UpdateFontSize(18)

        assertEquals(18, event.size)
    }

    @Test
    fun updateTabSize_storesSize() {
        val event = SettingsEvent.UpdateTabSize(2)

        assertEquals(2, event.size)
    }

    @Test
    fun updateWordWrap_storesEnabled() {
        val event = SettingsEvent.UpdateWordWrap(true)

        assertTrue(event.enabled)
    }

    @Test
    fun updateTheme_storesTheme() {
        val event = SettingsEvent.UpdateTheme(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, event.theme)
    }

    @Test
    fun updateMaxResultRows_storesRows() {
        val event = SettingsEvent.UpdateMaxResultRows(500)

        assertEquals(500, event.rows)
    }

    @Test
    fun updateNullDisplayText_storesText() {
        val event = SettingsEvent.UpdateNullDisplayText("<null>")

        assertEquals("<null>", event.text)
    }

    @Test
    fun updateDateTimeFormat_storesFormat() {
        val event = SettingsEvent.UpdateDateTimeFormat("dd/MM/yyyy")

        assertEquals("dd/MM/yyyy", event.format)
    }
}

class SettingsEffectTest {
    @Test
    fun settingsSaved_isSingleton() {
        val effect1 = SettingsEffect.SettingsSaved
        val effect2 = SettingsEffect.SettingsSaved

        assertEquals(effect1, effect2)
    }

    @Test
    fun settingsReset_isSingleton() {
        val effect1 = SettingsEffect.SettingsReset
        val effect2 = SettingsEffect.SettingsReset

        assertEquals(effect1, effect2)
    }

    @Test
    fun showError_storesMessage() {
        val effect = SettingsEffect.ShowError("Error message")

        assertEquals("Error message", effect.message)
    }

    @Test
    fun showError_handlesEmptyMessage() {
        val effect = SettingsEffect.ShowError("")

        assertEquals("", effect.message)
    }
}
