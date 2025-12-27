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
 *
 * Supports:
 * - Find operations: find(), findOne()
 * - Count operations: countDocuments(), estimatedDocumentCount()
 * - Distinct: distinct()
 * - Insert operations: insertOne(), insertMany()
 * - Update operations: updateOne(), updateMany(), replaceOne()
 * - Delete operations: deleteOne(), deleteMany()
 * - FindAndModify: findOneAndUpdate(), findOneAndDelete(), findOneAndReplace()
 * - Index operations: createIndex(), dropIndex(), getIndexes()
 * - Aggregation: aggregate() with all standard stages
 * - Method chaining: sort(), limit(), skip(), project(), hint(), explain()
 */
class MongoGrammar : Grammar<MongoNode>() {
    // ========================================================================
    // Tokens
    // ========================================================================

    // Keywords - db object
    private val keywordDb = regexToken("DB", "db")

    // Find operations
    private val keywordFind = regexToken("FIND", "find")
    private val keywordFindOne = regexToken("FIND_ONE", "findOne")

    // Count operations
    private val keywordCountDocuments = regexToken("COUNT_DOCUMENTS", "countDocuments")
    private val keywordEstimatedDocumentCount = regexToken("ESTIMATED_COUNT", "estimatedDocumentCount")

    // Distinct
    private val keywordDistinct = regexToken("DISTINCT", "distinct")

    // Aggregation
    private val keywordAggregate = regexToken("AGGREGATE", "aggregate")

    // Insert operations
    private val keywordInsertOne = regexToken("INSERT_ONE", "insertOne")
    private val keywordInsertMany = regexToken("INSERT_MANY", "insertMany")

    // Update operations
    private val keywordUpdateOne = regexToken("UPDATE_ONE", "updateOne")
    private val keywordUpdateMany = regexToken("UPDATE_MANY", "updateMany")
    private val keywordReplaceOne = regexToken("REPLACE_ONE", "replaceOne")

    // Delete operations
    private val keywordDeleteOne = regexToken("DELETE_ONE", "deleteOne")
    private val keywordDeleteMany = regexToken("DELETE_MANY", "deleteMany")

    // FindAndModify operations
    private val keywordFindOneAndUpdate = regexToken("FIND_ONE_AND_UPDATE", "findOneAndUpdate")
    private val keywordFindOneAndDelete = regexToken("FIND_ONE_AND_DELETE", "findOneAndDelete")
    private val keywordFindOneAndReplace = regexToken("FIND_ONE_AND_REPLACE", "findOneAndReplace")

    // Index operations
    private val keywordCreateIndex = regexToken("CREATE_INDEX", "createIndex")
    private val keywordDropIndex = regexToken("DROP_INDEX", "dropIndex")
    private val keywordGetIndexes = regexToken("GET_INDEXES", "getIndexes")

    // Method modifiers
    private val keywordSort = regexToken("SORT", "sort")
    private val keywordLimit = regexToken("LIMIT", "limit")
    private val keywordSkip = regexToken("SKIP", "skip")
    private val keywordProject = regexToken("PROJECT", "project")
    private val keywordHint = regexToken("HINT", "hint")
    private val keywordExplain = regexToken("EXPLAIN", "explain")
    private val keywordCount = regexToken("COUNT", "count")
    private val keywordToArray = regexToken("TO_ARRAY", "toArray")

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
            // Find operations
            keywordFindOne,
            keywordFind,
            // Count operations
            keywordCountDocuments,
            keywordEstimatedDocumentCount,
            // Distinct
            keywordDistinct,
            // Aggregation
            keywordAggregate,
            // Insert operations
            keywordInsertOne,
            keywordInsertMany,
            // Update operations
            keywordUpdateOne,
            keywordUpdateMany,
            keywordReplaceOne,
            // Delete operations
            keywordDeleteOne,
            keywordDeleteMany,
            // FindAndModify operations
            keywordFindOneAndUpdate,
            keywordFindOneAndDelete,
            keywordFindOneAndReplace,
            // Index operations
            keywordCreateIndex,
            keywordDropIndex,
            keywordGetIndexes,
            // Method modifiers
            keywordSort,
            keywordLimit,
            keywordSkip,
            keywordProject,
            keywordHint,
            keywordExplain,
            keywordCount,
            keywordToArray,
            // JSON tokens
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

