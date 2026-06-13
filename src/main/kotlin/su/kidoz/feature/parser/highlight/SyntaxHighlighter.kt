package su.kidoz.feature.parser.highlight

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import su.kidoz.feature.parser.sql.SqlDialect

/**
 * Syntax highlighting theme
 */
data class SyntaxTheme(
    val keyword: SpanStyle,
    val string: SpanStyle,
    val number: SpanStyle,
    val operator: SpanStyle,
    val identifier: SpanStyle,
    val function: SpanStyle,
    val type: SpanStyle,
    val comment: SpanStyle,
    val error: SpanStyle,
    val bracket: SpanStyle,
    val property: SpanStyle,
    val mongoOperator: SpanStyle,
    val esQueryType: SpanStyle,
    val parameter: SpanStyle,
) {
    companion object {
        val Dark =
            SyntaxTheme(
                keyword = SpanStyle(color = Color(0xFF569CD6), fontWeight = FontWeight.Bold),
                string = SpanStyle(color = Color(0xFFCE9178)),
                number = SpanStyle(color = Color(0xFFB5CEA8)),
                operator = SpanStyle(color = Color(0xFFD4D4D4)),
                identifier = SpanStyle(color = Color(0xFF9CDCFE)),
                function = SpanStyle(color = Color(0xFFDCDCAA)),
                type = SpanStyle(color = Color(0xFF4EC9B0)),
                comment = SpanStyle(color = Color(0xFF6A9955), fontStyle = FontStyle.Italic),
                error = SpanStyle(color = Color(0xFFEF4E4E)),
                bracket = SpanStyle(color = Color(0xFFD7BA7D)),
                property = SpanStyle(color = Color(0xFF9CDCFE)),
                mongoOperator = SpanStyle(color = Color(0xFF4EC9B0)),
                esQueryType = SpanStyle(color = Color(0xFF4EC9B0)),
                parameter = SpanStyle(color = Color(0xFFD7BA7D)),
            )

        val Light =
            SyntaxTheme(
                keyword = SpanStyle(color = Color(0xFF2F5AFF), fontWeight = FontWeight.Bold),
                string = SpanStyle(color = Color(0xFFB85A0A)),
                number = SpanStyle(color = Color(0xFF0C8A55)),
                operator = SpanStyle(color = Color(0xFF1B1B1B)),
                identifier = SpanStyle(color = Color(0xFF0A3A8C)),
                function = SpanStyle(color = Color(0xFF9A6A00)),
                type = SpanStyle(color = Color(0xFF0E7C69)),
                comment = SpanStyle(color = Color(0xFF7A859A), fontStyle = FontStyle.Italic),
                error = SpanStyle(color = Color(0xFFEF4E4E)),
                bracket = SpanStyle(color = Color(0xFF3D6BFF)),
                property = SpanStyle(color = Color(0xFF0A3A8C)),
                mongoOperator = SpanStyle(color = Color(0xFF1AA88E)),
                esQueryType = SpanStyle(color = Color(0xFF3D7BFF)),
                parameter = SpanStyle(color = Color(0xFFF2B86B)),
            )
    }
}

/**
 * Highlight span with position and style
 */
data class HighlightSpan(
    val start: Int,
    val end: Int,
    val style: SpanStyle,
    val type: HighlightType,
)

enum class HighlightType {
    KEYWORD,
    STRING,
    NUMBER,
    OPERATOR,
    IDENTIFIER,
    FUNCTION,
    TYPE,
    COMMENT,
    ERROR,
    BRACKET,
    PROPERTY,
    MONGO_OPERATOR,
    ES_QUERY_TYPE,
    PARAMETER,
}

/**
 * Abstract syntax highlighter
 */
interface QueryHighlighter {
    fun highlight(
        text: String,
        theme: SyntaxTheme,
    ): AnnotatedString

    fun getSpans(
        text: String,
        theme: SyntaxTheme,
    ): List<HighlightSpan>
}

