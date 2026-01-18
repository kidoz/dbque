package su.kidoz.feature.results

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import su.kidoz.database.export.CsvExporter
import su.kidoz.database.export.JsonExporter
import su.kidoz.database.export.SqlExporter
import su.kidoz.feature.results.ui.EditingCell
import su.kidoz.mvi.MviViewModel
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.JFileChooser

class ResultsViewModel(
    private val dataEditor: DataEditor,
) : MviViewModel<ResultsState, ResultsEvent, ResultsEffect>(ResultsState()) {
    override fun onEvent(event: ResultsEvent) {
        when (event) {
            is ResultsEvent.SetResults -> setResults(event.results)
            is ResultsEvent.ClearResults -> clearResults()
            is ResultsEvent.SelectResultTab -> selectResultTab(event.index)
            is ResultsEvent.SortByColumn -> sortByColumn(event.columnIndex)
            is ResultsEvent.SetFilter -> setFilter(event.text)
            is ResultsEvent.ClearFilter -> clearFilter()
            is ResultsEvent.SelectRow -> selectRow(event.rowIndex, event.addToSelection)
            is ResultsEvent.SelectRows -> selectRows(event.startRow, event.endRow)
            is ResultsEvent.SelectAllRows -> selectAllRows()
            is ResultsEvent.ClearSelection -> clearSelection()
            is ResultsEvent.SelectColumn -> selectColumn(event.columnIndex)
            is ResultsEvent.CopySelectedCells -> copySelectedCells()
            is ResultsEvent.CopySelectedRows -> copySelectedRows()
            is ResultsEvent.CopyColumnValues -> copyColumnValues()
            is ResultsEvent.ShowExportDialog -> showExportDialog()
            is ResultsEvent.HideExportDialog -> hideExportDialog()
            is ResultsEvent.Export -> export(event.format, event.selectedOnly)
            // Edit mode events
            is ResultsEvent.SetEditMode -> setEditMode(event.enabled)
            is ResultsEvent.SetTableInfo -> setTableInfo(event.tableName, event.schemaName, event.primaryKeyColumns)
            is ResultsEvent.StartCellEdit -> startCellEdit(event.rowIndex, event.columnIndex, event.value)
            is ResultsEvent.UpdateCellEdit -> updateCellEdit(event.value)
            is ResultsEvent.CommitCellEdit -> commitCellEdit()
            is ResultsEvent.CancelCellEdit -> cancelCellEdit()
            is ResultsEvent.SaveChanges -> saveChanges()
            is ResultsEvent.DiscardChanges -> discardChanges()
            is ResultsEvent.ShowDeleteConfirmation -> showDeleteConfirmation()
            is ResultsEvent.HideDeleteConfirmation -> hideDeleteConfirmation()
            is ResultsEvent.DeleteSelectedRows -> deleteSelectedRows()
        }
    }

    private fun setResults(results: List<su.kidoz.core.model.QueryResult>) {
        updateState {
            copy(
                results = results,
                activeResultIndex = 0,
                sortColumn = null,
                filterText = "",
                selectedRows = emptySet(),
                selectedColumn = null,
            )
        }
    }

    private fun clearResults() {
        updateState { ResultsState() }
    }

    private fun selectResultTab(index: Int) {
        updateState {
            copy(
                activeResultIndex = index,
                sortColumn = null,
                filterText = "",
                selectedRows = emptySet(),
                selectedColumn = null,
            )
        }
    }

    private fun sortByColumn(columnIndex: Int) {
        updateState {
            copy(
                sortColumn = columnIndex,
                sortAscending = if (sortColumn == columnIndex) !sortAscending else true,
            )
        }
    }

    private fun setFilter(text: String) {
        updateState { copy(filterText = text, selectedRows = emptySet()) }
    }

    private fun clearFilter() {
        updateState { copy(filterText = "", selectedRows = emptySet()) }
    }

    private fun selectRow(
        rowIndex: Int,
        addToSelection: Boolean,
    ) {
        updateState {
            copy(
                selectedRows =
                    if (addToSelection) {
                        if (selectedRows.contains(rowIndex)) {
                            selectedRows - rowIndex
                        } else {
                            selectedRows + rowIndex
                        }
                    } else {
                        setOf(rowIndex)
                    },
                selectedColumn = null,
            )
        }
    }

    private fun selectRows(
        startRow: Int,
        endRow: Int,
    ) {
        val range = (minOf(startRow, endRow)..maxOf(startRow, endRow)).toSet()
        updateState { copy(selectedRows = range, selectedColumn = null) }
    }

    private fun selectAllRows() {
        val rowCount = currentState.filteredRows.size
        updateState { copy(selectedRows = (0 until rowCount).toSet()) }
    }

    private fun clearSelection() {
        updateState { copy(selectedRows = emptySet(), selectedColumn = null) }
    }

    private fun selectColumn(columnIndex: Int) {
        updateState { copy(selectedColumn = columnIndex, selectedRows = emptySet()) }
    }

    private fun copySelectedCells() {
        val result = currentState.activeResult ?: return
        val rows = currentState.filteredRows
        val selectedRows = currentState.selectedRows.sorted()

        if (selectedRows.isEmpty()) {
            sendEffect(ResultsEffect.ShowMessage("No cells selected"))
            return
        }

        val text =
            buildString {
                selectedRows.forEach { rowIndex ->
                    rows.getOrNull(rowIndex)?.let { row ->
                        appendLine(row.joinToString("\t") { it?.toString() ?: "" })
                    }
                }
            }

        copyToClipboard(text)
        sendEffect(ResultsEffect.CopiedToClipboard(selectedRows.size))
    }

    private fun copySelectedRows() {
        val result = currentState.activeResult ?: return
        val rows = currentState.filteredRows
        val selectedRows = currentState.selectedRows.sorted()

        if (selectedRows.isEmpty()) {
            sendEffect(ResultsEffect.ShowMessage("No rows selected"))
            return
        }

        val headers = result.columns.joinToString("\t") { it.label }
        val text =
            buildString {
                appendLine(headers)
                selectedRows.forEach { rowIndex ->
                    rows.getOrNull(rowIndex)?.let { row ->
                        appendLine(row.joinToString("\t") { it?.toString() ?: "" })
                    }
                }
            }

        copyToClipboard(text)
        sendEffect(ResultsEffect.CopiedToClipboard(selectedRows.size))
    }

    private fun copyColumnValues() {
        val column = currentState.selectedColumn ?: return
        val rows = currentState.filteredRows

        val text =
            rows.joinToString("\n") { row ->
                row.getOrNull(column)?.toString() ?: ""
            }

        copyToClipboard(text)
        sendEffect(ResultsEffect.CopiedToClipboard(rows.size))
    }

    private fun copyToClipboard(text: String) {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(text), null)
        } catch (e: Exception) {
            logger.error(e) { "Failed to copy to clipboard" }
            sendEffect(ResultsEffect.ShowError("Failed to copy to clipboard"))
        }
    }

    private fun showExportDialog() {
        updateState { copy(exportDialogVisible = true) }
    }

    private fun hideExportDialog() {
        updateState { copy(exportDialogVisible = false) }
    }

    private fun export(
        format: ExportFormat,
        selectedOnly: Boolean,
    ) {
        val result = currentState.activeResult ?: return
        val rows =
            if (selectedOnly && currentState.selectedRows.isNotEmpty()) {
                currentState.filteredRows.filterIndexed { index, _ ->
                    currentState.selectedRows.contains(index)
                }
            } else {
                currentState.filteredRows
            }

        viewModelScope.launch {
            updateState { copy(isExporting = true, exportDialogVisible = false) }

            try {
                val chooser =
                    JFileChooser().apply {
                        dialogTitle = "Export Results"
                        selectedFile = File("export.${format.extension}")
                    }

                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val file = chooser.selectedFile
                    val content =
                        when (format) {
                            ExportFormat.CSV -> CsvExporter().export(result.columns, rows)
                            ExportFormat.JSON -> JsonExporter().export(result.columns, rows)
                            ExportFormat.SQL_INSERT -> SqlExporter().exportInsert("table_name", result.columns, rows)
                            ExportFormat.SQL_UPDATE -> SqlExporter().exportUpdate("table_name", result.columns, rows, listOf(0))
                        }

                    withContext(Dispatchers.IO) {
                        file.writeText(content)
                    }

                    sendEffect(ResultsEffect.ExportCompleted(file.absolutePath))
                }
            } catch (e: Exception) {
                logger.error(e) { "Export failed" }
                sendEffect(ResultsEffect.ShowError(e.message ?: "Export failed"))
            } finally {
                updateState { copy(isExporting = false) }
            }
        }
    }

    // Edit mode methods
    private fun setEditMode(enabled: Boolean) {
        if (!enabled && currentState.hasPendingChanges) {
            sendEffect(ResultsEffect.ShowMessage("Please save or discard changes first"))
            return
        }
        updateState {
            copy(
                isEditMode = enabled,
                editingCell = null,
                pendingEdits = if (!enabled) emptyMap() else pendingEdits,
            )
        }
    }

    private fun setTableInfo(
        tableName: String,
        schemaName: String?,
        primaryKeyColumns: List<Int>,
    ) {
        updateState {
            copy(
                tableName = tableName,
                schemaName = schemaName,
                primaryKeyColumns = primaryKeyColumns,
            )
        }
    }

    private fun startCellEdit(
        rowIndex: Int,
        columnIndex: Int,
        value: Any?,
    ) {
        if (!currentState.isEditMode) return

        updateState {
            copy(
                editingCell =
                    EditingCell(
                        rowIndex = rowIndex,
                        columnIndex = columnIndex,
                        originalValue = value,
                        currentValue = value?.toString() ?: "",
                    ),
            )
        }
    }

    private fun updateCellEdit(value: String) {
        val editingCell = currentState.editingCell ?: return
        updateState {
            copy(editingCell = editingCell.copy(currentValue = value))
        }
    }

    private fun commitCellEdit() {
        val editingCell = currentState.editingCell ?: return
        val result = currentState.activeResult ?: return
        val column = result.columns.getOrNull(editingCell.columnIndex) ?: return

        // Check if value actually changed
        val originalValueStr = editingCell.originalValue?.toString() ?: ""
        if (editingCell.currentValue == originalValueStr) {
            updateState { copy(editingCell = null) }
            return
        }

        // Parse the new value to the appropriate type
        val newValue: Any? = parseValue(editingCell.currentValue, column.jdbcType)

        val cellKey = editingCell.rowIndex to editingCell.columnIndex
        val cellEdit =
            CellEdit(
                rowIndex = editingCell.rowIndex,
                columnIndex = editingCell.columnIndex,
                oldValue = editingCell.originalValue,
                newValue = newValue,
                column = column,
            )

        updateState {
            copy(
                pendingEdits = pendingEdits + (cellKey to cellEdit),
                editingCell = null,
            )
        }
    }

    private fun cancelCellEdit() {
        updateState { copy(editingCell = null) }
    }

    private fun saveChanges() {
        val tableName = currentState.tableName
        if (tableName == null) {
            sendEffect(ResultsEffect.ShowError("Table name not set - cannot save changes"))
            return
        }

        val result = currentState.activeResult ?: return
        val edits = currentState.pendingEdits.values.toList()

        if (edits.isEmpty()) {
            sendEffect(ResultsEffect.ShowMessage("No changes to save"))
            return
        }

        viewModelScope.launch {
            updateState { copy(isSaving = true) }

            try {
                val changes =
                    PendingChanges(
                        tableName = tableName,
                        schema = currentState.schemaName,
                        edits = edits,
                        primaryKeyColumns = currentState.primaryKeyColumns,
                    )

                val affectedRows = dataEditor.applyChanges(changes, result).getOrThrow()

                updateState {
                    copy(
                        pendingEdits = emptyMap(),
                        isSaving = false,
                    )
                }

                sendEffect(ResultsEffect.ChangesSaved(affectedRows))
                sendEffect(ResultsEffect.RefreshData)
            } catch (e: Exception) {
                logger.error(e) { "Failed to save changes" }
                updateState { copy(isSaving = false) }
                sendEffect(ResultsEffect.ShowError(e.message ?: "Failed to save changes"))
            }
        }
    }

    private fun discardChanges() {
        updateState {
            copy(
                pendingEdits = emptyMap(),
                editingCell = null,
            )
        }
        sendEffect(ResultsEffect.ShowMessage("Changes discarded"))
    }

    private fun showDeleteConfirmation() {
        if (currentState.selectedRows.isEmpty()) {
            sendEffect(ResultsEffect.ShowMessage("No rows selected"))
            return
        }
        updateState { copy(deleteConfirmationVisible = true) }
    }

    private fun hideDeleteConfirmation() {
        updateState { copy(deleteConfirmationVisible = false) }
    }

    private fun deleteSelectedRows() {
        val tableName = currentState.tableName
        if (tableName == null) {
            sendEffect(ResultsEffect.ShowError("Table name not set - cannot delete rows"))
            return
        }

        val result = currentState.activeResult ?: return
        val selectedRows = currentState.selectedRows.sorted()
        val rows = selectedRows.mapNotNull { result.rows.getOrNull(it) }

        if (rows.isEmpty()) {
            sendEffect(ResultsEffect.ShowMessage("No rows to delete"))
            return
        }

        viewModelScope.launch {
            updateState { copy(isSaving = true, deleteConfirmationVisible = false) }

            try {
                val affectedRows =
                    dataEditor
                        .deleteRows(
                            tableName = tableName,
                            schema = currentState.schemaName,
                            columns = result.columns,
                            rows = rows,
                            primaryKeyColumns = currentState.primaryKeyColumns,
                        ).getOrThrow()

                updateState {
                    copy(
                        selectedRows = emptySet(),
                        isSaving = false,
                    )
                }

                sendEffect(ResultsEffect.RowsDeleted(affectedRows))
                sendEffect(ResultsEffect.RefreshData)
            } catch (e: Exception) {
                logger.error(e) { "Failed to delete rows" }
                updateState { copy(isSaving = false) }
                sendEffect(ResultsEffect.ShowError(e.message ?: "Failed to delete rows"))
            }
        }
    }

    private fun parseValue(
        value: String,
        jdbcType: Int,
    ): Any? {
        if (value.isEmpty() || value.equals("NULL", ignoreCase = true)) {
            return null
        }

        return when (jdbcType) {
            java.sql.Types.BOOLEAN, java.sql.Types.BIT ->
                value.equals("true", ignoreCase = true) || value == "1"
            java.sql.Types.TINYINT, java.sql.Types.SMALLINT, java.sql.Types.INTEGER ->
                value.toIntOrNull() ?: value
            java.sql.Types.BIGINT -> value.toLongOrNull() ?: value
            java.sql.Types.FLOAT, java.sql.Types.REAL -> value.toFloatOrNull() ?: value
            java.sql.Types.DOUBLE, java.sql.Types.DECIMAL, java.sql.Types.NUMERIC ->
                value.toDoubleOrNull() ?: value
            else -> value
        }
    }
}
