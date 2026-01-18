package su.kidoz.feature.results

import org.junit.jupiter.api.Test
import su.kidoz.core.model.QueryResult
import su.kidoz.core.model.ResultColumn
import su.kidoz.feature.results.ui.EditingCell
import java.sql.Types
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for ResultsState and related classes.
 * Note: Full ViewModel tests would require mocking DataEditor.
 * These tests focus on the state classes and their behavior.
 */
class ResultsStateTest {
    private fun createTestColumn(name: String) =
        ResultColumn(
            name = name,
            label = name,
            typeName = "VARCHAR",
            jdbcType = Types.VARCHAR,
            displaySize = 100,
            precision = 0,
            scale = 0,
            nullable = true,
        )

    private fun createTestResult() =
        QueryResult(
            columns = listOf(createTestColumn("id"), createTestColumn("name")),
            rows = listOf(listOf(1, "Alice"), listOf(2, "Bob"), listOf(3, "Charlie")),
            rowCount = 3,
            executionTimeMs = 100L,
            query = "SELECT * FROM users",
            isResultSet = true,
        )

    @Test
    fun defaultState_hasExpectedValues() {
        val state = ResultsState()

        assertTrue(state.results.isEmpty())
        assertEquals(0, state.activeResultIndex)
        assertNull(state.sortColumn)
        assertTrue(state.sortAscending)
        assertEquals("", state.filterText)
        assertTrue(state.selectedRows.isEmpty())
        assertNull(state.selectedColumn)
        assertFalse(state.isExporting)
        assertFalse(state.exportDialogVisible)
        assertFalse(state.isEditMode)
        assertNull(state.editingCell)
        assertTrue(state.pendingEdits.isEmpty())
        assertFalse(state.isSaving)
        assertFalse(state.deleteConfirmationVisible)
        assertNull(state.tableName)
        assertNull(state.schemaName)
        assertTrue(state.primaryKeyColumns.isEmpty())
    }

    @Test
    fun activeResult_returnsNullWhenEmpty() {
        val state = ResultsState()

        assertNull(state.activeResult)
    }

    @Test
    fun activeResult_returnsCorrectResult() {
        val result1 = createTestResult()
        val result2 = createTestResult().copy(rowCount = 5)
        val state = ResultsState(results = listOf(result1, result2), activeResultIndex = 1)

        assertEquals(result2, state.activeResult)
    }

    @Test
    fun hasPendingChanges_falseWhenEmpty() {
        val state = ResultsState()

        assertFalse(state.hasPendingChanges)
    }

    @Test
    fun hasPendingChanges_trueWhenEditsExist() {
        val col = createTestColumn("name")
        val edit = CellEdit(0, 0, "old", "new", col)
        val state = ResultsState(pendingEdits = mapOf((0 to 0) to edit))

        assertTrue(state.hasPendingChanges)
    }

    @Test
    fun filteredRows_returnsAllRowsWhenNoFilter() {
        val result = createTestResult()
        val state = ResultsState(results = listOf(result))

        assertEquals(3, state.filteredRows.size)
    }

    @Test
    fun filteredRows_appliesFilter() {
        val result = createTestResult()
        val state = ResultsState(results = listOf(result), filterText = "Alice")

        assertEquals(1, state.filteredRows.size)
        assertEquals("Alice", state.filteredRows[0][1])
    }

    @Test
    fun filteredRows_filterIsCaseInsensitive() {
        val result = createTestResult()
        val state = ResultsState(results = listOf(result), filterText = "alice")

        assertEquals(1, state.filteredRows.size)
    }

    @Test
    fun filteredRows_appliesSorting() {
        val result = createTestResult()
        val state = ResultsState(results = listOf(result), sortColumn = 1, sortAscending = true)

        val names = state.filteredRows.map { it[1] as String }
        assertEquals(listOf("Alice", "Bob", "Charlie"), names)
    }

    @Test
    fun filteredRows_appliesSortingDescending() {
        val result = createTestResult()
        val state = ResultsState(results = listOf(result), sortColumn = 1, sortAscending = false)

        val names = state.filteredRows.map { it[1] as String }
        assertEquals(listOf("Charlie", "Bob", "Alice"), names)
    }

