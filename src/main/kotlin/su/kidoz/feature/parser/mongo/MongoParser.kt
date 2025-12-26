package su.kidoz.feature.parser.mongo

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
 * MongoDB Query/Aggregation Parser
 * Parses MongoDB shell syntax and JSON query format
 */
class MongoGrammar : Grammar<MongoNode>() {
    // ========================================================================
    // Tokens
    // ========================================================================

    // Keywords
    private val keywordDb = regexToken("DB", "db")
    private val keywordFind = regexToken("FIND", "find")
    private val keywordAggregate = regexToken("AGGREGATE", "aggregate")
    private val keywordInsertOne = regexToken("INSERT_ONE", "insertOne")
    private val keywordInsertMany = regexToken("INSERT_MANY", "insertMany")
    private val keywordUpdateOne = regexToken("UPDATE_ONE", "updateOne")
    private val keywordUpdateMany = regexToken("UPDATE_MANY", "updateMany")
    private val keywordDeleteOne = regexToken("DELETE_ONE", "deleteOne")
    private val keywordDeleteMany = regexToken("DELETE_MANY", "deleteMany")
    private val keywordSort = regexToken("SORT", "sort")
    private val keywordLimit = regexToken("LIMIT", "limit")
    private val keywordSkip = regexToken("SKIP", "skip")
    private val keywordProject = regexToken("PROJECT", "project")
    private val keywordCount = regexToken("COUNT", "count")

    // JSON tokens
    private val tokenLbrace = literalToken("{")
    private val tokenRbrace = literalToken("}")
    private val tokenLbracket = literalToken("[")
    private val tokenRbracket = literalToken("]")
    private val tokenColon = literalToken(":")
    private val tokenComma = literalToken(",")
    private val tokenLparen = literalToken("(")
    private val tokenRparen = literalToken(")")
    private val tokenDot = literalToken(".")

    // Literals
    private val tokenString = regexToken("STRING", "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'")
    private val tokenNumber = regexToken("NUMBER", "-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?")
    private val tokenTrue = regexToken("TRUE", "true")
    private val tokenFalse = regexToken("FALSE", "false")
    private val tokenNull = regexToken("NULL", "null")

    // MongoDB operators (start with $)
    private val tokenOperator = regexToken("OPERATOR", "\\$[a-zA-Z_][a-zA-Z0-9_]*")

    // Identifiers
    private val tokenId = regexToken("ID", "[a-zA-Z_][a-zA-Z0-9_]*")

    // Whitespace and comments
    private val tokenWs = regexToken("WS", "\\s+", ignore = true)
    private val tokenLineComment = regexToken("LINE_COMMENT", "//[^\\n]*", ignore = true)

    override val tokens =
        listOf(
            tokenLineComment,
            keywordDb,
            keywordFind,
            keywordAggregate,
            keywordInsertOne,
            keywordInsertMany,
            keywordUpdateOne,
            keywordUpdateMany,
            keywordDeleteOne,
            keywordDeleteMany,
            keywordSort,
            keywordLimit,
            keywordSkip,
            keywordProject,
            keywordCount,
            tokenLbrace,
            tokenRbrace,
            tokenLbracket,
            tokenRbracket,
            tokenColon,
            tokenComma,
            tokenLparen,
            tokenRparen,
            tokenDot,
            tokenString,
            tokenNumber,
            tokenTrue,
            tokenFalse,
            tokenNull,
            tokenOperator,
            tokenId,
            tokenWs,
        )

    // ========================================================================
    // Helpers
    // ========================================================================

    private fun TokenMatch.pos() = SourcePosition(offset, offset + text.length)

    private fun parseString(s: String): String {
        val inner = s.substring(1, s.length - 1)
        return inner
            .replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
    }

    // ========================================================================
    // JSON Value Parsers
    // ========================================================================

    private val stringValue: Parser<MongoScalar> =
        tokenString map {
            MongoScalar(parseString(it.text), it.pos())
        }

