package su.kidoz.feature.parser

import androidx.compose.ui.text.AnnotatedString
import su.kidoz.feature.parser.ast.*
import su.kidoz.feature.parser.elasticsearch.ElasticsearchParser
import su.kidoz.feature.parser.highlight.*
import su.kidoz.feature.parser.mongo.MongoParser
import su.kidoz.feature.parser.sql.SqlDialect
import su.kidoz.feature.parser.sql.SqlParser
import su.kidoz.feature.parser.validation.*

/**
 * Unified Query Parser Service
 * Main entry point for parsing, highlighting, and validating queries
 */
class QueryParserService {
    private val sqlHighlighter = SqlHighlighter()
    private val mongoHighlighter = MongoHighlighter()
    private val esHighlighter = ElasticsearchHighlighter()

    // ========================================================================
    // SQL
    // ========================================================================

    fun parseSql(
        sql: String,
        dialect: SqlDialect = SqlDialect.POSTGRESQL,
    ): Result<SqlStatement> = SqlParser.parse(sql, dialect)

    fun validateSql(
        sql: String,
        dialect: SqlDialect = SqlDialect.POSTGRESQL,
        version: DatabaseVersion? = null,
    ): ValidationResult = SqlValidator(dialect, version).validate(sql)

    fun highlightSql(
        sql: String,
        theme: SyntaxTheme = SyntaxTheme.Dark,
    ): AnnotatedString = sqlHighlighter.highlight(sql, theme)

    fun getSqlSpans(
        sql: String,
        theme: SyntaxTheme = SyntaxTheme.Dark,
    ): List<HighlightSpan> = sqlHighlighter.getSpans(sql, theme)

    // ========================================================================
    // MongoDB
    // ========================================================================

    fun parseMongo(query: String): Result<MongoNode> = MongoParser.parse(query)

    fun validateMongo(
        query: String,
        version: DatabaseVersion? = null,
    ): ValidationResult = MongoValidator(version).validate(query)

    fun highlightMongo(
        query: String,
        theme: SyntaxTheme = SyntaxTheme.Dark,
    ): AnnotatedString = mongoHighlighter.highlight(query, theme)

    fun getMongoSpans(
        query: String,
        theme: SyntaxTheme = SyntaxTheme.Dark,
    ): List<HighlightSpan> = mongoHighlighter.getSpans(query, theme)

    // ========================================================================
    // Elasticsearch
    // ========================================================================

    fun parseElasticsearch(query: String): Result<EsQuery> = ElasticsearchParser.parse(query)

    fun validateElasticsearch(
        query: String,
        version: DatabaseVersion? = null,
    ): ValidationResult = ElasticsearchValidator(version).validate(query)

    fun highlightElasticsearch(
        query: String,
        theme: SyntaxTheme = SyntaxTheme.Dark,
    ): AnnotatedString = esHighlighter.highlight(query, theme)

    fun getElasticsearchSpans(
        query: String,
        theme: SyntaxTheme = SyntaxTheme.Dark,
    ): List<HighlightSpan> = esHighlighter.getSpans(query, theme)

    // ========================================================================
    // Auto-detect
    // ========================================================================

    fun detectQueryType(query: String): DatabaseType {
        val trimmed = query.trim()
        return when {
            // SQL patterns
            trimmed.uppercase().let {
                it.startsWith("SELECT") ||
                    it.startsWith("INSERT") ||
                    it.startsWith("UPDATE") ||
                    it.startsWith("DELETE") ||
                    it.startsWith("CREATE") ||
                    it.startsWith("DROP") ||
                    it.startsWith("ALTER") ||
                    it.startsWith("WITH")
            } -> DatabaseType.SQL

            // MongoDB patterns
            trimmed.startsWith("db.") ||
                (trimmed.startsWith("{") && trimmed.contains("\$")) -> DatabaseType.MONGODB

            // Elasticsearch patterns
            trimmed.startsWith("{") &&
                (
                    trimmed.contains("\"query\"") ||
                        trimmed.contains("\"aggs\"") ||
                        trimmed.contains("\"bool\"") ||
                        trimmed.contains("\"match\"")
                ) -> DatabaseType.ELASTICSEARCH

            // Default to SQL
            else -> DatabaseType.SQL
        }
    }

