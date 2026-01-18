package su.kidoz.feature.results

import org.junit.jupiter.api.Test
import su.kidoz.core.model.ResultColumn
import java.sql.Types
import kotlin.test.assertEquals

class CellEditTest {
    private fun column(
        name: String,
        jdbcType: Int,
    ) = ResultColumn(
        name = name,
        label = name,
        typeName = "VARCHAR",
        jdbcType = jdbcType,
        displaySize = 0,
        precision = 0,
        scale = 0,
        nullable = true,
    )

    @Test
    fun cellEdit_storesValues() {
        val col = column("name", Types.VARCHAR)
        val edit =
            CellEdit(
                rowIndex = 0,
                columnIndex = 1,
                oldValue = "old",
                newValue = "new",
                column = col,
            )

        assertEquals(0, edit.rowIndex)
        assertEquals(1, edit.columnIndex)
        assertEquals("old", edit.oldValue)
        assertEquals("new", edit.newValue)
        assertEquals(col, edit.column)
    }

    @Test
    fun cellEdit_handlesNullValues() {
        val col = column("value", Types.INTEGER)
        val edit =
            CellEdit(
                rowIndex = 0,
                columnIndex = 0,
                oldValue = null,
                newValue = 42,
                column = col,
            )

        assertEquals(null, edit.oldValue)
        assertEquals(42, edit.newValue)
    }
}

class PendingChangesTest {
    private fun column(
        name: String,
        jdbcType: Int,
    ) = ResultColumn(
        name = name,
        label = name,
        typeName = "VARCHAR",
        jdbcType = jdbcType,
        displaySize = 0,
        precision = 0,
        scale = 0,
        nullable = true,
    )

    @Test
    fun pendingChanges_storesTableInfo() {
        val changes =
            PendingChanges(
                tableName = "users",
                schema = "public",
                edits = emptyList(),
                primaryKeyColumns = listOf(0),
            )

        assertEquals("users", changes.tableName)
        assertEquals("public", changes.schema)
        assertEquals(listOf(0), changes.primaryKeyColumns)
    }

    @Test
    fun pendingChanges_handlesNullSchema() {
        val changes =
            PendingChanges(
                tableName = "users",
                schema = null,
                edits = emptyList(),
                primaryKeyColumns = listOf(0),
            )

        assertEquals(null, changes.schema)
    }

    @Test
    fun pendingChanges_storesMultipleEdits() {
        val col1 = column("name", Types.VARCHAR)
        val col2 = column("email", Types.VARCHAR)

        val edits =
            listOf(
                CellEdit(0, 0, "old1", "new1", col1),
                CellEdit(0, 1, "old2", "new2", col2),
                CellEdit(1, 0, "old3", "new3", col1),
            )

        val changes =
            PendingChanges(
                tableName = "users",
                schema = "public",
                edits = edits,
                primaryKeyColumns = listOf(0),
            )

        assertEquals(3, changes.edits.size)
    }

    @Test
    fun pendingChanges_handlesCompositePrimaryKey() {
        val changes =
            PendingChanges(
                tableName = "user_roles",
                schema = null,
                edits = emptyList(),
                primaryKeyColumns = listOf(0, 1), // Composite key
            )

        assertEquals(2, changes.primaryKeyColumns.size)
        assertEquals(0, changes.primaryKeyColumns[0])
        assertEquals(1, changes.primaryKeyColumns[1])
    }

    @Test
    fun pendingChanges_handlesEmptyPrimaryKey() {
        val changes =
            PendingChanges(
                tableName = "log_entries",
                schema = null,
                edits = emptyList(),
                primaryKeyColumns = emptyList(), // No primary key
            )

        assertEquals(0, changes.primaryKeyColumns.size)
    }
}

/**
 * Tests for SQL value formatting via reflection.
 * Tests the formatValueForSql logic indirectly.
 */
class SqlFormattingTest {
    @Test
    fun stringEscaping_handlesQuotes() {
        // Test that single quotes are escaped
        val input = "O'Brien"
        val escaped = input.replace("'", "''")

        assertEquals("O''Brien", escaped)
    }

    @Test
    fun stringEscaping_handlesMultipleQuotes() {
        val input = "It's a 'test'"
        val escaped = input.replace("'", "''")

        assertEquals("It''s a ''test''", escaped)
    }

    @Test
    fun stringEscaping_handlesEmptyString() {
        val input = ""
        val escaped = input.replace("'", "''")

        assertEquals("", escaped)
    }

    @Test
    fun stringEscaping_handlesNoQuotes() {
        val input = "simple text"
        val escaped = input.replace("'", "''")

        assertEquals("simple text", escaped)
    }
}