// ============================================================================
// SQL Syntax Highlighter
// ============================================================================

class SqlHighlighter(
    private val dialect: SqlDialect = SqlDialect.POSTGRESQL,
) : QueryHighlighter {
    private val sqlKeywords =
        setOf(
            "SELECT",
            "FROM",
            "WHERE",
            "AND",
            "OR",
            "NOT",
            "AS",
            "ON",
            "JOIN",
            "LEFT",
            "RIGHT",
            "INNER",
            "OUTER",
            "FULL",
            "CROSS",
            "GROUP",
            "BY",
            "HAVING",
            "ORDER",
            "ASC",
            "DESC",
            "LIMIT",
            "OFFSET",
            "NULLS",
            "FIRST",
            "LAST",
            "DISTINCT",
            "ALL",
            "UNION",
            "INTERSECT",
            "EXCEPT",
            "INSERT",
            "INTO",
            "VALUES",
            "UPDATE",
            "SET",
            "DELETE",
            "CREATE",
            "TABLE",
            "INDEX",
            "DROP",
            "ALTER",
            "ADD",
            "COLUMN",
            "PRIMARY",
            "KEY",
            "FOREIGN",
            "REFERENCES",
            "CONSTRAINT",
            "UNIQUE",
            "CHECK",
            "DEFAULT",
            "NULL",
            "TRUE",
            "FALSE",
            "IS",
            "IN",
            "BETWEEN",
            "LIKE",
            "ILIKE",
            "SIMILAR",
            "TO",
            "ESCAPE",
            "CASE",
            "WHEN",
            "THEN",
            "ELSE",
            "END",
            "CAST",
            "EXISTS",
            "ANY",
            "SOME",
            "WITH",
            "RECURSIVE",
            "OVER",
            "PARTITION",
            "ROWS",
            "RANGE",
            "UNBOUNDED",
            "PRECEDING",
            "FOLLOWING",
            "CURRENT",
            "ROW",
            "RETURNING",
            "CONFLICT",
            "DO",
            "NOTHING",
            "LATERAL",
            "REPLACE",
            "IGNORE",
            "DUPLICATE",
            "USING",
        )

    private val sqlFunctions =
        setOf(
            "COUNT",
            "SUM",
            "AVG",
            "MIN",
            "MAX",
            "COALESCE",
            "NULLIF",
            "GREATEST",
            "LEAST",
            "CONCAT",
            "SUBSTRING",
            "TRIM",
            "UPPER",
            "LOWER",
            "LENGTH",
            "REPLACE",
            "POSITION",
            "NOW",
            "CURRENT_TIMESTAMP",
            "CURRENT_DATE",
            "CURRENT_TIME",
            "DATE_TRUNC",
            "EXTRACT",
            "TO_CHAR",
            "TO_DATE",
            "TO_TIMESTAMP",
            "CAST",
            "ARRAY_AGG",
            "STRING_AGG",
            "JSON_AGG",
            "JSONB_AGG",
            "ROW_NUMBER",
            "RANK",
            "DENSE_RANK",
            "LAG",
            "LEAD",
            "FIRST_VALUE",
            "LAST_VALUE",
            "NTH_VALUE",
            "NTILE",
            "PERCENT_RANK",
            "CUME_DIST",
            "ABS",
            "ROUND",
            "CEIL",
            "FLOOR",
            "POWER",
            "SQRT",
            "MOD",
        )

    private val sqlTypes =
        setOf(
            "INT",
            "INTEGER",
            "BIGINT",
            "SMALLINT",
            "SERIAL",
            "BIGSERIAL",
            "DECIMAL",
            "NUMERIC",
            "REAL",
            "DOUBLE",
            "FLOAT",
            "BOOLEAN",
            "BOOL",
            "CHAR",
            "VARCHAR",
            "TEXT",
            "BYTEA",
            "DATE",
            "TIME",
            "TIMESTAMP",
            "TIMESTAMPTZ",
            "INTERVAL",
            "UUID",
            "JSON",
            "JSONB",
            "ARRAY",
            "XML",
        )

    override fun highlight(
        text: String,
        theme: SyntaxTheme,
    ): AnnotatedString {
        val spans = getSpans(text, theme)

        return buildAnnotatedString {
            append(text)
            for (span in spans) {
                addStyle(span.style, span.start, span.end.coerceAtMost(text.length))
            }
        }
    }

    override fun getSpans(
        text: String,
        theme: SyntaxTheme,
    ): List<HighlightSpan> {
        val spans = mutableListOf<HighlightSpan>()
        var i = 0

        while (i < text.length) {
            when {
                // Single-line comment
                text.startsWith("--", i) -> {
                    val end = text.indexOf('\n', i).takeIf { it >= 0 } ?: text.length
                    spans.add(HighlightSpan(i, end, theme.comment, HighlightType.COMMENT))
                    i = end
                }

                // Multi-line comment
                text.startsWith("/*", i) -> {
                    val end = (text.indexOf("*/", i + 2).takeIf { it >= 0 }?.plus(2)) ?: text.length
                    spans.add(HighlightSpan(i, end, theme.comment, HighlightType.COMMENT))
                    i = end
                }

                // String literal (single quotes)
                text[i] == '\'' -> {
                    val end = findStringEnd(text, i, '\'')
                    spans.add(HighlightSpan(i, end, theme.string, HighlightType.STRING))
                    i = end
                }

                // Dollar-quoted string (PostgreSQL)
                text.startsWith("\$\$", i) -> {
                    val end = (text.indexOf("\$\$", i + 2).takeIf { it >= 0 }?.plus(2)) ?: text.length
                    spans.add(HighlightSpan(i, end, theme.string, HighlightType.STRING))
                    i = end
                }

                // Quoted identifier
                text[i] == '"' -> {
                    val end = findStringEnd(text, i, '"')
                    spans.add(HighlightSpan(i, end, theme.identifier, HighlightType.IDENTIFIER))
                    i = end
                }

                // Backtick identifier (MySQL)
                text[i] == '`' -> {
                    val end = findStringEnd(text, i, '`')
                    spans.add(HighlightSpan(i, end, theme.identifier, HighlightType.IDENTIFIER))
                    i = end
                }

                // Number
                text[i].isDigit() || (text[i] == '-' && i + 1 < text.length && text[i + 1].isDigit()) -> {
                    val end = findNumberEnd(text, i)
                    spans.add(HighlightSpan(i, end, theme.number, HighlightType.NUMBER))
                    i = end
                }

                // Parameter ($1, ?, :name)
                text[i] == '$' && i + 1 < text.length && text[i + 1].isDigit() -> {
                    val end = findIdentifierEnd(text, i + 1)
                    spans.add(HighlightSpan(i, end, theme.parameter, HighlightType.PARAMETER))
                    i = end
                }

                text[i] == '?' -> {
                    spans.add(HighlightSpan(i, i + 1, theme.parameter, HighlightType.PARAMETER))
                    i++
                }

                text[i] == ':' && i + 1 < text.length && (text[i + 1].isLetter() || text[i + 1] == '_') -> {
                    val end = findIdentifierEnd(text, i + 1)
                    spans.add(HighlightSpan(i, end, theme.parameter, HighlightType.PARAMETER))
                    i = end
                }

                // Identifier/Keyword
                text[i].isLetter() || text[i] == '_' -> {
                    val end = findIdentifierEnd(text, i)
                    val word = text.substring(i, end)
                    val upperWord = word.uppercase()

                    val (style, type) =
                        when {
                            upperWord in sqlKeywords -> theme.keyword to HighlightType.KEYWORD
                            upperWord in sqlFunctions -> theme.function to HighlightType.FUNCTION
                            upperWord in sqlTypes -> theme.type to HighlightType.TYPE
                            else -> theme.identifier to HighlightType.IDENTIFIER
                        }
                    spans.add(HighlightSpan(i, end, style, type))
                    i = end
                }

                // Operators
                text[i] in "=<>!+-*/%|&^~" -> {
                    val end = findOperatorEnd(text, i)
                    spans.add(HighlightSpan(i, end, theme.operator, HighlightType.OPERATOR))
                    i = end
                }

                // Brackets
                text[i] in "()[]" -> {
                    spans.add(HighlightSpan(i, i + 1, theme.bracket, HighlightType.BRACKET))
                    i++
                }

                else -> {
                    i++
                }
            }
        }

        return spans
    }

    private fun findStringEnd(
        text: String,
        start: Int,
        quote: Char,
    ): Int {
        var i = start + 1
        while (i < text.length) {
            if (text[i] == quote) {
                if (i + 1 < text.length && text[i + 1] == quote) {
                    i += 2 // Escaped quote
                } else {
                    return i + 1
                }
            } else if (text[i] == '\\' && i + 1 < text.length) {
                i += 2 // Escape sequence
            } else {
                i++
            }
        }
        return text.length
    }

    private fun findNumberEnd(
        text: String,
        start: Int,
    ): Int {
        var i = start
        if (i < text.length && text[i] == '-') i++
        while (i < text.length && text[i].isDigit()) i++
        if (i < text.length && text[i] == '.') {
            i++
            while (i < text.length && text[i].isDigit()) i++
        }
        if (i < text.length && text[i].lowercaseChar() == 'e') {
            i++
            if (i < text.length && text[i] in "+-") i++
            while (i < text.length && text[i].isDigit()) i++
        }
        return i
    }

    private fun findIdentifierEnd(
        text: String,
        start: Int,
    ): Int {
        var i = start
        while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
        return i
    }

    private fun findOperatorEnd(
        text: String,
        start: Int,
    ): Int {
        val operators =
            listOf(
                "::",
                "->",
                "->>",
                "#>",
                "#>>",
                "||",
                "<=",
                ">=",
                "<>",
                "!=",
                "~*",
                "!~*",
                "~",
                "!~",
            )
        for (op in operators) {
            if (text.startsWith(op, start)) return start + op.length
        }
        return start + 1
    }
}

