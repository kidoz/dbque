package su.kidoz.feature.parser.elasticsearch

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.lexer.TokenMatch
import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.lexer.regexToken
import com.github.h0tk3y.betterParse.parser.Parser
import su.kidoz.feature.parser.ast.*

/**
 * Elasticsearch Query DSL Parser
 * Parses JSON-based Elasticsearch queries into AST
 */
class ElasticsearchGrammar : Grammar<EsQuery>() {
    // ========================================================================
    // Tokens
    // ========================================================================

    private val tokenLbrace = literalToken("{")
    private val tokenRbrace = literalToken("}")
    private val tokenLbracket = literalToken("[")
    private val tokenRbracket = literalToken("]")
    private val tokenColon = literalToken(":")
    private val tokenComma = literalToken(",")

    private val tokenString = regexToken("STRING", "\"([^\"\\\\]|\\\\.)*\"")
    private val tokenNumber = regexToken("NUMBER", "-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?")
    private val tokenTrue = regexToken("TRUE", "true")
    private val tokenFalse = regexToken("FALSE", "false")
    private val tokenNull = regexToken("NULL", "null")

    private val tokenWs = regexToken("WS", "\\s+", ignore = true)

    override val tokens =
        listOf(
            tokenLbrace,
            tokenRbrace,
            tokenLbracket,
            tokenRbracket,
            tokenColon,
            tokenComma,
            tokenString,
            tokenNumber,
            tokenTrue,
            tokenFalse,
            tokenNull,
            tokenWs,
        )

    // ========================================================================
    // Helpers
    // ========================================================================

    private fun TokenMatch.pos() = SourcePosition(offset, offset + text.length)

    private fun parseString(s: String): String =
        s
            .removeSurrounding("\"")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\t", "\t")

    // ========================================================================
    // JSON Value Parsers
    // ========================================================================

    private sealed class JsonValue {
        data class Str(
            val value: String,
            val pos: SourcePosition,
        ) : JsonValue()

        data class Num(
            val value: Number,
            val pos: SourcePosition,
        ) : JsonValue()

        data class Bool(
            val value: Boolean,
            val pos: SourcePosition,
        ) : JsonValue()

        data class Null(
            val pos: SourcePosition,
        ) : JsonValue()

        data class Arr(
            val elements: List<JsonValue>,
            val pos: SourcePosition,
        ) : JsonValue()

        data class Obj(
            val fields: Map<String, JsonValue>,
            val pos: SourcePosition,
        ) : JsonValue()

        fun asString(): String? = (this as? Str)?.value

        fun asNumber(): Number? = (this as? Num)?.value

        fun asInt(): Int? = asNumber()?.toInt()

        fun asFloat(): Float? = asNumber()?.toFloat()

        fun asBool(): Boolean? = (this as? Bool)?.value

        fun asArray(): List<JsonValue>? = (this as? Arr)?.elements

        fun asObject(): Map<String, JsonValue>? = (this as? Obj)?.fields

        fun asAny(): Any? =
            when (this) {
                is Str -> value
                is Num -> value
                is Bool -> value
                is Null -> null
                is Arr -> elements.map { it.asAny() }
                is Obj -> fields.mapValues { it.value.asAny() }
            }
    }

    private val stringValue: Parser<JsonValue.Str> =
        tokenString map {
            JsonValue.Str(parseString(it.text), it.pos())
        }

    private val numberValue: Parser<JsonValue.Num> =
        tokenNumber map {
            val value =
                if (it.text.contains('.') || it.text.contains('e', ignoreCase = true)) {
                    it.text.toDouble()
                } else {
                    it.text.toLong()
                }
            JsonValue.Num(value, it.pos())
        }

    private val boolValue: Parser<JsonValue.Bool> =
        (tokenTrue or tokenFalse) map {
            JsonValue.Bool(it.text == "true", it.pos())
        }

    private val nullValue: Parser<JsonValue.Null> =
        tokenNull map {
            JsonValue.Null(it.pos())
        }

    private val arrayValue: Parser<JsonValue.Arr> by lazy {
        (
            tokenLbracket and
                optional(separatedTerms(parser { jsonValue }, tokenComma)) and
                tokenRbracket
        ) map { result ->
            val lb = result.t1
            val elements = result.t2
            val rb = result.t3
            JsonValue.Arr(elements ?: emptyList(), SourcePosition(lb.offset, rb.offset + 1))
        }
    }

    private val objectKey: Parser<String> = tokenString map { parseString(it.text) }

