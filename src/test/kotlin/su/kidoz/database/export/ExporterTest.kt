package su.kidoz.database.export

import org.junit.jupiter.api.Test
import su.kidoz.core.model.ResultColumn
import java.sql.Types
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvExporterTest {
    private val exporter = CsvExporter()

    private fun column(
        name: String,
        typeName: String,
        jdbcType: Int,
    ) = ResultColumn(
        name = name,
        label = name,
        typeName = typeName,
        jdbcType = jdbcType,
        displaySize = 0,
        precision = 0,
        scale = 0,
        nullable = true,
    )

    @Test
    fun export_simpleData_succeeds() {
        val columns =
            listOf(
                column("id", "INT", Types.INTEGER),
                column("name", "VARCHAR", Types.VARCHAR),
            )
        val rows =
            listOf(
                listOf(1, "John"),
                listOf(2, "Jane"),
            )

        val result = exporter.export(columns, rows)

        assertTrue(result.contains("id,name"))
        assertTrue(result.contains("1,John"))
        assertTrue(result.contains("2,Jane"))
    }

    @Test
    fun export_withComma_escapesCorrectly() {
        val columns = listOf(column("name", "VARCHAR", Types.VARCHAR))
        val rows = listOf(listOf("John, Jr."))

        val result = exporter.export(columns, rows)

        assertTrue(result.contains("\"John, Jr.\""))
    }

    @Test
    fun export_withQuotes_escapesCorrectly() {
        val columns = listOf(column("quote", "VARCHAR", Types.VARCHAR))
        val rows = listOf(listOf("He said \"hello\""))

        val result = exporter.export(columns, rows)

        assertTrue(result.contains("\"He said \"\"hello\"\"\""))
    }

    @Test
    fun export_withNewline_escapesCorrectly() {
        val columns = listOf(column("text", "VARCHAR", Types.VARCHAR))
        val rows = listOf(listOf("line1\nline2"))

        val result = exporter.export(columns, rows)

        assertTrue(result.contains("\"line1\nline2\""))
    }

    @Test
    fun export_withNull_handlesCorrectly() {
        val columns = listOf(column("value", "VARCHAR", Types.VARCHAR))
        val rows = listOf(listOf<Any?>(null))

        val result = exporter.export(columns, rows)

        val lines = result.lines()
        assertEquals("value", lines[0])
        assertEquals("", lines[1])
    }

    @Test
    fun export_emptyRows_returnsHeaderOnly() {
        val columns = listOf(column("id", "INT", Types.INTEGER))
        val rows = emptyList<List<Any?>>()

        val result = exporter.export(columns, rows)

        assertEquals("id\n", result)
    }
}

class JsonExporterTest {
    private val exporter = JsonExporter()

    private fun column(
        name: String,
        typeName: String,
        jdbcType: Int,
    ) = ResultColumn(
        name = name,
        label = name,
        typeName = typeName,
        jdbcType = jdbcType,
        displaySize = 0,
        precision = 0,
        scale = 0,
        nullable = true,
    )

    @Test
    fun export_simpleData_succeeds() {
        val columns =
            listOf(
                column("id", "INT", Types.INTEGER),
                column("name", "VARCHAR", Types.VARCHAR),
            )
        val rows =
            listOf(
                listOf(1, "John"),
                listOf(2, "Jane"),
            )

        val result = exporter.export(columns, rows)

        assertTrue(result.contains("\"id\": 1"))
        assertTrue(result.contains("\"name\": \"John\""))
        assertTrue(result.contains("\"id\": 2"))
        assertTrue(result.contains("\"name\": \"Jane\""))
    }

    @Test
    fun export_withNull_handlesCorrectly() {
        val columns = listOf(column("value", "VARCHAR", Types.VARCHAR))
        val rows = listOf(listOf<Any?>(null))

        val result = exporter.export(columns, rows)

        assertTrue(result.contains("\"value\": null"))
    }

    @Test
    fun export_withBoolean_handlesCorrectly() {
        val columns = listOf(column("active", "BOOLEAN", Types.BOOLEAN))
        val rows = listOf(listOf(true), listOf(false))

        val result = exporter.export(columns, rows)

        assertTrue(result.contains("\"active\": true"))
        assertTrue(result.contains("\"active\": false"))
    }