    private val numberValue: Parser<MongoScalar> =
        tokenNumber map {
            val value =
                if (it.text.contains('.') || it.text.contains('e', ignoreCase = true)) {
                    it.text.toDouble()
                } else {
                    it.text.toLong()
                }
            MongoScalar(value, it.pos())
        }

    private val boolValue: Parser<MongoScalar> =
        (tokenTrue or tokenFalse) map {
            MongoScalar(it.text == "true", it.pos())
        }

    private val nullValue: Parser<MongoScalar> =
        tokenNull map {
            MongoScalar(null, it.pos())
        }

    private val arrayValue: Parser<MongoArray> by lazy {
        (
            tokenLbracket and
                optional(separatedTerms(parser { jsonValue }, tokenComma)) and
                tokenRbracket
        ) map { result ->
            val lb = result.t1
            val elements = result.t2
            val rb = result.t3
            MongoArray(elements ?: emptyList(), SourcePosition(lb.offset, rb.offset + 1))
        }
    }

    private val objectKey: Parser<String> =
        (tokenString or tokenId or tokenOperator) map {
            if (it.text.startsWith("\"") || it.text.startsWith("'")) {
                parseString(it.text)
            } else {
                it.text
            }
        }

    private val objectField: Parser<Pair<String, MongoValue>> by lazy {
        (objectKey and tokenColon and parser { jsonValue }) map { result ->
            result.t1 to result.t3
        }
    }

    private val objectValue: Parser<MongoObject> by lazy {
        (
            tokenLbrace and
                optional(separatedTerms(objectField, tokenComma)) and
                tokenRbrace
        ) map { result ->
            val lb = result.t1
            val fields = result.t2
            val rb = result.t3
            MongoObject(
                fields?.toMap() ?: emptyMap(),
                SourcePosition(lb.offset, rb.offset + 1),
            )
        }
    }

    private val jsonValue: Parser<MongoValue> by lazy {
        stringValue or numberValue or boolValue or nullValue or arrayValue or objectValue
    }

    // ========================================================================
    // MongoDB Filter Parser
    // ========================================================================

    private fun parseFilter(obj: MongoObject): MongoFilter {
        val filters = mutableListOf<MongoFilter>()

        for ((key, value) in obj.fields) {
            when {
                // Logical operators
                key == "\$and" && value is MongoArray -> {
                    val subFilters =
                        value.elements.mapNotNull {
                            (it as? MongoObject)?.let { parseFilter(it) }
                        }
                    filters.add(MongoLogicalFilter(MongoLogicalOperator.AND, subFilters, obj.position))
                }
                key == "\$or" && value is MongoArray -> {
                    val subFilters =
                        value.elements.mapNotNull {
                            (it as? MongoObject)?.let { parseFilter(it) }
                        }
                    filters.add(MongoLogicalFilter(MongoLogicalOperator.OR, subFilters, obj.position))
                }
                key == "\$nor" && value is MongoArray -> {
                    val subFilters =
                        value.elements.mapNotNull {
                            (it as? MongoObject)?.let { parseFilter(it) }
                        }
                    filters.add(MongoLogicalFilter(MongoLogicalOperator.NOR, subFilters, obj.position))
                }
                key == "\$not" && value is MongoObject -> {
                    filters.add(
                        MongoLogicalFilter(
                            MongoLogicalOperator.NOT,
                            listOf(parseFilter(value)),
                            obj.position,
                        ),
                    )
                }
                // Field with operators
                value is MongoObject && value.fields.keys.any { it.startsWith("\$") } -> {
                    for ((op, opValue) in value.fields) {
                        val operator = MongoOperator.entries.find { it.symbol == op }
                        if (operator != null) {
                            filters.add(MongoFieldFilter(key, operator, opValue, value.position))
                        }
                    }
                }
                // Direct equality
                else -> {
                    filters.add(MongoFieldFilter(key, MongoOperator.EQ, value, obj.position))
                }
            }
        }

        return when {
            filters.isEmpty() ->
                MongoFieldFilter(
                    "_id",
                    MongoOperator.EXISTS,
                    MongoScalar(true, obj.position),
                    obj.position,
                )
            filters.size == 1 -> filters.first()
            else -> MongoLogicalFilter(MongoLogicalOperator.AND, filters, obj.position)
        }
    }