// ============================================================================
// MongoDB Syntax Highlighter
// ============================================================================

class MongoHighlighter : QueryHighlighter {
    // Query and Comparison Operators
    private val queryOperators =
        setOf(
            "\$eq",
            "\$ne",
            "\$gt",
            "\$gte",
            "\$lt",
            "\$lte",
            "\$in",
            "\$nin",
            "\$and",
            "\$or",
            "\$nor",
            "\$not",
            "\$exists",
            "\$type",
            "\$regex",
            "\$options",
            "\$mod",
            "\$text",
            "\$search",
            "\$where",
            "\$expr",
            "\$jsonSchema",
            "\$comment",
        )

    // Array Query Operators
    private val arrayOperators =
        setOf(
            "\$all",
            "\$elemMatch",
            "\$size",
        )

    // Geospatial Operators
    private val geoOperators =
        setOf(
            "\$geoWithin",
            "\$geoIntersects",
            "\$near",
            "\$nearSphere",
            "\$box",
            "\$center",
            "\$centerSphere",
            "\$geometry",
            "\$maxDistance",
            "\$minDistance",
            "\$polygon",
        )

    // Update Operators
    private val updateOperators =
        setOf(
            "\$set",
            "\$unset",
            "\$inc",
            "\$mul",
            "\$rename",
            "\$setOnInsert",
            "\$min",
            "\$max",
            "\$currentDate",
            "\$addToSet",
            "\$pop",
            "\$pull",
            "\$push",
            "\$pullAll",
            "\$each",
            "\$position",
            "\$slice",
            "\$sort",
            "\$bit",
        )