    private fun parseStage(obj: MongoObject): MongoStage {
        val (stageType, stageValue) =
            obj.fields.entries.firstOrNull()
                ?: return GenericStage("unknown", obj, obj.position)

        return when (stageType) {
            "\$match" ->
                (stageValue as? MongoObject)?.let {
                    MatchStage(parseFilter(it), obj.position)
                } ?: GenericStage(stageType, stageValue, obj.position)

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
                } ?: GenericStage(stageType, stageValue, obj.position)

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
                } ?: GenericStage(stageType, stageValue, obj.position)

            "\$sort" ->
                (stageValue as? MongoObject)?.let { sortObj ->
                    val fields =
                        sortObj.fields.mapValues { (_, v) ->
                            (v as? MongoScalar)?.value?.let { (it as? Number)?.toInt() } ?: 1
                        }
                    SortStage(MongoSort(fields, sortObj.position), obj.position)
                } ?: GenericStage(stageType, stageValue, obj.position)

            "\$limit" ->
                (stageValue as? MongoScalar)?.value?.let {
                    LimitStage((it as Number).toInt(), obj.position)
                } ?: GenericStage(stageType, stageValue, obj.position)

            "\$skip" ->
                (stageValue as? MongoScalar)?.value?.let {
                    SkipStage((it as Number).toInt(), obj.position)
                } ?: GenericStage(stageType, stageValue, obj.position)

            "\$lookup" ->
                (stageValue as? MongoObject)?.let { lookupObj ->
                    val from = (lookupObj.fields["from"] as? MongoScalar)?.value as? String ?: ""
                    val localField = (lookupObj.fields["localField"] as? MongoScalar)?.value as? String ?: ""
                    val foreignField = (lookupObj.fields["foreignField"] as? MongoScalar)?.value as? String ?: ""
                    val alias = (lookupObj.fields["as"] as? MongoScalar)?.value as? String ?: ""
                    LookupStage(from, localField, foreignField, alias, obj.position)
                } ?: GenericStage(stageType, stageValue, obj.position)

            "\$unwind" ->
                when (stageValue) {
                    is MongoScalar -> UnwindStage(stageValue.value as? String ?: "", false, obj.position)
                    is MongoObject -> {
                        val path = (stageValue.fields["path"] as? MongoScalar)?.value as? String ?: ""
                        val preserve = (stageValue.fields["preserveNullAndEmptyArrays"] as? MongoScalar)?.value as? Boolean ?: false
                        UnwindStage(path, preserve, obj.position)
                    }
                    else -> GenericStage(stageType, stageValue, obj.position)
                }

            "\$addFields", "\$set" ->
                (stageValue as? MongoObject)?.let { fieldsObj ->
                    AddFieldsStage(fieldsObj.fields, obj.position)
                } ?: GenericStage(stageType, stageValue, obj.position)

            "\$replaceRoot" ->
                (stageValue as? MongoObject)?.let { replaceObj ->
                    val newRoot = replaceObj.fields["newRoot"] ?: stageValue
                    ReplaceRootStage(newRoot, obj.position)
                } ?: GenericStage(stageType, stageValue, obj.position)

            "\$replaceWith" ->
                ReplaceRootStage(stageValue, obj.position)

            "\$facet" ->
                (stageValue as? MongoObject)?.let { facetObj ->
                    val facets =
                        facetObj.fields.mapValues { (_, v) ->
                            (v as? MongoArray)?.elements?.mapNotNull { elem ->
                                (elem as? MongoObject)?.let { parseStage(it) }
                            } ?: emptyList()
                        }
                    FacetStage(facets, obj.position)
                } ?: GenericStage(stageType, stageValue, obj.position)

            "\$bucket" ->
                (stageValue as? MongoObject)?.let { bucketObj ->
                    val groupBy = bucketObj.fields["groupBy"] ?: MongoScalar(null, obj.position)
                    val boundaries =
                        bucketObj.fields["boundaries"] as? MongoArray
                            ?: MongoArray(emptyList(), obj.position)
                    val defaultBucket = bucketObj.fields["default"]
                    val output =
                        (bucketObj.fields["output"] as? MongoObject)?.fields?.mapValues { (_, v) ->
                            if (v is MongoObject && v.fields.size == 1) {
                                val (accOp, accExpr) = v.fields.entries.first()
                                MongoAccumulator(accOp, accExpr, v.position)
                            } else {
                                MongoAccumulator("\$sum", MongoScalar(1, v.position), v.position)
                            }
                        }
                    BucketStage(groupBy, boundaries, defaultBucket, output, obj.position)
                } ?: GenericStage(stageType, stageValue, obj.position)

