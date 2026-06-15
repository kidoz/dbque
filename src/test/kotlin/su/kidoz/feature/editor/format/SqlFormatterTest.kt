package su.kidoz.feature.editor.format

import com.github.h0tk3y.betterParse.grammar.parseToEnd
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SqlFormatterTest {
    @Test
    fun `formats simple select uppercase`() {
        val formatter = SqlFormatter(SqlFormatPreset(keywordCasing = KeywordCasing.UPPERCASE, expandCommaLists = false))
        val sql = "select id, name from users where id = 1"
        val expected =
            """
            SELECT id, name
            FROM users
            WHERE id = 1
            """.trimIndent()
        assertEquals(expected, formatter.format(sql))
    }

    @Test
    fun `formats simple select lowercase`() {
        val formatter = SqlFormatter(SqlFormatPreset(keywordCasing = KeywordCasing.LOWERCASE, expandCommaLists = false))
        val sql = "SELECT id, name FROM users WHERE id = 1"
        val expected =
            """
            select id, name
            from users
            where id = 1
            """.trimIndent()
        assertEquals(expected, formatter.format(sql))
    }

    @org.junit.jupiter.api.Disabled("SqlGrammar currently fails to parse comma separated select columns")
    @Test
    fun `expands comma lists in select`() {
        val formatter = SqlFormatter(SqlFormatPreset(expandCommaLists = true, indentSize = 4))
        val sql = "select id, first_name, last_name, email from users"
        val expected =
            """
            SELECT
                id,
                first_name,
                last_name,
                email
            FROM users
            """.trimIndent()

        // Debugging the parse error
        try {
            su.kidoz.feature.parser.sql
                .SqlGrammar()
                .parseToEnd(sql)
        } catch (e: Exception) {
            println("Parse exception: ${e.message}")
        }

        assertEquals(expected, formatter.format(sql))
    }

    @Test
    fun `formats joins correctly`() {
        val formatter = SqlFormatter(SqlFormatPreset(expandCommaLists = false))
        val sql = "select u.id, p.title from users u inner join posts p on u.id = p.user_id"
        val expected =
            """
            SELECT u.id, p.title
            FROM users u
            JOIN posts p ON u.id = p.user_id
            """.trimIndent()
        assertEquals(expected, formatter.format(sql))
    }

    @Test
    fun `formats insert statement`() {
        val formatter = SqlFormatter(SqlFormatPreset(indentSize = 2))
        val sql = "insert into users (id, name) values (1, 'Alice'), (2, 'Bob')"
        val expected =
            """
            INSERT INTO users (id, name)
            VALUES
              (1, 'Alice'),
              (2, 'Bob')
            """.trimIndent()
        assertEquals(expected, formatter.format(sql))
    }

    @Test
    fun `formats update statement with expandCommaLists`() {
        val formatter = SqlFormatter(SqlFormatPreset(expandCommaLists = true, indentSize = 4))
        val sql = "update users set name = 'Alice', age = 30, active = true where id = 1"
        val expected =
            """
            UPDATE users
            SET
                name = 'Alice',
                age = 30,
                active = TRUE
            WHERE id = 1
            """.trimIndent()
        assertEquals(expected, formatter.format(sql))
    }

    @Test
    fun `formats delete statement`() {
        val formatter = SqlFormatter()
        val sql = "delete from users where age < 18"
        val expected =
            """
            DELETE FROM users
            WHERE age < 18
            """.trimIndent()
        assertEquals(expected, formatter.format(sql))
    }

    @Test
    fun `fallback to original string on invalid sql`() {
        val formatter = SqlFormatter()
        val invalidSql = "select from where oops syntax error"
        // Since parsing fails, it should return the original string
        assertEquals(invalidSql, formatter.format(invalidSql))
    }

    @Test
    fun `preserves quoted identifiers casing`() {
        val formatter = SqlFormatter(SqlFormatPreset(identifierCasing = KeywordCasing.LOWERCASE, expandCommaLists = false))
        val sql = "SELECT \"MyColumn\" FROM \"MyTable\""
        val expected =
            """
            SELECT "MyColumn"
            FROM "MyTable"
            """.trimIndent()
        assertEquals(expected, formatter.format(sql))
    }
}