    @Test
    fun filteredRows_appliesFilterAndSorting() {
        val columns = listOf(createTestColumn("id"), createTestColumn("name"))
        val rows =
            listOf(
                listOf(1, "Alice"),
                listOf(2, "Bob"),
                listOf(3, "Alberto"),
                listOf(4, "Aaron"),
            )
        val result =
            QueryResult(
                columns = columns,
                rows = rows,
                rowCount = 4,
                executionTimeMs = 100L,
                query = "SELECT * FROM users",
                isResultSet = true,
            )
        val state = ResultsState(results = listOf(result), filterText = "A", sortColumn = 1, sortAscending = true)

        val names = state.filteredRows.map { it[1] as String }
        assertEquals(listOf("Aaron", "Alberto", "Alice"), names)
    }

    @Test
    fun state_copyPreservesUnchangedFields() {
        val state =
            ResultsState(
                filterText = "test",
                sortColumn = 1,
                sortAscending = false,
            )
        val updated = state.copy(isEditMode = true)

        assertEquals("test", updated.filterText)
        assertEquals(1, updated.sortColumn)
        assertFalse(updated.sortAscending)
        assertTrue(updated.isEditMode)
    }
}

class EditingCellTest {
    @Test
    fun editingCell_storesValues() {
        val cell =
            EditingCell(
                rowIndex = 1,
                columnIndex = 2,
                originalValue = "original",
                currentValue = "current",
            )

        assertEquals(1, cell.rowIndex)
        assertEquals(2, cell.columnIndex)
        assertEquals("original", cell.originalValue)
        assertEquals("current", cell.currentValue)
    }

    @Test
    fun editingCell_handlesNullOriginalValue() {
        val cell =
            EditingCell(
                rowIndex = 0,
                columnIndex = 0,
                originalValue = null,
                currentValue = "new",
            )

        assertNull(cell.originalValue)
        assertEquals("new", cell.currentValue)
    }
}

class ExportFormatTest {
    @Test
    fun csv_hasCorrectProperties() {
        assertEquals("CSV", ExportFormat.CSV.displayName)
        assertEquals("csv", ExportFormat.CSV.extension)
    }

    @Test
    fun json_hasCorrectProperties() {
        assertEquals("JSON", ExportFormat.JSON.displayName)
        assertEquals("json", ExportFormat.JSON.extension)
    }

    @Test
    fun sqlInsert_hasCorrectProperties() {
        assertEquals("SQL INSERT", ExportFormat.SQL_INSERT.displayName)
        assertEquals("sql", ExportFormat.SQL_INSERT.extension)
    }

    @Test
    fun sqlUpdate_hasCorrectProperties() {
        assertEquals("SQL UPDATE", ExportFormat.SQL_UPDATE.displayName)
        assertEquals("sql", ExportFormat.SQL_UPDATE.extension)
    }

    @Test
    fun exportFormat_hasFourEntries() {
        assertEquals(4, ExportFormat.entries.size)
    }
}

class ResultsEventTest {
    @Test
    fun setResults_storesResults() {
        val result =
            QueryResult(
                columns = emptyList(),
                rows = emptyList(),
                rowCount = 0,
                executionTimeMs = 100L,
                query = "SELECT 1",
                isResultSet = true,
            )
        val event = ResultsEvent.SetResults(listOf(result))

        assertEquals(1, event.results.size)
    }

    @Test
    fun clearResults_isSingleton() {
        val event1 = ResultsEvent.ClearResults
        val event2 = ResultsEvent.ClearResults

        assertEquals(event1, event2)
    }

    @Test
    fun selectResultTab_storesIndex() {
        val event = ResultsEvent.SelectResultTab(2)

        assertEquals(2, event.index)
    }

    @Test
    fun sortByColumn_storesColumnIndex() {
        val event = ResultsEvent.SortByColumn(3)

        assertEquals(3, event.columnIndex)
    }

    @Test
    fun setFilter_storesText() {
        val event = ResultsEvent.SetFilter("search text")

        assertEquals("search text", event.text)
    }