    // ========================================================================
    // MongoDB Aggregation Pipeline Parser
    // ========================================================================

    private fun parseStage(obj: MongoObject): MongoStage? {
        val (stageType, stageValue) = obj.fields.entries.firstOrNull() ?: return null

        return when (stageType) {
            "\$match" ->
                (stageValue as? MongoObject)?.let {
                    MatchStage(parseFilter(it), obj.position)
                }
            "\$project" ->
                (stageValue as? MongoObject)?.let { projObj ->
                    val fields =
                        projObj.fields.mapValues { (_, v) ->
                            when (v) {
                                is MongoScalar -> (v.value as? Number)?.toInt() == 1
                                else -> true
                            }
                        }
                    ProjectStage(MongoProjection(fields, projObj.position), obj.position)
                }
            "\$group" ->
                (stageValue as? MongoObject)?.let { groupObj ->
                    val id = groupObj.fields["_id"] ?: MongoScalar(null, obj.position)
                    val accumulators =
                        groupObj.fields.filterKeys { it != "_id" }.mapValues { (_, v) ->
                            if (v is MongoObject && v.fields.size == 1) {
                                val (accOp, accExpr) = v.fields.entries.first()
                                MongoAccumulator(accOp, accExpr, v.position)
                            } else {
                                MongoAccumulator("\$first", v, v.position)
                            }
                        }
                    GroupStage(id, accumulators, obj.position)
                }
            "\$sort" ->
                (stageValue as? MongoObject)?.let { sortObj ->
                    val fields =
                        sortObj.fields.mapValues { (_, v) ->
                            (v as? MongoScalar)?.value?.let { (it as? Number)?.toInt() } ?: 1
                        }
                    SortStage(MongoSort(fields, sortObj.position), obj.position)
                }
            "\$limit" ->
                (stageValue as? MongoScalar)?.value?.let {
                    LimitStage((it as Number).toInt(), obj.position)
                }
            "\$skip" ->
                (stageValue as? MongoScalar)?.value?.let {
                    SkipStage((it as Number).toInt(), obj.position)
                }
            "\$lookup" ->
                (stageValue as? MongoObject)?.let { lookupObj ->
                    val from = (lookupObj.fields["from"] as? MongoScalar)?.value as? String ?: ""
                    val localField = (lookupObj.fields["localField"] as? MongoScalar)?.value as? String ?: ""
                    val foreignField = (lookupObj.fields["foreignField"] as? MongoScalar)?.value as? String ?: ""
                    val alias = (lookupObj.fields["as"] as? MongoScalar)?.value as? String ?: ""
                    LookupStage(from, localField, foreignField, alias, obj.position)
                }
            "\$unwind" ->
                when (stageValue) {
                    is MongoScalar -> UnwindStage(stageValue.value as? String ?: "", false, obj.position)
                    is MongoObject -> {
                        val path = (stageValue.fields["path"] as? MongoScalar)?.value as? String ?: ""
                        val preserve =
                            (stageValue.fields["preserveNullAndEmptyArrays"] as? MongoScalar)?.value as? Boolean ?: false
                        UnwindStage(path, preserve, obj.position)
                    }
                    else -> null
                }
            else -> null
        }
    }

    // ========================================================================
    // Shell Command Parsers
    // ========================================================================

    private val collectionName: Parser<String> = tokenId map { it.text }

    // Modifier parsers for chained method calls
    private val sortModifier: Parser<Pair<String, MongoValue>> by lazy {
        (tokenDot and keywordSort and tokenLparen and objectValue and tokenRparen) map { result ->
            "sort" to result.t4
        }
    }