    private val objectField: Parser<Pair<String, JsonValue>> by lazy {
        (objectKey and tokenColon and parser { jsonValue }) map { result ->
            result.t1 to result.t3
        }
    }

    private val objectValue: Parser<JsonValue.Obj> by lazy {
        (
            tokenLbrace and
                optional(separatedTerms(objectField, tokenComma)) and
                tokenRbrace
        ) map { result ->
            val lb = result.t1
            val fields = result.t2
            val rb = result.t3
            JsonValue.Obj(fields?.toMap() ?: emptyMap(), SourcePosition(lb.offset, rb.offset + 1))
        }
    }

    private val jsonValue: Parser<JsonValue> by lazy {
        stringValue or numberValue or boolValue or nullValue or arrayValue or objectValue
    }

    // ========================================================================
    // Elasticsearch Query Clause Parsers
    // ========================================================================

    private fun parseQueryClause(obj: JsonValue.Obj): EsQueryClause? {
        val (queryType, queryValue) = obj.fields.entries.firstOrNull() ?: return null
        val valueObj = queryValue.asObject()

        return when (queryType) {
            "bool" -> parseBoolQuery(valueObj, obj.pos)

            "match" -> parseMatchQuery(valueObj, obj.pos)

            "match_all" -> EsMatchAllQuery(valueObj?.get("boost")?.asFloat(), obj.pos)

            "term" -> parseTermQuery(valueObj, obj.pos)

            "terms" -> parseTermsQuery(valueObj, obj.pos)

            "range" -> parseRangeQuery(valueObj, obj.pos)

            "exists" -> valueObj?.get("field")?.asString()?.let { EsExistsQuery(it, obj.pos) }

            "wildcard" -> parseWildcardQuery(valueObj, obj.pos)

            "regexp" -> parseRegexpQuery(valueObj, obj.pos)

            "nested" -> parseNestedQuery(valueObj, obj.pos)

            "match_phrase" -> parseMatchQuery(valueObj, obj.pos)

            // Similar structure
            "prefix" -> parsePrefixAsWildcard(valueObj, obj.pos)

            else -> null
        }
    }

    private fun parseBoolQuery(
        obj: Map<String, JsonValue>?,
        pos: SourcePosition,
    ): EsBoolQuery {
        if (obj == null) return EsBoolQuery(position = pos)

        fun parseClauseList(key: String): List<EsQueryClause> =
            obj[key]?.asArray()?.mapNotNull { elem ->
                (elem as? JsonValue.Obj)?.let { parseQueryClause(it) }
            } ?: emptyList()

        return EsBoolQuery(
            must = parseClauseList("must"),
            should = parseClauseList("should"),
            mustNot = parseClauseList("must_not"),
            filter = parseClauseList("filter"),
            minimumShouldMatch = obj["minimum_should_match"]?.asInt(),
            position = pos,
        )
    }

    private fun parseMatchQuery(
        obj: Map<String, JsonValue>?,
        pos: SourcePosition,
    ): EsMatchQuery? {
        if (obj == null) return null

        val (field, fieldValue) = obj.entries.firstOrNull() ?: return null

        return when (fieldValue) {
            is JsonValue.Str -> {
                EsMatchQuery(field, fieldValue.value, position = pos)
            }

            is JsonValue.Obj -> {
                val queryVal = fieldValue.fields["query"]?.asString() ?: ""
                EsMatchQuery(
                    field = field,
                    query = queryVal,
                    operator = fieldValue.fields["operator"]?.asString(),
                    fuzziness = fieldValue.fields["fuzziness"]?.asString(),
                    position = pos,
                )
            }

            else -> {
                null
            }
        }
    }

    private fun parseTermQuery(
        obj: Map<String, JsonValue>?,
        pos: SourcePosition,
    ): EsTermQuery? {
        if (obj == null) return null

        val (field, fieldValue) = obj.entries.firstOrNull() ?: return null

        return when (fieldValue) {
            is JsonValue.Obj -> {
                val value = fieldValue.fields["value"]?.asAny() ?: return null
                EsTermQuery(
                    field = field,
                    value = value,
                    boost = fieldValue.fields["boost"]?.asFloat(),
                    position = pos,
                )
            }

            else -> {
                EsTermQuery(field, fieldValue.asAny() ?: return null, position = pos)
            }
        }
    }