    @Test
    fun selectRow_storesRowAndFlag() {
        val event = ResultsEvent.SelectRow(5, true)

        assertEquals(5, event.rowIndex)
        assertTrue(event.addToSelection)
    }

    @Test
    fun selectRows_storesRange() {
        val event = ResultsEvent.SelectRows(2, 7)

        assertEquals(2, event.startRow)
        assertEquals(7, event.endRow)
    }

    @Test
    fun export_storesFormatAndFlag() {
        val event = ResultsEvent.Export(ExportFormat.JSON, true)

        assertEquals(ExportFormat.JSON, event.format)
        assertTrue(event.selectedOnly)
    }

    @Test
    fun setEditMode_storesEnabled() {
        val event = ResultsEvent.SetEditMode(true)

        assertTrue(event.enabled)
    }

    @Test
    fun setTableInfo_storesAllFields() {
        val event = ResultsEvent.SetTableInfo("users", "public", listOf(0, 1))

        assertEquals("users", event.tableName)
        assertEquals("public", event.schemaName)
        assertEquals(listOf(0, 1), event.primaryKeyColumns)
    }

    @Test
    fun startCellEdit_storesAllFields() {
        val event = ResultsEvent.StartCellEdit(1, 2, "value")

        assertEquals(1, event.rowIndex)
        assertEquals(2, event.columnIndex)
        assertEquals("value", event.value)
    }

    @Test
    fun updateCellEdit_storesValue() {
        val event = ResultsEvent.UpdateCellEdit("new value")

        assertEquals("new value", event.value)
    }
}

class ResultsEffectTest {
    @Test
    fun copiedToClipboard_storesRowCount() {
        val effect = ResultsEffect.CopiedToClipboard(10)

        assertEquals(10, effect.rowCount)
    }

    @Test
    fun exportCompleted_storesFilePath() {
        val effect = ResultsEffect.ExportCompleted("/path/to/file.csv")

        assertEquals("/path/to/file.csv", effect.filePath)
    }

    @Test
    fun showError_storesMessage() {
        val effect = ResultsEffect.ShowError("Error occurred")

        assertEquals("Error occurred", effect.message)
    }

    @Test
    fun showMessage_storesMessage() {
        val effect = ResultsEffect.ShowMessage("Info message")

        assertEquals("Info message", effect.message)
    }

    @Test
    fun changesSaved_storesRowsAffected() {
        val effect = ResultsEffect.ChangesSaved(5)

        assertEquals(5, effect.rowsAffected)
    }

    @Test
    fun rowsDeleted_storesRowCount() {
        val effect = ResultsEffect.RowsDeleted(3)

        assertEquals(3, effect.rowCount)
    }

    @Test
    fun refreshData_isSingleton() {
        val effect1 = ResultsEffect.RefreshData
        val effect2 = ResultsEffect.RefreshData

        assertEquals(effect1, effect2)
    }
}

class QueryResultTest {
    @Test
    fun queryResult_storesAllFields() {
        val columns =
            listOf(
                ResultColumn("id", "id", "INTEGER", Types.INTEGER, 10, 0, 0, false),
                ResultColumn("name", "name", "VARCHAR", Types.VARCHAR, 100, 0, 0, true),
            )
        val rows = listOf(listOf(1, "Alice"), listOf(2, "Bob"))
        val result =
            QueryResult(
                columns = columns,
                rows = rows,
                rowCount = 2,
                executionTimeMs = 150L,
                query = "SELECT * FROM users",
                isResultSet = true,
            )

        assertEquals(2, result.columns.size)
        assertEquals(2, result.rows.size)
        assertEquals(2, result.rowCount)
        assertEquals(150L, result.executionTimeMs)
        assertTrue(result.isResultSet)
    }

    @Test
    fun queryResult_handlesDmlResult() {
        val result =
            QueryResult(
                columns = emptyList(),
                rows = emptyList(),
                rowCount = 0,
                executionTimeMs = 50L,
                query = "DELETE FROM users WHERE id = 1",
                isResultSet = false,
                affectedRows = 5,
            )

        assertFalse(result.isResultSet)
        assertEquals(5, result.affectedRows)
    }
}