    // Aggregation Pipeline Stages
    private val pipelineStages =
        setOf(
            "\$match",
            "\$project",
            "\$group",
            "\$sort",
            "\$limit",
            "\$skip",
            "\$unwind",
            "\$lookup",
            "\$graphLookup",
            "\$sample",
            "\$count",
            "\$facet",
            "\$bucket",
            "\$bucketAuto",
            "\$addFields",
            "\$replaceRoot",
            "\$replaceWith",
            "\$merge",
            "\$out",
            "\$redact",
            "\$sortByCount",
            "\$unionWith",
            "\$densify",
            "\$fill",
            "\$setWindowFields",
            "\$documents",
            "\$changeStream",
            "\$listSessions",
            "\$listLocalSessions",
            "\$currentOp",
            "\$collStats",
            "\$indexStats",
            "\$planCacheStats",
            "\$geoNear",
        )

    // Aggregation Accumulators
    private val accumulators =
        setOf(
            "\$sum",
            "\$avg",
            "\$min",
            "\$max",
            "\$first",
            "\$last",
            "\$push",
            "\$addToSet",
            "\$stdDevPop",
            "\$stdDevSamp",
            "\$mergeObjects",
            "\$accumulator",
            "\$bottom",
            "\$bottomN",
            "\$top",
            "\$topN",
            "\$firstN",
            "\$lastN",
            "\$maxN",
            "\$minN",
            "\$median",
            "\$percentile",
        )