    private val limitModifier: Parser<Pair<String, MongoValue>> by lazy {
        (tokenDot and keywordLimit and tokenLparen and tokenNumber and tokenRparen) map { result ->
            val n = result.t4
            "limit" to MongoScalar(n.text.toInt(), n.pos())
        }
    }

    private val skipModifier: Parser<Pair<String, MongoValue>> by lazy {
        (tokenDot and keywordSkip and tokenLparen and tokenNumber and tokenRparen) map { result ->
            val n = result.t4
            "skip" to MongoScalar(n.text.toInt(), n.pos())
        }
    }

    // db.collection.find({...})
    private val findQuery: Parser<MongoQuery> by lazy {
        (
            keywordDb and tokenDot and collectionName and tokenDot and keywordFind and
                tokenLparen and optional(objectValue) and
                optional(tokenComma and objectValue map { it.t2 }) and tokenRparen and
                zeroOrMore(sortModifier or limitModifier or skipModifier)
        ) map { result ->
            val db = result.t1
            val coll = result.t3
            val filter = result.t7
            val projection = result.t8
            val modifiers = result.t10

            var sort: MongoSort? = null
            var limit: Int? = null
            var skip: Int? = null

            for ((mod, value) in modifiers) {
                when (mod) {
                    "sort" ->
                        sort =
                            MongoSort(
                                (value as MongoObject).fields.mapValues { (_, v) ->
                                    ((v as? MongoScalar)?.value as? Number)?.toInt() ?: 1
                                },
                                value.position,
                            )
                    "limit" -> limit = ((value as MongoScalar).value as Number).toInt()
                    "skip" -> skip = ((value as MongoScalar).value as Number).toInt()
                }
            }

            MongoQuery(
                collection = coll,
                filter = filter?.let { parseFilter(it) },
                projection =
                    projection?.let { p ->
                        MongoProjection(
                            p.fields.mapValues { (_, v) -> ((v as? MongoScalar)?.value as? Number)?.toInt() == 1 },
                            p.position,
                        )
                    },
                sort = sort,
                limit = limit,
                skip = skip,
                position = db.pos(),
            )
        }
    }

    // db.collection.aggregate([...])
    private val aggregateQuery: Parser<MongoAggregation> by lazy {
        (
            keywordDb and tokenDot and collectionName and tokenDot and keywordAggregate and
                tokenLparen and arrayValue and tokenRparen
        ) map { result ->
            val db = result.t1
            val coll = result.t3
            val pipeline = result.t7
            val stages =
                pipeline.elements.mapNotNull { elem ->
                    (elem as? MongoObject)?.let { parseStage(it) }
                }
            MongoAggregation(coll, stages, db.pos())
        }
    }

    // Pure JSON query (for validation/highlighting)
    private val jsonQuery: Parser<MongoQuery> =
        objectValue map { obj ->
            MongoQuery(
                collection = null,
                filter = parseFilter(obj),
                position = obj.position,
            )
        }

    // ========================================================================
    // Root Parser
    // ========================================================================

    override val rootParser: Parser<MongoNode> by lazy { findQuery or aggregateQuery or jsonQuery }
}

/**
 * MongoDB Parser facade
 */
object MongoParser {
    private val grammar = MongoGrammar()

    fun parse(query: String): Result<MongoNode> =
        try {
            Result.success(grammar.parseToEnd(query))
        } catch (e: Exception) {
            Result.failure(e)
        }

    fun parseFilter(json: String): Result<MongoFilter> =
        try {
            val node = grammar.parseToEnd(json)
            when (node) {
                is MongoQuery ->
                    node.filter?.let { Result.success(it) }
                        ?: Result.failure(IllegalArgumentException("No filter found"))
                else -> Result.failure(IllegalArgumentException("Expected query, got ${node::class.simpleName}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
}