            "\$bucketAuto" ->
                (stageValue as? MongoObject)?.let { bucketObj ->
                    val groupBy = bucketObj.fields["groupBy"] ?: MongoScalar(null, obj.position)
                    val buckets =
                        (bucketObj.fields["buckets"] as? MongoScalar)?.value?.let {
                            (it as? Number)?.toInt()
                        } ?: 5
                    val granularity = (bucketObj.fields["granularity"] as? MongoScalar)?.value as? String
                    val output =
                        (bucketObj.fields["output"] as? MongoObject)?.fields?.mapValues { (_, v) ->
                            if (v is MongoObject && v.fields.size == 1) {
                                val (accOp, accExpr) = v.fields.entries.first()
                                MongoAccumulator(accOp, accExpr, v.position)
                            } else {
                                MongoAccumulator("\$sum", MongoScalar(1, v.position), v.position)
                            }
                        }
                    BucketAutoStage(groupBy, buckets, output, granularity, obj.position)
                } ?: GenericStage(stageType, stageValue, obj.position)

            "\$count" ->
                (stageValue as? MongoScalar)?.value?.let {
                    CountStage(it as String, obj.position)
                } ?: GenericStage(stageType, stageValue, obj.position)

            "\$out" ->
                when (stageValue) {
                    is MongoScalar -> OutStage(stageValue.value as? String ?: "", null, obj.position)
                    is MongoObject -> {
                        val db = (stageValue.fields["db"] as? MongoScalar)?.value as? String
                        val coll = (stageValue.fields["coll"] as? MongoScalar)?.value as? String ?: ""
                        OutStage(coll, db, obj.position)
                    }
                    else -> GenericStage(stageType, stageValue, obj.position)
                }

            "\$merge" ->
                (stageValue as? MongoObject)?.let { mergeObj ->
                    val into =
                        when (val intoVal = mergeObj.fields["into"]) {
                            is MongoScalar -> intoVal.value as? String ?: ""
                            is MongoObject -> (intoVal.fields["coll"] as? MongoScalar)?.value as? String ?: ""
                            else -> ""
                        }
                    val on =
                        (mergeObj.fields["on"] as? MongoArray)?.elements?.mapNotNull {
                            (it as? MongoScalar)?.value as? String
                        } ?: (mergeObj.fields["on"] as? MongoScalar)?.value?.let { listOf(it as String) }
                    val whenMatched = (mergeObj.fields["whenMatched"] as? MongoScalar)?.value as? String
                    val whenNotMatched = (mergeObj.fields["whenNotMatched"] as? MongoScalar)?.value as? String
                    MergeStage(into, on, whenMatched, whenNotMatched, obj.position)
                } ?: GenericStage(stageType, stageValue, obj.position)

            "\$sample" ->
                (stageValue as? MongoObject)?.let { sampleObj ->
                    val size =
                        (sampleObj.fields["size"] as? MongoScalar)?.value?.let {
                            (it as? Number)?.toInt()
                        } ?: 1
                    SampleStage(size, obj.position)
                } ?: GenericStage(stageType, stageValue, obj.position)

            "\$redact" ->
                RedactStage(stageValue, obj.position)

            // Generic fallback for unsupported stages
            else -> GenericStage(stageType, stageValue, obj.position)
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

    private val projectModifier: Parser<Pair<String, MongoValue>> by lazy {
        (tokenDot and keywordProject and tokenLparen and objectValue and tokenRparen) map { result ->
            "project" to result.t4
        }
    }

    private val hintModifier: Parser<Pair<String, MongoValue>> by lazy {
        (tokenDot and keywordHint and tokenLparen and (objectValue or tokenString) and tokenRparen) map { result ->
            val value = result.t4
            "hint" to
                when (value) {
                    is MongoObject -> value
                    else -> MongoScalar((value as TokenMatch).text.let { parseString(it) }, (value as TokenMatch).pos())
                }
        }
    }