    private fun parseTermsQuery(
        obj: Map<String, JsonValue>?,
        pos: SourcePosition,
    ): EsTermsQuery? {
        if (obj == null) return null

        val (field, fieldValue) = obj.entries.firstOrNull() ?: return null
        val values = fieldValue.asArray()?.mapNotNull { it.asAny() } ?: return null

        return EsTermsQuery(field, values, pos)
    }

    private fun parseRangeQuery(
        obj: Map<String, JsonValue>?,
        pos: SourcePosition,
    ): EsRangeQuery? {
        if (obj == null) return null

        val (field, fieldValue) = obj.entries.firstOrNull() ?: return null
        val rangeObj = fieldValue.asObject() ?: return null

        return EsRangeQuery(
            field = field,
            gt = rangeObj["gt"]?.asAny(),
            gte = rangeObj["gte"]?.asAny(),
            lt = rangeObj["lt"]?.asAny(),
            lte = rangeObj["lte"]?.asAny(),
            format = rangeObj["format"]?.asString(),
            position = pos,
        )
    }

    private fun parseWildcardQuery(
        obj: Map<String, JsonValue>?,
        pos: SourcePosition,
    ): EsWildcardQuery? {
        if (obj == null) return null

        val (field, fieldValue) = obj.entries.firstOrNull() ?: return null

        return when (fieldValue) {
            is JsonValue.Str -> {
                EsWildcardQuery(field, fieldValue.value, pos)
            }

            is JsonValue.Obj -> {
                val value = fieldValue.fields["value"]?.asString() ?: return null
                EsWildcardQuery(field, value, pos)
            }

            else -> {
                null
            }
        }
    }

    private fun parseRegexpQuery(
        obj: Map<String, JsonValue>?,
        pos: SourcePosition,
    ): EsRegexpQuery? {
        if (obj == null) return null

        val (field, fieldValue) = obj.entries.firstOrNull() ?: return null

        return when (fieldValue) {
            is JsonValue.Str -> {
                EsRegexpQuery(field, fieldValue.value, position = pos)
            }

            is JsonValue.Obj -> {
                val value = fieldValue.fields["value"]?.asString() ?: return null
                EsRegexpQuery(
                    field = field,
                    value = value,
                    flags = fieldValue.fields["flags"]?.asString(),
                    position = pos,
                )
            }

            else -> {
                null
            }
        }
    }

    private fun parseNestedQuery(
        obj: Map<String, JsonValue>?,
        pos: SourcePosition,
    ): EsNestedQuery? {
        if (obj == null) return null

        val path = obj["path"]?.asString() ?: return null
        val queryObj = obj["query"]?.asObject()?.let { JsonValue.Obj(it, pos) } ?: return null
        val query = parseQueryClause(queryObj) ?: return null

        return EsNestedQuery(
            path = path,
            query = query,
            scoreMode = obj["score_mode"]?.asString(),
            position = pos,
        )
    }

    private fun parsePrefixAsWildcard(
        obj: Map<String, JsonValue>?,
        pos: SourcePosition,
    ): EsWildcardQuery? {
        if (obj == null) return null

        val (field, fieldValue) = obj.entries.firstOrNull() ?: return null
        val value =
            when (fieldValue) {
                is JsonValue.Str -> fieldValue.value
                is JsonValue.Obj -> fieldValue.fields["value"]?.asString()
                else -> null
            } ?: return null

        return EsWildcardQuery(field, "$value*", pos)
    }

    // ========================================================================
    // Elasticsearch Aggregation Parsers
    // ========================================================================

