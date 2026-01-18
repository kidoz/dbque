package su.kidoz.feature.parser.highlight

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyntaxHighlighterTest {
    private val highlighter = SqlHighlighter()
    private val theme = SyntaxTheme.Dark

    @Test
    fun highlightSql_keywordsAreHighlighted() {
        val sql = "SELECT * FROM users WHERE id = 1"
        val spans = highlighter.getSpans(sql, theme)

        // Should have spans for: SELECT, FROM, users, WHERE, id, =, 1
        assertTrue(spans.isNotEmpty(), "Expected spans but got none")

        // Find SELECT keyword
        val selectSpan = spans.find { sql.substring(it.start, it.end) == "SELECT" }
        assertTrue(selectSpan != null, "SELECT keyword should be highlighted")
        assertEquals(HighlightType.KEYWORD, selectSpan.type)
        assertEquals(theme.keyword, selectSpan.style)

        // Find FROM keyword
        val fromSpan = spans.find { sql.substring(it.start, it.end) == "FROM" }
        assertTrue(fromSpan != null, "FROM keyword should be highlighted")
        assertEquals(HighlightType.KEYWORD, fromSpan.type)

        // Find WHERE keyword
        val whereSpan = spans.find { sql.substring(it.start, it.end) == "WHERE" }
        assertTrue(whereSpan != null, "WHERE keyword should be highlighted")
        assertEquals(HighlightType.KEYWORD, whereSpan.type)
    }

    @Test
    fun highlightSql_numbersAreHighlighted() {
        val sql = "SELECT 123, 45.67 FROM dual"
        val spans = highlighter.getSpans(sql, theme)

        val numberSpans = spans.filter { it.type == HighlightType.NUMBER }
        assertEquals(2, numberSpans.size, "Expected 2 number spans")
    }

    @Test
    fun highlightSql_stringsAreHighlighted() {
        val sql = "SELECT 'hello world' FROM dual"
        val spans = highlighter.getSpans(sql, theme)

        val stringSpan = spans.find { it.type == HighlightType.STRING }
        assertTrue(stringSpan != null, "String should be highlighted")
        assertEquals("'hello world'", sql.substring(stringSpan.start, stringSpan.end))
    }

    @Test
    fun highlightSql_commentsAreHighlighted() {
        val sql = "SELECT * -- this is a comment\nFROM users"
        val spans = highlighter.getSpans(sql, theme)

        val commentSpan = spans.find { it.type == HighlightType.COMMENT }
        assertTrue(commentSpan != null, "Comment should be highlighted")
    }

    @Test
    fun highlightSql_functionsAreHighlighted() {
        val sql = "SELECT COUNT(*), MAX(id), SUM(amount) FROM orders"
        val spans = highlighter.getSpans(sql, theme)

        val functionSpans = spans.filter { it.type == HighlightType.FUNCTION }
        val functionNames = functionSpans.map { sql.substring(it.start, it.end) }
        assertTrue(functionNames.contains("COUNT"), "COUNT should be highlighted as function")
        assertTrue(functionNames.contains("MAX"), "MAX should be highlighted as function")
        assertTrue(functionNames.contains("SUM"), "SUM should be highlighted as function")
    }

    @Test
    fun highlightSql_typesAreHighlighted() {
        val sql = "CREATE TABLE test (id INTEGER, name VARCHAR, created TIMESTAMP)"
        val spans = highlighter.getSpans(sql, theme)

        val typeSpans = spans.filter { it.type == HighlightType.TYPE }
        val typeNames = typeSpans.map { sql.substring(it.start, it.end) }
        assertTrue(typeNames.contains("INTEGER"), "INTEGER should be highlighted as type")
        assertTrue(typeNames.contains("VARCHAR"), "VARCHAR should be highlighted as type")
        assertTrue(typeNames.contains("TIMESTAMP"), "TIMESTAMP should be highlighted as type")
    }

    @Test
    fun highlightSql_operatorsAreHighlighted() {
        val sql = "SELECT * FROM users WHERE a = 1 AND b > 2"
        val spans = highlighter.getSpans(sql, theme)

        val operatorSpans = spans.filter { it.type == HighlightType.OPERATOR }
        assertTrue(operatorSpans.isNotEmpty(), "Operators should be highlighted")
    }

    @Test
    fun highlightSql_bracketsAreHighlighted() {
        val sql = "SELECT (a + b) FROM test"
        val spans = highlighter.getSpans(sql, theme)

        val bracketSpans = spans.filter { it.type == HighlightType.BRACKET }
        assertEquals(2, bracketSpans.size, "Both parentheses should be highlighted")
    }

    @Test
    fun highlightSql_parametersAreHighlighted() {
        val sql = "SELECT * FROM users WHERE id = $1 AND name = :name AND active = ?"
        val spans = highlighter.getSpans(sql, theme)

        val paramSpans = spans.filter { it.type == HighlightType.PARAMETER }
        assertEquals(3, paramSpans.size, "All 3 parameters should be highlighted")
    }

    @Test
    fun highlightSql_producesAnnotatedString() {
        val sql = "SELECT * FROM users"
        val annotated = highlighter.highlight(sql, theme)

        assertEquals(sql, annotated.text)
        assertTrue(annotated.spanStyles.isNotEmpty(), "Should have span styles")
    }

    @Test
    fun highlightSql_spanColorsMatchTheme() {
        val sql = "SELECT"
        val spans = highlighter.getSpans(sql, theme)

        assertEquals(1, spans.size)
        assertEquals(theme.keyword.color, spans[0].style.color, "Keyword color should match theme")
    }
}

class MongoHighlighterTest {
    private val highlighter = MongoHighlighter()
    private val theme = SyntaxTheme.Dark

    @Test
    fun highlightMongo_operatorsAreHighlighted() {
        val query = "db.users.find({ \"\$eq\": 1, \"\$gt\": 5 })"
        val spans = highlighter.getSpans(query, theme)

        assertTrue(spans.isNotEmpty(), "Should have spans")
    }

    @Test
    fun highlightMongo_stringsAreHighlighted() {
        val query = """{ "name": "John" }"""
        val spans = highlighter.getSpans(query, theme)

        val stringSpans = spans.filter { it.type == HighlightType.STRING }
        assertTrue(stringSpans.isNotEmpty(), "Strings should be highlighted")
    }
}

class ElasticsearchHighlighterTest {
    private val highlighter = ElasticsearchHighlighter()
    private val theme = SyntaxTheme.Dark

    @Test
    fun highlightEs_queryTypesAreHighlighted() {
        val query = """{ "query": { "match": { "title": "test" } } }"""
        val spans = highlighter.getSpans(query, theme)

        assertTrue(spans.isNotEmpty(), "Should have spans")
    }
}
