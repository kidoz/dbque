package su.kidoz.feature.parser.sql

import org.junit.jupiter.api.Test
import su.kidoz.feature.parser.ast.BinaryExpression
import su.kidoz.feature.parser.ast.DeleteStatement
import su.kidoz.feature.parser.ast.InsertStatement
import su.kidoz.feature.parser.ast.NumberLiteral
import su.kidoz.feature.parser.ast.SelectStatement
import su.kidoz.feature.parser.ast.StringLiteral
import su.kidoz.feature.parser.ast.UpdateStatement
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlParserTest {
    @Test
    fun parse_simpleSelect_succeeds() {
        val result = SqlParser.parse("SELECT id, name FROM users")

        assertTrue(result.isSuccess)
        val stmt = result.getOrNull()
        assertIs<SelectStatement>(stmt)
        assertEquals(2, stmt.columns.size)
        assertNotNull(stmt.from)
    }

    @Test
    fun parse_selectWithWhere_succeeds() {
        val result = SqlParser.parse("SELECT * FROM users WHERE id = 1")

        assertTrue(result.isSuccess)
        val stmt = result.getOrNull() as SelectStatement
        assertNotNull(stmt.where)
        assertIs<BinaryExpression>(stmt.where)
        assertEquals("=", stmt.where.operator)
    }

    @Test
    fun parse_selectWithGroupBy_succeeds() {
        val result = SqlParser.parse("SELECT department FROM employees GROUP BY department")

        assertTrue(result.isSuccess)
        val stmt = result.getOrNull() as SelectStatement
        assertEquals(1, stmt.groupBy.size)
    }

    @Test
    fun parse_selectWithLimit_succeeds() {
        val result = SqlParser.parse("SELECT * FROM users LIMIT 10 OFFSET 5")

        assertTrue(result.isSuccess)
        val stmt = result.getOrNull() as SelectStatement
        assertNotNull(stmt.limit)
        assertNotNull(stmt.offset)
    }

    @Test
    fun parse_selectWithAlias_succeeds() {
        val result = SqlParser.parse("SELECT name AS user_name FROM users")

        assertTrue(result.isSuccess)
        val stmt = result.getOrNull() as SelectStatement
        assertNotNull(stmt.columns[0].alias)
        assertEquals("user_name", stmt.columns[0].alias?.name)
    }

    @Test
    fun parse_insertStatement_succeeds() {
        val result =
            SqlParser.parse(
                "INSERT INTO users (name, email) VALUES ('John', 'john@example.com')",
            )

        assertTrue(result.isSuccess)
        val stmt = result.getOrNull()
        assertIs<InsertStatement>(stmt)
        assertEquals("users", stmt.table.fullName)
        assertEquals(2, stmt.columns?.size)
        assertEquals(1, stmt.values?.size)
    }

    @Test
    fun parse_insertWithMultipleRows_succeeds() {
        val result =
            SqlParser.parse(
                "INSERT INTO users (name) VALUES ('John'), ('Jane'), ('Bob')",
            )

        assertTrue(result.isSuccess)
        val stmt = result.getOrNull() as InsertStatement
        assertEquals(3, stmt.values?.size)
    }

    @Test
    fun parse_updateStatement_succeeds() {
        val result = SqlParser.parse("UPDATE users SET name = 'John' WHERE id = 1")

        assertTrue(result.isSuccess)
        val stmt = result.getOrNull()
        assertIs<UpdateStatement>(stmt)
        assertEquals(1, stmt.assignments.size)
        assertNotNull(stmt.where)
    }

    @Test
    fun parse_updateWithoutWhere_succeeds() {
        val result = SqlParser.parse("UPDATE users SET active = FALSE")

        assertTrue(result.isSuccess)
        val stmt = result.getOrNull() as UpdateStatement
        assertNull(stmt.where)
    }

    @Test
    fun parse_deleteStatement_succeeds() {
        val result = SqlParser.parse("DELETE FROM users WHERE id = 1")

        assertTrue(result.isSuccess)
        val stmt = result.getOrNull()
        assertIs<DeleteStatement>(stmt)
        assertNotNull(stmt.where)
    }

    @Test
    fun parse_deleteWithoutWhere_succeeds() {
        val result = SqlParser.parse("DELETE FROM users")

        assertTrue(result.isSuccess)
        val stmt = result.getOrNull() as DeleteStatement
        assertNull(stmt.where)
    }

    @Test
    fun parse_selectWithLike_succeeds() {
        val result = SqlParser.parse("SELECT * FROM users WHERE name LIKE '%john%'")

        assertTrue(result.isSuccess)
        val stmt = result.getOrNull() as SelectStatement
        val where = stmt.where as BinaryExpression
        assertEquals("LIKE", where.operator)
    }

    @Test
    fun parse_selectWithIsNull_succeeds() {
        val result = SqlParser.parse("SELECT * FROM users WHERE email IS NULL")

        assertTrue(result.isSuccess)
    }

    @Test
    fun parse_selectWithIsNotNull_succeeds() {
        val result = SqlParser.parse("SELECT * FROM users WHERE email IS NOT NULL")

        assertTrue(result.isSuccess)
    }

    @Test
    fun parse_selectWithDistinct_succeeds() {
        val result = SqlParser.parse("SELECT DISTINCT name FROM users")

        assertTrue(result.isSuccess)
    }

    @Test
    fun parse_literalTypes_succeeds() {
        val result = SqlParser.parse("SELECT 'text', 123, 45.67, TRUE, FALSE, NULL FROM dual")

        assertTrue(result.isSuccess)
        val stmt = result.getOrNull() as SelectStatement
        assertIs<StringLiteral>(stmt.columns[0].expression)
        assertIs<NumberLiteral>(stmt.columns[1].expression)
        assertIs<NumberLiteral>(stmt.columns[2].expression)
    }

    @Test
    fun parse_invalidSyntax_fails() {
        val result = SqlParser.parse("SELECT FROM WHERE")

        assertTrue(result.isFailure)
    }

    @Test
    fun parse_emptyString_fails() {
        val result = SqlParser.parse("")

        assertTrue(result.isFailure)
    }
}