    fun parse(query: String): Result<QueryNode> =
        when (detectQueryType(query)) {
            DatabaseType.SQL -> parseSql(query).map { it as QueryNode }
            DatabaseType.MONGODB -> parseMongo(query).map { it as QueryNode }
            DatabaseType.ELASTICSEARCH -> parseElasticsearch(query).map { it as QueryNode }
        }

    fun validate(
        query: String,
        version: DatabaseVersion? = null,
    ): ValidationResult =
        when (detectQueryType(query)) {
            DatabaseType.SQL -> validateSql(query, version = version)
            DatabaseType.MONGODB -> validateMongo(query, version)
            DatabaseType.ELASTICSEARCH -> validateElasticsearch(query, version)
        }

    fun highlight(
        query: String,
        theme: SyntaxTheme = SyntaxTheme.Dark,
    ): AnnotatedString =
        when (detectQueryType(query)) {
            DatabaseType.SQL -> highlightSql(query, theme)
            DatabaseType.MONGODB -> highlightMongo(query, theme)
            DatabaseType.ELASTICSEARCH -> highlightElasticsearch(query, theme)
        }

    fun getSpans(
        query: String,
        theme: SyntaxTheme = SyntaxTheme.Dark,
    ): List<HighlightSpan> =
        when (detectQueryType(query)) {
            DatabaseType.SQL -> getSqlSpans(query, theme)
            DatabaseType.MONGODB -> getMongoSpans(query, theme)
            DatabaseType.ELASTICSEARCH -> getElasticsearchSpans(query, theme)
        }

    // ========================================================================
    // Explicit type methods
    // ========================================================================

    fun parseAs(
        query: String,
        type: DatabaseType,
        dialect: SqlDialect = SqlDialect.POSTGRESQL,
    ): Result<QueryNode> =
        when (type) {
            DatabaseType.SQL -> parseSql(query, dialect).map { it as QueryNode }
            DatabaseType.MONGODB -> parseMongo(query).map { it as QueryNode }
            DatabaseType.ELASTICSEARCH -> parseElasticsearch(query).map { it as QueryNode }
        }

    fun validateAs(
        query: String,
        type: DatabaseType,
        dialect: SqlDialect = SqlDialect.POSTGRESQL,
        version: DatabaseVersion? = null,
    ): ValidationResult =
        when (type) {
            DatabaseType.SQL -> validateSql(query, dialect, version)
            DatabaseType.MONGODB -> validateMongo(query, version)
            DatabaseType.ELASTICSEARCH -> validateElasticsearch(query, version)
        }

    fun highlightAs(
        query: String,
        type: DatabaseType,
        dialect: SqlDialect = SqlDialect.POSTGRESQL,
        theme: SyntaxTheme = SyntaxTheme.Dark,
    ): AnnotatedString =
        when (type) {
            DatabaseType.SQL -> SqlHighlighter(dialect).highlight(query, theme)
            DatabaseType.MONGODB -> highlightMongo(query, theme)
            DatabaseType.ELASTICSEARCH -> highlightElasticsearch(query, theme)
        }

    fun getSpansAs(
        query: String,
        type: DatabaseType,
        dialect: SqlDialect = SqlDialect.POSTGRESQL,
        theme: SyntaxTheme = SyntaxTheme.Dark,
    ): List<HighlightSpan> =
        when (type) {
            DatabaseType.SQL -> SqlHighlighter(dialect).getSpans(query, theme)
            DatabaseType.MONGODB -> getMongoSpans(query, theme)
            DatabaseType.ELASTICSEARCH -> getElasticsearchSpans(query, theme)
        }
}