    // Aggregation Expressions
    private val expressions =
        setOf(
            "\$abs",
            "\$add",
            "\$ceil",
            "\$divide",
            "\$exp",
            "\$floor",
            "\$ln",
            "\$log",
            "\$log10",
            "\$mod",
            "\$multiply",
            "\$pow",
            "\$round",
            "\$sqrt",
            "\$subtract",
            "\$trunc",
            "\$concat",
            "\$indexOfBytes",
            "\$indexOfCP",
            "\$ltrim",
            "\$rtrim",
            "\$regexFind",
            "\$regexFindAll",
            "\$regexMatch",
            "\$split",
            "\$strLenBytes",
            "\$strLenCP",
            "\$strcasecmp",
            "\$substr",
            "\$substrBytes",
            "\$substrCP",
            "\$toLower",
            "\$toUpper",
            "\$trim",
            "\$dateFromParts",
            "\$dateFromString",
            "\$dateToParts",
            "\$dateToString",
            "\$dayOfMonth",
            "\$dayOfWeek",
            "\$dayOfYear",
            "\$hour",
            "\$isoDayOfWeek",
            "\$isoWeek",
            "\$isoWeekYear",
            "\$millisecond",
            "\$minute",
            "\$month",
            "\$second",
            "\$toDate",
            "\$week",
            "\$year",
            "\$cond",
            "\$ifNull",
            "\$switch",
            "\$arrayElemAt",
            "\$arrayToObject",
            "\$concatArrays",
            "\$filter",
            "\$firstN",
            "\$in",
            "\$indexOfArray",
            "\$isArray",
            "\$lastN",
            "\$map",
            "\$maxN",
            "\$minN",
            "\$objectToArray",
            "\$range",
            "\$reduce",
            "\$reverseArray",
            "\$size",
            "\$slice",
            "\$sortArray",
            "\$zip",
            "\$literal",
            "\$type",
            "\$convert",
            "\$toBool",
            "\$toDecimal",
            "\$toDouble",
            "\$toInt",
            "\$toLong",
            "\$toObjectId",
            "\$toString",
            "\$let",
            "\$getField",
            "\$setField",
            "\$unsetField",
            "\$mergeObjects",
            "\$objectToArray",
            "\$setDifference",
            "\$setEquals",
            "\$setIntersection",
            "\$setIsSubset",
            "\$setUnion",
            "\$allElementsTrue",
            "\$anyElementTrue",
            "\$and",
            "\$or",
            "\$not",
            "\$cmp",
            "\$eq",
            "\$gt",
            "\$gte",
            "\$lt",
            "\$lte",
            "\$ne",
        )