    @Test
    fun export_withNumber_handlesCorrectly() {
        val columns =
            listOf(
                column("int_val", "INT", Types.INTEGER),
                column("double_val", "DOUBLE", Types.DOUBLE),
            )
        val rows = listOf(listOf(42, 3.14))

        val result = exporter.export(columns, rows)

        assertTrue(result.contains("\"int_val\": 42"))
        assertTrue(result.contains("\"double_val\": 3.14"))
    }

    @Test
    fun export_emptyRows_returnsEmptyArray() {
        val columns = listOf(column("id", "INT", Types.INTEGER))
        val rows = emptyList<List<Any?>>()

        val result = exporter.export(columns, rows)

        assertTrue(result.contains("["))
        assertTrue(result.contains("]"))
    }
}

class SqlExporterTest {
    private val exporter = SqlExporter()

    private fun column(
        name: String,
        typeName: String,
        jdbcType: Int,
    ) = ResultColumn(
        name = name,
        label = name,
        typeName = typeName,
        jdbcType = jdbcType,
        displaySize = 0,
        precision = 0,
        scale = 0,
        nullable = true,
    )

    @Test
    fun exportInsert_simpleData_succeeds() {
        val columns =
            listOf(
                column("id", "INT", Types.INTEGER),
                column("name", "VARCHAR", Types.VARCHAR),
            )
        val rows = listOf(listOf(1, "John"))

        val result = exporter.exportInsert("users", columns, rows)

        assertTrue(result.contains("INSERT INTO \"users\""))
        assertTrue(result.contains("(\"id\", \"name\")"))
        assertTrue(result.contains("VALUES (1, 'John')"))
    }

    @Test
    fun exportInsert_withNull_handlesCorrectly() {
        val columns = listOf(column("name", "VARCHAR", Types.VARCHAR))
        val rows = listOf(listOf<Any?>(null))

        val result = exporter.exportInsert("users", columns, rows)

        assertTrue(result.contains("VALUES (NULL)"))
    }

    @Test
    fun exportInsert_withBoolean_handlesCorrectly() {
        val columns = listOf(column("active", "BOOLEAN", Types.BOOLEAN))
        val rows = listOf(listOf(true), listOf(false))

        val result = exporter.exportInsert("users", columns, rows)

        assertTrue(result.contains("VALUES (TRUE)"))
        assertTrue(result.contains("VALUES (FALSE)"))
    }

    @Test
    fun exportInsert_withQuotes_escapesCorrectly() {
        val columns = listOf(column("name", "VARCHAR", Types.VARCHAR))
        val rows = listOf(listOf("O'Brien"))

        val result = exporter.exportInsert("users", columns, rows)

        assertTrue(result.contains("'O''Brien'"))
    }

    @Test
    fun exportInsert_multipleRows_generatesMultipleStatements() {
        val columns = listOf(column("id", "INT", Types.INTEGER))
        val rows = listOf(listOf(1), listOf(2), listOf(3))

        val result = exporter.exportInsert("users", columns, rows)

        assertEquals(3, result.count { it == ';' })
    }

    @Test
    fun exportUpdate_simpleData_succeeds() {
        val columns =
            listOf(
                column("id", "INT", Types.INTEGER),
                column("name", "VARCHAR", Types.VARCHAR),
            )
        val rows = listOf(listOf(1, "John"))

        val result = exporter.exportUpdate("users", columns, rows, listOf(0))

        assertTrue(result.contains("UPDATE \"users\""))
        assertTrue(result.contains("SET \"name\" = 'John'"))
        assertTrue(result.contains("WHERE \"id\" = 1"))
    }

    @Test
    fun exportUpdate_multipleKeyColumns_succeeds() {
        val columns =
            listOf(
                column("org_id", "INT", Types.INTEGER),
                column("user_id", "INT", Types.INTEGER),
                column("role", "VARCHAR", Types.VARCHAR),
            )
        val rows = listOf(listOf(1, 2, "admin"))

        val result = exporter.exportUpdate("user_roles", columns, rows, listOf(0, 1))

        assertTrue(result.contains("WHERE \"org_id\" = 1 AND \"user_id\" = 2"))
    }
}