    private val explainModifier: Parser<Pair<String, MongoValue>> by lazy {
        (tokenDot and keywordExplain and tokenLparen and optional(tokenString) and tokenRparen) map { result ->
            "explain" to MongoScalar(result.t4?.text?.let { parseString(it) } ?: "queryPlanner", result.t2.pos())
        }
    }

    private val countModifier: Parser<Pair<String, MongoValue>> by lazy {
        (tokenDot and keywordCount and tokenLparen and tokenRparen) map { result ->
            "count" to MongoScalar(true, result.t2.pos())
        }
    }

    private val toArrayModifier: Parser<Pair<String, MongoValue>> by lazy {
        (tokenDot and keywordToArray and tokenLparen and tokenRparen) map { result ->
            "toArray" to MongoScalar(true, result.t2.pos())
        }
    }

    private val allModifiers: Parser<Pair<String, MongoValue>> by lazy {
        sortModifier or limitModifier or skipModifier or projectModifier or
            hintModifier or explainModifier or countModifier or toArrayModifier
    }

    // Helper to extract modifiers
    private fun extractModifiers(modifiers: List<Pair<String, MongoValue>>): ModifierData {
        var sort: MongoSort? = null
        var limit: Int? = null
        var skip: Int? = null
        var hint: String? = null
        var explain = false
        var projection: MongoProjection? = null

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
                "hint" ->
                    hint =
                        when (value) {
                            is MongoScalar -> value.value as? String
                            is MongoObject -> value.fields.keys.firstOrNull()
                            else -> null
                        }
                "explain" -> explain = true
                "project" ->
                    projection =
                        (value as MongoObject).let { p ->
                            MongoProjection(
                                p.fields.mapValues { (_, v) -> ((v as? MongoScalar)?.value as? Number)?.toInt() == 1 },
                                p.position,
                            )
                        }
            }
        }
        return ModifierData(sort, limit, skip, hint, explain, projection)
    }

    private data class ModifierData(
        val sort: MongoSort?,
        val limit: Int?,
        val skip: Int?,
        val hint: String?,
        val explain: Boolean,
        val projection: MongoProjection?,
    )

    // db.collection.find({...})
    private val findQuery: Parser<MongoQuery> by lazy {
        (
            keywordDb and tokenDot and collectionName and tokenDot and keywordFind and
                tokenLparen and optional(objectValue) and
                optional(tokenComma and objectValue map { it.t2 }) and tokenRparen and
                zeroOrMore(allModifiers)
        ) map { result ->
            val db = result.t1
            val coll = result.t3
            val filter = result.t7
            val projArg = result.t8
            val modifiers = extractModifiers(result.t10)

            MongoQuery(
                collection = coll,
                operationType = MongoOperationType.FIND,
                filter = filter?.let { parseFilter(it) },
                projection =
                    modifiers.projection ?: projArg?.let { p ->
                        MongoProjection(
                            p.fields.mapValues { (_, v) -> ((v as? MongoScalar)?.value as? Number)?.toInt() == 1 },
                            p.position,
                        )
                    },
                sort = modifiers.sort,
                limit = modifiers.limit,
                skip = modifiers.skip,
                hint = modifiers.hint,
                explain = modifiers.explain,
                position = db.pos(),
            )
        }
    }

    // db.collection.findOne({...})
    private val findOneQuery: Parser<MongoQuery> by lazy {
        (
            keywordDb and tokenDot and collectionName and tokenDot and keywordFindOne and
                tokenLparen and optional(objectValue) and
                optional(tokenComma and objectValue map { it.t2 }) and tokenRparen and
                zeroOrMore(allModifiers)
        ) map { result ->
            val db = result.t1
            val coll = result.t3
            val filter = result.t7
            val projArg = result.t8
            val modifiers = extractModifiers(result.t10)

            MongoQuery(
                collection = coll,
                operationType = MongoOperationType.FIND_ONE,
                filter = filter?.let { parseFilter(it) },
                projection =
                    modifiers.projection ?: projArg?.let { p ->
                        MongoProjection(
                            p.fields.mapValues { (_, v) -> ((v as? MongoScalar)?.value as? Number)?.toInt() == 1 },
                            p.position,
                        )
                    },
                sort = modifiers.sort,
                hint = modifiers.hint,
                explain = modifiers.explain,
                position = db.pos(),
            )
        }
    }

    // db.collection.countDocuments({...})
    private val countDocumentsQuery: Parser<MongoCount> by lazy {
        (
            keywordDb and tokenDot and collectionName and tokenDot and keywordCountDocuments and
                tokenLparen and optional(objectValue) and tokenRparen
        ) map { result ->
            val db = result.t1
            val coll = result.t3
            val filter = result.t7

            MongoCount(
                collection = coll,
                filter = filter?.let { parseFilter(it) },
                position = db.pos(),
            )
        }
    }

    // db.collection.distinct(field, filter)
    private val distinctQuery: Parser<MongoDistinct> by lazy {
        (
            keywordDb and tokenDot and collectionName and tokenDot and keywordDistinct and
                tokenLparen and tokenString and
                optional(tokenComma and objectValue map { it.t2 }) and tokenRparen
        ) map { result ->
            val db = result.t1
            val coll = result.t3
            val field = parseString(result.t7.text)
            val filter = result.t8

            MongoDistinct(
                collection = coll,
                field = field,
                filter = filter?.let { parseFilter(it) },
                position = db.pos(),
            )
        }
    }

    // db.collection.insertOne({...})
    private val insertOneQuery: Parser<MongoInsert> by lazy {
        (
            keywordDb and tokenDot and collectionName and tokenDot and keywordInsertOne and
                tokenLparen and objectValue and tokenRparen
        ) map { result ->
            val db = result.t1
            val coll = result.t3
            val doc = result.t7

            MongoInsert(
                collection = coll,
                operationType = MongoOperationType.INSERT_ONE,
                documents = listOf(doc),
                position = db.pos(),
            )
        }
    }

    // db.collection.insertMany([...])
    private val insertManyQuery: Parser<MongoInsert> by lazy {
        (
            keywordDb and tokenDot and collectionName and tokenDot and keywordInsertMany and
                tokenLparen and arrayValue and tokenRparen
        ) map { result ->
            val db = result.t1
            val coll = result.t3
            val docs = result.t7.elements.filterIsInstance<MongoObject>()

            MongoInsert(
                collection = coll,
                operationType = MongoOperationType.INSERT_MANY,
                documents = docs,
                position = db.pos(),
            )
        }
    }

    // db.collection.updateOne/updateMany(filter, update, options)
    private val updateOneQuery: Parser<MongoUpdate> by lazy {
        (
            keywordDb and tokenDot and collectionName and tokenDot and keywordUpdateOne and
                tokenLparen and objectValue and tokenComma and objectValue and
                optional(tokenComma and objectValue map { it.t2 }) and tokenRparen
        ) map { result ->
            val db = result.t1
            val coll = result.t3
            val filter = result.t7
            val update = result.t9
            val options = result.t10
            val upsert =
                options?.fields?.get("upsert")?.let {
                    (it as? MongoScalar)?.value as? Boolean
                } ?: false

            MongoUpdate(
                collection = coll,
                operationType = MongoOperationType.UPDATE_ONE,
                filter = parseFilter(filter),
                update = update,
                upsert = upsert,
                position = db.pos(),
            )
        }
    }

    private val updateManyQuery: Parser<MongoUpdate> by lazy {
        (
            keywordDb and tokenDot and collectionName and tokenDot and keywordUpdateMany and
                tokenLparen and objectValue and tokenComma and objectValue and
                optional(tokenComma and objectValue map { it.t2 }) and tokenRparen
        ) map { result ->
            val db = result.t1
            val coll = result.t3
            val filter = result.t7
            val update = result.t9
            val options = result.t10
            val upsert =
                options?.fields?.get("upsert")?.let {
                    (it as? MongoScalar)?.value as? Boolean
                } ?: false

            MongoUpdate(
                collection = coll,
                operationType = MongoOperationType.UPDATE_MANY,
                filter = parseFilter(filter),
                update = update,
                upsert = upsert,
                position = db.pos(),
            )
        }
    }

    // db.collection.replaceOne(filter, replacement, options)
    private val replaceOneQuery: Parser<MongoUpdate> by lazy {
        (
            keywordDb and tokenDot and collectionName and tokenDot and keywordReplaceOne and
                tokenLparen and objectValue and tokenComma and objectValue and
                optional(tokenComma and objectValue map { it.t2 }) and tokenRparen
        ) map { result ->
            val db = result.t1
            val coll = result.t3
            val filter = result.t7
            val replacement = result.t9
            val options = result.t10
            val upsert =
                options?.fields?.get("upsert")?.let {
                    (it as? MongoScalar)?.value as? Boolean
                } ?: false

            MongoUpdate(
                collection = coll,
                operationType = MongoOperationType.REPLACE_ONE,
                filter = parseFilter(filter),
                update = replacement,
                upsert = upsert,
                position = db.pos(),
            )
        }
    }

    // db.collection.deleteOne/deleteMany(filter)
    private val deleteOneQuery: Parser<MongoDelete> by lazy {
        (
            keywordDb and tokenDot and collectionName and tokenDot and keywordDeleteOne and
                tokenLparen and objectValue and tokenRparen
        ) map { result ->
            val db = result.t1
            val coll = result.t3
            val filter = result.t7

            MongoDelete(
                collection = coll,
                operationType = MongoOperationType.DELETE_ONE,
                filter = parseFilter(filter),
                position = db.pos(),
            )
        }
    }

    private val deleteManyQuery: Parser<MongoDelete> by lazy {
        (
            keywordDb and tokenDot and collectionName and tokenDot and keywordDeleteMany and
                tokenLparen and objectValue and tokenRparen
        ) map { result ->
            val db = result.t1
            val coll = result.t3
            val filter = result.t7

            MongoDelete(
                collection = coll,
                operationType = MongoOperationType.DELETE_MANY,
                filter = parseFilter(filter),
                position = db.pos(),
            )
        }
    }

    // db.collection.findOneAndUpdate(filter, update, options)
    private val findOneAndUpdateQuery: Parser<MongoFindAndModify> by lazy {
        (
            keywordDb and tokenDot and collectionName and tokenDot and keywordFindOneAndUpdate and
                tokenLparen and objectValue and tokenComma and objectValue and
                optional(tokenComma and objectValue map { it.t2 }) and tokenRparen
        ) map { result ->
            val db = result.t1
            val coll = result.t3
            val filter = result.t7
            val update = result.t9
            val options = result.t10

            MongoFindAndModify(
                collection = coll,
                operationType = MongoOperationType.FIND_ONE_AND_UPDATE,
                filter = parseFilter(filter),
                update = update,
                projection =
                    options?.fields?.get("projection")?.let { p ->
                        (p as? MongoObject)?.let {
                            MongoProjection(
                                it.fields.mapValues { (_, v) -> ((v as? MongoScalar)?.value as? Number)?.toInt() == 1 },
                                it.position,
                            )
                        }
                    },
                sort =
                    options?.fields?.get("sort")?.let { s ->
                        (s as? MongoObject)?.let {
                            MongoSort(
                                it.fields.mapValues { (_, v) -> ((v as? MongoScalar)?.value as? Number)?.toInt() ?: 1 },
                                it.position,
                            )
                        }
                    },
                upsert = options?.fields?.get("upsert")?.let { (it as? MongoScalar)?.value as? Boolean } ?: false,
                returnDocument =
                    options?.fields?.get("returnDocument")?.let {
                        (it as? MongoScalar)?.value as? String
                    } ?: "before",
                position = db.pos(),
            )
        }
    }

    // db.collection.findOneAndDelete(filter, options)
    private val findOneAndDeleteQuery: Parser<MongoFindAndModify> by lazy {
        (
            keywordDb and tokenDot and collectionName and tokenDot and keywordFindOneAndDelete and
                tokenLparen and objectValue and
                optional(tokenComma and objectValue map { it.t2 }) and tokenRparen
        ) map { result ->
            val db = result.t1
            val coll = result.t3
            val filter = result.t7
            val options = result.t8

            MongoFindAndModify(
                collection = coll,
                operationType = MongoOperationType.FIND_ONE_AND_DELETE,
                filter = parseFilter(filter),
                update = null,
                projection =
                    options?.fields?.get("projection")?.let { p ->
                        (p as? MongoObject)?.let {
                            MongoProjection(
                                it.fields.mapValues { (_, v) -> ((v as? MongoScalar)?.value as? Number)?.toInt() == 1 },
                                it.position,
                            )
                        }
                    },
                sort =
                    options?.fields?.get("sort")?.let { s ->
                        (s as? MongoObject)?.let {
                            MongoSort(
                                it.fields.mapValues { (_, v) -> ((v as? MongoScalar)?.value as? Number)?.toInt() ?: 1 },
                                it.position,
                            )
                        }
                    },
                position = db.pos(),
            )
        }
    }

    // db.collection.findOneAndReplace(filter, replacement, options)
    private val findOneAndReplaceQuery: Parser<MongoFindAndModify> by lazy {
        (
            keywordDb and tokenDot and collectionName and tokenDot and keywordFindOneAndReplace and
                tokenLparen and objectValue and tokenComma and objectValue and
                optional(tokenComma and objectValue map { it.t2 }) and tokenRparen
        ) map { result ->
            val db = result.t1
            val coll = result.t3
            val filter = result.t7
            val replacement = result.t9
            val options = result.t10

            MongoFindAndModify(
                collection = coll,
                operationType = MongoOperationType.FIND_ONE_AND_REPLACE,
                filter = parseFilter(filter),
                update = replacement,
                projection =
                    options?.fields?.get("projection")?.let { p ->
                        (p as? MongoObject)?.let {
                            MongoProjection(
                                it.fields.mapValues { (_, v) -> ((v as? MongoScalar)?.value as? Number)?.toInt() == 1 },
                                it.position,
                            )
                        }
                    },
                sort =
                    options?.fields?.get("sort")?.let { s ->
                        (s as? MongoObject)?.let {
                            MongoSort(
                                it.fields.mapValues { (_, v) -> ((v as? MongoScalar)?.value as? Number)?.toInt() ?: 1 },
                                it.position,
                            )
                        }
                    },
                upsert = options?.fields?.get("upsert")?.let { (it as? MongoScalar)?.value as? Boolean } ?: false,
                returnDocument =
                    options?.fields?.get("returnDocument")?.let {
                        (it as? MongoScalar)?.value as? String
                    } ?: "before",
                position = db.pos(),
            )
        }
    }

    // db.collection.createIndex(keys, options)
    private val createIndexQuery: Parser<MongoIndexOp> by lazy {
        (
            keywordDb and tokenDot and collectionName and tokenDot and keywordCreateIndex and
                tokenLparen and objectValue and
                optional(tokenComma and objectValue map { it.t2 }) and tokenRparen
        ) map { result ->
            val db = result.t1
            val coll = result.t3
            val keys = result.t7
            val options = result.t8

            MongoIndexOp(
                collection = coll,
                operationType = MongoOperationType.CREATE_INDEX,
                keys = keys,
                options = options,
                position = db.pos(),
            )
        }
    }

    // db.collection.dropIndex(indexName or keys)
    private val dropIndexQuery: Parser<MongoIndexOp> by lazy {
        (
            keywordDb and tokenDot and collectionName and tokenDot and keywordDropIndex and
                tokenLparen and (tokenString or objectValue) and tokenRparen
        ) map { result ->
            val db = result.t1
            val coll = result.t3
            val indexSpec = result.t7

            MongoIndexOp(
                collection = coll,
                operationType = MongoOperationType.DROP_INDEX,
                keys = (indexSpec as? MongoObject),
                indexName = (indexSpec as? TokenMatch)?.text?.let { parseString(it) },
                position = db.pos(),
            )
        }
    }

    // db.collection.getIndexes()
    private val getIndexesQuery: Parser<MongoIndexOp> by lazy {
        (
            keywordDb and tokenDot and collectionName and tokenDot and keywordGetIndexes and
                tokenLparen and tokenRparen
        ) map { result ->
            val db = result.t1
            val coll = result.t3

            MongoIndexOp(
                collection = coll,
                operationType = MongoOperationType.GET_INDEXES,
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

    override val rootParser: Parser<MongoNode> by lazy {
        // Order matters: more specific parsers first
        findOneQuery or
            findQuery or
            countDocumentsQuery or
            distinctQuery or
            aggregateQuery or
            insertOneQuery or
            insertManyQuery or
            updateOneQuery or
            updateManyQuery or
            replaceOneQuery or
            deleteOneQuery or
            deleteManyQuery or
            findOneAndUpdateQuery or
            findOneAndDeleteQuery or
            findOneAndReplaceQuery or
            createIndexQuery or
            dropIndexQuery or
            getIndexesQuery or
            jsonQuery
    }
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