    // All MongoDB operators combined
    private val mongoOperators =
        queryOperators + arrayOperators + geoOperators + updateOperators +
            pipelineStages + accumulators + expressions

    private val mongoMethods =
        setOf(
            // Find operations
            "find",
            "findOne",
            // Count operations
            "countDocuments",
            "estimatedDocumentCount",
            "count",
            // Distinct
            "distinct",
            // Aggregation
            "aggregate",
            // Insert operations
            "insertOne",
            "insertMany",
            // Update operations
            "updateOne",
            "updateMany",
            "replaceOne",
            // Delete operations
            "deleteOne",
            "deleteMany",
            // FindAndModify operations
            "findOneAndUpdate",
            "findOneAndDelete",
            "findOneAndReplace",
            // Index operations
            "createIndex",
            "createIndexes",
            "dropIndex",
            "dropIndexes",
            "getIndexes",
            "reIndex",
            // Cursor modifiers
            "sort",
            "limit",
            "skip",
            "project",
            "hint",
            "explain",
            "batchSize",
            "maxTimeMS",
            "toArray",
            "forEach",
            "map",
            "hasNext",
            "next",
            "close",
            // Collection operations
            "drop",
            "renameCollection",
            "watch",
            "validate",
            "stats",
            // Bulk operations
            "bulkWrite",
            "initializeOrderedBulkOp",
            "initializeUnorderedBulkOp",
        )

    override fun highlight(
        text: String,
        theme: SyntaxTheme,
    ): AnnotatedString {
        val spans = getSpans(text, theme)

        return buildAnnotatedString {
            append(text)
            for (span in spans) {
                addStyle(span.style, span.start, span.end.coerceAtMost(text.length))
            }
        }
    }

    override fun getSpans(
        text: String,
        theme: SyntaxTheme,
    ): List<HighlightSpan> {
        val spans = mutableListOf<HighlightSpan>()
        var i = 0

        while (i < text.length) {
            when {
                // Comment
                text.startsWith("//", i) -> {
                    val end = text.indexOf('\n', i).takeIf { it >= 0 } ?: text.length
                    spans.add(HighlightSpan(i, end, theme.comment, HighlightType.COMMENT))
                    i = end
                }

                // String (double or single quotes)
                text[i] == '"' || text[i] == '\'' -> {
                    val quote = text[i]
                    val end = findJsonStringEnd(text, i, quote)
                    val content = text.substring(i + 1, (end - 1).coerceAtLeast(i + 1))

                    val (style, type) =
                        when {
                            content.startsWith("\$") && content in mongoOperators -> {
                                theme.mongoOperator to HighlightType.MONGO_OPERATOR
                            }

                            else -> {
                                theme.string to HighlightType.STRING
                            }
                        }
                    spans.add(HighlightSpan(i, end, style, type))
                    i = end
                }

                // Number
                text[i].isDigit() || (text[i] == '-' && i + 1 < text.length && text[i + 1].isDigit()) -> {
                    val end = findNumberEnd(text, i)
                    spans.add(HighlightSpan(i, end, theme.number, HighlightType.NUMBER))
                    i = end
                }

                // Boolean/null
                text.startsWith("true", i) && !text.getOrNull(i + 4).isLetterOrDigitOrNull() -> {
                    spans.add(HighlightSpan(i, i + 4, theme.keyword, HighlightType.KEYWORD))
                    i += 4
                }

                text.startsWith("false", i) && !text.getOrNull(i + 5).isLetterOrDigitOrNull() -> {
                    spans.add(HighlightSpan(i, i + 5, theme.keyword, HighlightType.KEYWORD))
                    i += 5
                }

                text.startsWith("null", i) && !text.getOrNull(i + 4).isLetterOrDigitOrNull() -> {
                    spans.add(HighlightSpan(i, i + 4, theme.keyword, HighlightType.KEYWORD))
                    i += 4
                }

                // Identifier (db, method names)
                text[i].isLetter() || text[i] == '_' -> {
                    val end = findIdentifierEnd(text, i)
                    val word = text.substring(i, end)

                    val (style, type) =
                        when {
                            word == "db" -> theme.keyword to HighlightType.KEYWORD
                            word in mongoMethods -> theme.function to HighlightType.FUNCTION
                            else -> theme.identifier to HighlightType.IDENTIFIER
                        }
                    spans.add(HighlightSpan(i, end, style, type))
                    i = end
                }

                // MongoDB operator in unquoted form
                text[i] == '$' -> {
                    val end = findIdentifierEnd(text, i + 1)
                    val op = text.substring(i, end)
                    val style =
                        if (op in mongoOperators) {
                            theme.mongoOperator
                        } else {
                            theme.identifier
                        }
                    spans.add(HighlightSpan(i, end, style, HighlightType.MONGO_OPERATOR))
                    i = end
                }

                // Brackets
                text[i] in "{}[]()." -> {
                    spans.add(HighlightSpan(i, i + 1, theme.bracket, HighlightType.BRACKET))
                    i++
                }

                else -> {
                    i++
                }
            }
        }

        return spans
    }