    private fun parseAggregation(obj: JsonValue.Obj): EsAggregation? {
        val fields = obj.fields

        return when {
            "terms" in fields -> {
                val termsObj = fields["terms"]?.asObject() ?: return null
                EsTermsAgg(
                    field = termsObj["field"]?.asString() ?: return null,
                    size = termsObj["size"]?.asInt(),
                    subAggs =
                        fields["aggs"]?.asObject()?.let { parseAggregations(it) }
                            ?: fields["aggregations"]?.asObject()?.let { parseAggregations(it) },
                    position = obj.pos,
                )
            }

            "avg" in fields -> {
                val avgObj = fields["avg"]?.asObject() ?: return null
                EsAvgAgg(avgObj["field"]?.asString() ?: return null, obj.pos)
            }

            "sum" in fields -> {
                val sumObj = fields["sum"]?.asObject() ?: return null
                EsSumAgg(sumObj["field"]?.asString() ?: return null, obj.pos)
            }

            "min" in fields -> {
                val minObj = fields["min"]?.asObject() ?: return null
                EsMinAgg(minObj["field"]?.asString() ?: return null, obj.pos)
            }

            "max" in fields -> {
                val maxObj = fields["max"]?.asObject() ?: return null
                EsMaxAgg(maxObj["field"]?.asString() ?: return null, obj.pos)
            }

            "value_count" in fields -> {
                val countObj = fields["value_count"]?.asObject()
                EsCountAgg(countObj?.get("field")?.asString(), obj.pos)
            }

            "date_histogram" in fields -> {
                val dhObj = fields["date_histogram"]?.asObject() ?: return null
                EsDateHistogramAgg(
                    field = dhObj["field"]?.asString() ?: return null,
                    interval =
                        dhObj["interval"]?.asString()
                            ?: dhObj["calendar_interval"]?.asString()
                            ?: dhObj["fixed_interval"]?.asString()
                            ?: return null,
                    format = dhObj["format"]?.asString(),
                    subAggs =
                        fields["aggs"]?.asObject()?.let { parseAggregations(it) }
                            ?: fields["aggregations"]?.asObject()?.let { parseAggregations(it) },
                    position = obj.pos,
                )
            }

            else -> {
                null
            }
        }
    }

    private fun parseAggregations(obj: Map<String, JsonValue>): Map<String, EsAggregation> =
        obj
            .mapNotNull { (name, value) ->
                (value as? JsonValue.Obj)?.let { parseAggregation(it) }?.let { name to it }
            }.toMap()

    // ========================================================================
    // Sort Parser
    // ========================================================================

    private fun parseSort(arr: JsonValue.Arr): List<EsSort> {
        return arr.elements.mapNotNull { elem ->
            when (elem) {
                is JsonValue.Str -> {
                    EsSort(elem.value, position = elem.pos)
                }

                is JsonValue.Obj -> {
                    val (field, fieldValue) = elem.fields.entries.firstOrNull() ?: return@mapNotNull null
                    when (fieldValue) {
                        is JsonValue.Str -> {
                            EsSort(field, fieldValue.value, position = elem.pos)
                        }

                        is JsonValue.Obj -> {
                            EsSort(
                                field = field,
                                order = fieldValue.fields["order"]?.asString() ?: "asc",
                                mode = fieldValue.fields["mode"]?.asString(),
                                position = elem.pos,
                            )
                        }

                        else -> {
                            null
                        }
                    }
                }

                else -> {
                    null
                }
            }
        }
    }

    // ========================================================================
    // Source Parser
    // ========================================================================

    private fun parseSource(value: JsonValue): EsSource? =
        when (value) {
            is JsonValue.Bool -> {
                if (value.value) null else EsSource(excludes = listOf("*"), position = value.pos)
            }

            is JsonValue.Arr -> {
                EsSource(includes = value.elements.mapNotNull { it.asString() }, position = value.pos)
            }

            is JsonValue.Obj -> {
                EsSource(
                    includes = value.fields["includes"]?.asArray()?.mapNotNull { it.asString() },
                    excludes = value.fields["excludes"]?.asArray()?.mapNotNull { it.asString() },
                    position = value.pos,
                )
            }

            else -> {
                null
            }
        }

    // ========================================================================
    // Root Query Parser
    // ========================================================================

    private val esQuery: Parser<EsQuery> =
        objectValue map { root ->
            val fields = root.fields

            val query =
                fields["query"]?.let { queryVal ->
                    (queryVal as? JsonValue.Obj)?.let { parseQueryClause(it) }
                }

            val sort =
                fields["sort"]?.let { sortVal ->
                    (sortVal as? JsonValue.Arr)?.let { parseSort(it) }
                }

            val source = fields["_source"]?.let { parseSource(it) }

            val aggregations =
                (fields["aggs"] ?: fields["aggregations"])?.asObject()?.let {
                    parseAggregations(it)
                }

            EsQuery(
                index = null,
                query = query,
                from = fields["from"]?.asInt(),
                size = fields["size"]?.asInt(),
                sort = sort,
                source = source,
                aggregations = aggregations,
                position = root.pos,
            )
        }

    override val rootParser: Parser<EsQuery> = esQuery
}

/**
 * Elasticsearch Parser facade
 */
object ElasticsearchParser {
    private val grammar = ElasticsearchGrammar()

    fun parse(query: String): Result<EsQuery> =
        try {
            Result.success(grammar.parseToEnd(query))
        } catch (e: Exception) {
            Result.failure(e)
        }
}
