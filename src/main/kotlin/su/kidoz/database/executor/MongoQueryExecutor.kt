package su.kidoz.database.executor

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.bson.Document
import su.kidoz.core.model.QueryExecutionResult
import su.kidoz.core.model.QueryResult
import su.kidoz.core.model.ResultColumn
import su.kidoz.database.MongoDatabaseConnection
import su.kidoz.feature.parser.ast.*
import su.kidoz.feature.parser.mongo.MongoParser
import java.sql.Types

/**
 * MongoDB query executor that parses MongoDB shell syntax and executes queries.
 */
class MongoQueryExecutor {
    private val logger = KotlinLogging.logger {}

    suspend fun execute(
        connection: MongoDatabaseConnection,
        query: String,
    ): QueryExecutionResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            try {
                val parseResult = MongoParser.parse(query.trim())
                if (parseResult.isFailure) {
                    return@withContext QueryExecutionResult.Error(
                        message = "Parse error: ${parseResult.exceptionOrNull()?.message}",
                    )
                }

                val ast = parseResult.getOrThrow()
                executeNode(connection, ast, query, startTime)
            } catch (e: Exception) {
                logger.error(e) { "MongoDB query execution failed" }
                QueryExecutionResult.Error(
                    message = e.message ?: "Query execution failed",
                )
            }
        }

    private suspend fun executeNode(
        connection: MongoDatabaseConnection,
        node: MongoNode,
        originalQuery: String,
        startTime: Long,
    ): QueryExecutionResult =
        when (node) {
            is MongoQuery -> executeFind(connection, node, originalQuery, startTime)
            is MongoAggregation -> executeAggregate(connection, node, originalQuery, startTime)
            is MongoInsert -> executeInsert(connection, node, originalQuery, startTime)
            is MongoUpdate -> executeUpdate(connection, node, originalQuery, startTime)
            is MongoDelete -> executeDelete(connection, node, originalQuery, startTime)
            is MongoCount -> executeCount(connection, node, originalQuery, startTime)
            is MongoDistinct -> executeDistinct(connection, node, originalQuery, startTime)
            is MongoFindAndModify -> executeFindAndModify(connection, node, originalQuery, startTime)
            is MongoIndexOp -> executeIndexOp(connection, node, originalQuery, startTime)
            else -> QueryExecutionResult.Error("Unsupported operation type: ${node::class.simpleName}")
        }

    private suspend fun executeFind(
        connection: MongoDatabaseConnection,
        query: MongoQuery,
        originalQuery: String,
        startTime: Long,
    ): QueryExecutionResult {
        val collectionName = query.collection ?: return QueryExecutionResult.Error("Collection name required")
        val collection = connection.database.getCollection<Document>(collectionName)

        val filter = query.filter?.let { filterToDocument(it) } ?: Document()
        var cursor = collection.find(filter)

        // Apply modifiers
        query.sort?.let { sort ->
            cursor = cursor.sort(Document(sort.fields.mapValues { it.value }))
        }
        query.skip?.let { cursor = cursor.skip(it) }
        query.limit?.let { cursor = cursor.limit(it) }
        query.projection?.let { proj ->
            cursor = cursor.projection(Document(proj.fields.mapValues { if (it.value) 1 else 0 }))
        }

        val documents = cursor.toList()
        val executionTime = System.currentTimeMillis() - startTime

        return documentsToResult(documents, originalQuery, executionTime)
    }

    private suspend fun executeAggregate(
        connection: MongoDatabaseConnection,
        aggregation: MongoAggregation,
        originalQuery: String,
        startTime: Long,
    ): QueryExecutionResult {
        val collectionName = aggregation.collection ?: return QueryExecutionResult.Error("Collection name required")
        val collection = connection.database.getCollection<Document>(collectionName)

        val pipeline = aggregation.pipeline.map { stageToDocument(it) }
        val documents = collection.aggregate(pipeline).toList()
        val executionTime = System.currentTimeMillis() - startTime

        return documentsToResult(documents, originalQuery, executionTime)
    }

    private suspend fun executeInsert(
        connection: MongoDatabaseConnection,
        insert: MongoInsert,
        originalQuery: String,
        startTime: Long,
    ): QueryExecutionResult {
        val collection = connection.database.getCollection<Document>(insert.collection)

        val documentsToInsert = insert.documents.map { mongoObjectToDocument(it) }
        val result =
            if (insert.operationType == MongoOperationType.INSERT_ONE && documentsToInsert.size == 1) {
                collection.insertOne(documentsToInsert.first())
                1
            } else {
                collection.insertMany(documentsToInsert)
                documentsToInsert.size
            }

        val executionTime = System.currentTimeMillis() - startTime

        return QueryExecutionResult.Success(
            QueryResult(
                columns = listOf(ResultColumn("result", "result", "VARCHAR", Types.VARCHAR, 50, 0, 0, false)),
                rows = listOf(listOf("Inserted $result document(s)")),
                rowCount = 1,
                affectedRows = result,
                executionTimeMs = executionTime,
                query = originalQuery,
                isResultSet = false,
            ),
        )
    }

    private suspend fun executeUpdate(
        connection: MongoDatabaseConnection,
        update: MongoUpdate,
        originalQuery: String,
        startTime: Long,
    ): QueryExecutionResult {
        val collection = connection.database.getCollection<Document>(update.collection)

        val filter = filterToDocument(update.filter)
        val updateDoc = (mongoValueToAny(update.update) as? Document) ?: Document()

        val result =
            when (update.operationType) {
                MongoOperationType.UPDATE_ONE -> collection.updateOne(filter, updateDoc).modifiedCount
                MongoOperationType.UPDATE_MANY -> collection.updateMany(filter, updateDoc).modifiedCount
                MongoOperationType.REPLACE_ONE -> collection.replaceOne(filter, updateDoc).modifiedCount
                else -> 0L
            }

        val executionTime = System.currentTimeMillis() - startTime

        return QueryExecutionResult.Success(
            QueryResult(
                columns = listOf(ResultColumn("result", "result", "VARCHAR", Types.VARCHAR, 50, 0, 0, false)),
                rows = listOf(listOf("Modified $result document(s)")),
                rowCount = 1,
                affectedRows = result.toInt(),
                executionTimeMs = executionTime,
                query = originalQuery,
                isResultSet = false,
            ),
        )
    }

    private suspend fun executeDelete(
        connection: MongoDatabaseConnection,
        delete: MongoDelete,
        originalQuery: String,
        startTime: Long,
    ): QueryExecutionResult {
        val collection = connection.database.getCollection<Document>(delete.collection)

        val filter = filterToDocument(delete.filter)

        val result =
            when (delete.operationType) {
                MongoOperationType.DELETE_ONE -> collection.deleteOne(filter).deletedCount
                MongoOperationType.DELETE_MANY -> collection.deleteMany(filter).deletedCount
                else -> 0L
            }

        val executionTime = System.currentTimeMillis() - startTime

        return QueryExecutionResult.Success(
            QueryResult(
                columns = listOf(ResultColumn("result", "result", "VARCHAR", Types.VARCHAR, 50, 0, 0, false)),
                rows = listOf(listOf("Deleted $result document(s)")),
                rowCount = 1,
                affectedRows = result.toInt(),
                executionTimeMs = executionTime,
                query = originalQuery,
                isResultSet = false,
            ),
        )
    }

    private suspend fun executeCount(
        connection: MongoDatabaseConnection,
        count: MongoCount,
        originalQuery: String,
        startTime: Long,
    ): QueryExecutionResult {
        val collection = connection.database.getCollection<Document>(count.collection)

        val filter = count.filter?.let { filterToDocument(it) } ?: Document()
        val result = collection.countDocuments(filter)
        val executionTime = System.currentTimeMillis() - startTime

        return QueryExecutionResult.Success(
            QueryResult(
                columns = listOf(ResultColumn("count", "count", "BIGINT", Types.BIGINT, 20, 0, 0, false)),
                rows = listOf(listOf(result)),
                rowCount = 1,
                executionTimeMs = executionTime,
                query = originalQuery,
            ),
        )
    }

    private suspend fun executeDistinct(
        connection: MongoDatabaseConnection,
        distinct: MongoDistinct,
        originalQuery: String,
        startTime: Long,
    ): QueryExecutionResult {
        val collection = connection.database.getCollection<Document>(distinct.collection)

        val filter = distinct.filter?.let { filterToDocument(it) } ?: Document()
        val values = collection.distinct<Any>(distinct.field, filter).toList()
        val executionTime = System.currentTimeMillis() - startTime

        return QueryExecutionResult.Success(
            QueryResult(
                columns = listOf(ResultColumn(distinct.field, distinct.field, "VARCHAR", Types.VARCHAR, 100, 0, 0, true)),
                rows = values.map { listOf(it.toString()) },
                rowCount = values.size,
                executionTimeMs = executionTime,
                query = originalQuery,
            ),
        )
    }

    private suspend fun executeFindAndModify(
        connection: MongoDatabaseConnection,
        op: MongoFindAndModify,
        originalQuery: String,
        startTime: Long,
    ): QueryExecutionResult {
        val collection = connection.database.getCollection<Document>(op.collection)

        val filter = filterToDocument(op.filter)
        val update = op.update?.let { mongoValueToAny(it) as? Document }

        val result: Document? =
            when (op.operationType) {
                MongoOperationType.FIND_ONE_AND_DELETE -> collection.findOneAndDelete(filter)
                MongoOperationType.FIND_ONE_AND_UPDATE -> update?.let { collection.findOneAndUpdate(filter, it) }
                MongoOperationType.FIND_ONE_AND_REPLACE -> update?.let { collection.findOneAndReplace(filter, it) }
                else -> null
            }

        val executionTime = System.currentTimeMillis() - startTime

        return if (result != null) {
            documentsToResult(listOf(result), originalQuery, executionTime)
        } else {
            QueryExecutionResult.Success(
                QueryResult(
                    columns = listOf(ResultColumn("result", "result", "VARCHAR", Types.VARCHAR, 50, 0, 0, false)),
                    rows = listOf(listOf("No document matched the filter")),
                    rowCount = 1,
                    executionTimeMs = executionTime,
                    query = originalQuery,
                    isResultSet = false,
                ),
            )
        }
    }

    private suspend fun executeIndexOp(
        connection: MongoDatabaseConnection,
        op: MongoIndexOp,
        originalQuery: String,
        startTime: Long,
    ): QueryExecutionResult {
        val collection = connection.database.getCollection<Document>(op.collection)
        val executionTime: Long

        return when (op.operationType) {
            MongoOperationType.CREATE_INDEX -> {
                val keys = op.keys?.let { mongoObjectToDocument(it) } ?: Document()
                val result = collection.createIndex(keys)
                executionTime = System.currentTimeMillis() - startTime
                QueryExecutionResult.Success(
                    QueryResult(
                        columns = listOf(ResultColumn("result", "result", "VARCHAR", Types.VARCHAR, 100, 0, 0, false)),
                        rows = listOf(listOf("Index created: $result")),
                        rowCount = 1,
                        executionTimeMs = executionTime,
                        query = originalQuery,
                        isResultSet = false,
                    ),
                )
            }
            MongoOperationType.DROP_INDEX -> {
                if (op.indexName != null) {
                    collection.dropIndex(op.indexName)
                } else if (op.keys != null) {
                    collection.dropIndex(mongoObjectToDocument(op.keys))
                }
                executionTime = System.currentTimeMillis() - startTime
                QueryExecutionResult.Success(
                    QueryResult(
                        columns = listOf(ResultColumn("result", "result", "VARCHAR", Types.VARCHAR, 50, 0, 0, false)),
                        rows = listOf(listOf("Index dropped")),
                        rowCount = 1,
                        executionTimeMs = executionTime,
                        query = originalQuery,
                        isResultSet = false,
                    ),
                )
            }
            MongoOperationType.GET_INDEXES -> {
                val indexes = collection.listIndexes().toList()
                executionTime = System.currentTimeMillis() - startTime
                documentsToResult(indexes, originalQuery, executionTime)
            }
            else -> QueryExecutionResult.Error("Unknown index operation")
        }
    }

    // ==================== Helpers ====================

    private fun documentsToResult(
        documents: List<Document>,
        query: String,
        executionTime: Long,
    ): QueryExecutionResult {
        if (documents.isEmpty()) {
            return QueryExecutionResult.Success(
                QueryResult(
                    columns = emptyList(),
                    rows = emptyList(),
                    rowCount = 0,
                    executionTimeMs = executionTime,
                    query = query,
                ),
            )
        }

        // Collect all unique keys across all documents
        val allKeys = documents.flatMap { it.keys }.distinct().sorted()

        val columns =
            allKeys.map { key ->
                ResultColumn(
                    name = key,
                    label = key,
                    typeName = "JSON",
                    jdbcType = Types.VARCHAR,
                    displaySize = 100,
                    precision = 0,
                    scale = 0,
                    nullable = true,
                )
            }

        val rows =
            documents.map { doc ->
                allKeys.map { key ->
                    val value = doc[key]
                    when (value) {
                        is Document -> value.toJson()
                        is List<*> -> value.toString()
                        else -> value
                    }
                }
            }

        return QueryExecutionResult.Success(
            QueryResult(
                columns = columns,
                rows = rows,
                rowCount = documents.size,
                executionTimeMs = executionTime,
                query = query,
            ),
        )
    }

    private fun filterToDocument(filter: MongoFilter): Document =
        when (filter) {
            is MongoFieldFilter -> {
                val operatorDoc =
                    when (filter.operator) {
                        MongoOperator.EQ -> filter.value.let { mongoValueToAny(it) }
                        else -> Document(filter.operator.symbol, mongoValueToAny(filter.value))
                    }
                Document(filter.field, operatorDoc)
            }
            is MongoLogicalFilter -> {
                val subDocs = filter.filters.map { filterToDocument(it) }
                when (filter.operator) {
                    MongoLogicalOperator.AND -> Document("\$and", subDocs)
                    MongoLogicalOperator.OR -> Document("\$or", subDocs)
                    MongoLogicalOperator.NOR -> Document("\$nor", subDocs)
                    MongoLogicalOperator.NOT -> Document("\$not", subDocs.firstOrNull() ?: Document())
                }
            }
        }

    private fun stageToDocument(stage: MongoStage): Document =
        when (stage) {
            is MatchStage -> Document("\$match", filterToDocument(stage.filter))
            is ProjectStage ->
                Document(
                    "\$project",
                    Document(stage.projection.fields.mapValues { if (it.value) 1 else 0 }),
                )
            is GroupStage -> {
                val groupDoc = Document("_id", mongoValueToAny(stage.id))
                stage.accumulators.forEach { (field, acc) ->
                    groupDoc[field] = Document(acc.operator, mongoValueToAny(acc.expression))
                }
                Document("\$group", groupDoc)
            }
            is SortStage -> Document("\$sort", Document(stage.sort.fields.mapValues { it.value }))
            is LimitStage -> Document("\$limit", stage.limit)
            is SkipStage -> Document("\$skip", stage.skip)
            is LookupStage ->
                Document(
                    "\$lookup",
                    Document()
                        .append("from", stage.from)
                        .append("localField", stage.localField)
                        .append("foreignField", stage.foreignField)
                        .append("as", stage.alias),
                )
            is UnwindStage ->
                if (stage.preserveNullAndEmpty) {
                    Document(
                        "\$unwind",
                        Document()
                            .append("path", stage.path)
                            .append("preserveNullAndEmptyArrays", true),
                    )
                } else {
                    Document("\$unwind", stage.path)
                }
            is AddFieldsStage -> Document("\$addFields", Document(stage.fields.mapValues { mongoValueToAny(it.value) }))
            is CountStage -> Document("\$count", stage.field)
            is SampleStage -> Document("\$sample", Document("size", stage.size))
            is GenericStage -> Document(stage.name, mongoValueToAny(stage.value))
            else -> Document()
        }

    private fun mongoValueToAny(value: MongoValue): Any? =
        when (value) {
            is MongoScalar -> value.value
            is MongoObject -> mongoObjectToDocument(value)
            is MongoArray -> value.elements.map { mongoValueToAny(it) }
        }

    private fun mongoObjectToDocument(obj: MongoObject): Document {
        val doc = Document()
        obj.fields.forEach { (key, value) ->
            doc[key] = mongoValueToAny(value)
        }
        return doc
    }
}