    private fun findJsonStringEnd(
        text: String,
        start: Int,
        quote: Char,
    ): Int {
        var i = start + 1
        while (i < text.length) {
            when {
                text[i] == '\\' && i + 1 < text.length -> i += 2
                text[i] == quote -> return i + 1
                else -> i++
            }
        }
        return text.length
    }

    private fun findNumberEnd(
        text: String,
        start: Int,
    ): Int {
        var i = start
        if (i < text.length && text[i] == '-') i++
        while (i < text.length && text[i].isDigit()) i++
        if (i < text.length && text[i] == '.') {
            i++
            while (i < text.length && text[i].isDigit()) i++
        }
        if (i < text.length && text[i].lowercaseChar() == 'e') {
            i++
            if (i < text.length && text[i] in "+-") i++
            while (i < text.length && text[i].isDigit()) i++
        }
        return i
    }

    private fun findIdentifierEnd(
        text: String,
        start: Int,
    ): Int {
        var i = start
        while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
        return i
    }

    private fun Char?.isLetterOrDigitOrNull(): Boolean = this?.isLetterOrDigit() ?: false
}

// ============================================================================
// Elasticsearch Syntax Highlighter
// ============================================================================

class ElasticsearchHighlighter : QueryHighlighter {
    private val esQueryTypes =
        setOf(
            "bool",
            "match",
            "match_all",
            "match_phrase",
            "multi_match",
            "term",
            "terms",
            "range",
            "exists",
            "prefix",
            "wildcard",
            "regexp",
            "fuzzy",
            "type",
            "ids",
            "nested",
            "has_child",
            "has_parent",
            "constant_score",
            "dis_max",
            "function_score",
            "boosting",
            "query_string",
            "simple_query_string",
        )

    private val esBoolClauses = setOf("must", "should", "must_not", "filter")

    private val esAggTypes =
        setOf(
            "terms",
            "avg",
            "sum",
            "min",
            "max",
            "value_count",
            "cardinality",
            "stats",
            "extended_stats",
            "percentiles",
            "percentile_ranks",
            "date_histogram",
            "histogram",
            "range",
            "date_range",
            "geo_distance",
            "nested",
            "reverse_nested",
            "filter",
            "filters",
            "global",
            "sampler",
            "diversified_sampler",
            "top_hits",
        )

    private val esKeywords =
        setOf(
            "query",
            "aggs",
            "aggregations",
            "sort",
            "from",
            "size",
            "_source",
            "highlight",
            "post_filter",
            "rescore",
            "suggest",
            "track_total_hits",
            "min_score",
            "timeout",
            "terminate_after",
            "search_after",
        )

    override fun highlight(
        text: String,
        theme: SyntaxTheme,
    ): AnnotatedString {
        val spans = getSpans(text, theme)

        return buildAnnotatedString {
            append(text)
            for (span in spans) {
                addStyle(span.style, span.start, span.end.coerceAtMost(text.length))
            }
        }
    }

    override fun getSpans(
        text: String,
        theme: SyntaxTheme,
    ): List<HighlightSpan> {
        val spans = mutableListOf<HighlightSpan>()
        var i = 0

        while (i < text.length) {
            when {
                // String
                text[i] == '"' -> {
                    val end = findJsonStringEnd(text, i)
                    val content = text.substring(i + 1, (end - 1).coerceAtLeast(i + 1))

                    val (style, type) =
                        when {
                            content in esQueryTypes -> theme.esQueryType to HighlightType.ES_QUERY_TYPE
                            content in esBoolClauses -> theme.keyword to HighlightType.KEYWORD
                            content in esAggTypes -> theme.function to HighlightType.FUNCTION
                            content in esKeywords -> theme.keyword to HighlightType.KEYWORD
                            else -> theme.string to HighlightType.STRING
                        }
                    spans.add(HighlightSpan(i, end, style, type))
                    i = end
                }

                // Number
                text[i].isDigit() || (text[i] == '-' && i + 1 < text.length && text[i + 1].isDigit()) -> {
                    val end = findNumberEnd(text, i)
                    spans.add(HighlightSpan(i, end, theme.number, HighlightType.NUMBER))
                    i = end
                }

                // Boolean/null
                text.startsWith("true", i) && !text.getOrNull(i + 4).isLetterOrDigitOrNull() -> {
                    spans.add(HighlightSpan(i, i + 4, theme.keyword, HighlightType.KEYWORD))
                    i += 4
                }

                text.startsWith("false", i) && !text.getOrNull(i + 5).isLetterOrDigitOrNull() -> {
                    spans.add(HighlightSpan(i, i + 5, theme.keyword, HighlightType.KEYWORD))
                    i += 5
                }

                text.startsWith("null", i) && !text.getOrNull(i + 4).isLetterOrDigitOrNull() -> {
                    spans.add(HighlightSpan(i, i + 4, theme.keyword, HighlightType.KEYWORD))
                    i += 4
                }

                // Brackets
                text[i] in "{}[]" -> {
                    spans.add(HighlightSpan(i, i + 1, theme.bracket, HighlightType.BRACKET))
                    i++
                }

                else -> {
                    i++
                }
            }
        }

        return spans
    }

    private fun findJsonStringEnd(
        text: String,
        start: Int,
    ): Int {
        var i = start + 1
        while (i < text.length) {
            when {
                text[i] == '\\' && i + 1 < text.length -> i += 2
                text[i] == '"' -> return i + 1
                else -> i++
            }
        }
        return text.length
    }

    private fun findNumberEnd(
        text: String,
        start: Int,
    ): Int {
        var i = start
        if (i < text.length && text[i] == '-') i++
        while (i < text.length && text[i].isDigit()) i++
        if (i < text.length && text[i] == '.') {
            i++
            while (i < text.length && text[i].isDigit()) i++
        }
        if (i < text.length && text[i].lowercaseChar() == 'e') {
            i++
            if (i < text.length && text[i] in "+-") i++
            while (i < text.length && text[i].isDigit()) i++
        }
        return i
    }

    private fun Char?.isLetterOrDigitOrNull(): Boolean = this?.isLetterOrDigit() ?: false
}

// ============================================================================
// Unified Highlighter Factory
// ============================================================================

object HighlighterFactory {
    fun create(
        databaseType: DatabaseType,
        dialect: SqlDialect = SqlDialect.POSTGRESQL,
    ): QueryHighlighter =
        when (databaseType) {
            DatabaseType.SQL -> SqlHighlighter(dialect)
            DatabaseType.MONGODB -> MongoHighlighter()
            DatabaseType.ELASTICSEARCH -> ElasticsearchHighlighter()
        }
}

enum class DatabaseType {
    SQL,
    MONGODB,
    ELASTICSEARCH,
}
