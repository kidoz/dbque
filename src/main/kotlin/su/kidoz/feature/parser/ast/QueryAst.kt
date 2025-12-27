package su.kidoz.feature.parser.ast

/**
 * Base interface for all AST nodes across SQL, MongoDB, and Elasticsearch
 */
sealed interface QueryNode {
    val position: SourcePosition
    val children: List<QueryNode> get() = emptyList()
}

data class SourcePosition(
    val start: Int,
    val end: Int,
    val line: Int = 0,
    val column: Int = 0,
) {
    companion object {
        val EMPTY = SourcePosition(0, 0)
    }
}

// ============================================================================
// Common AST Nodes
// ============================================================================

sealed interface Expression : QueryNode

sealed interface Literal : Expression {
    val value: Any?
}

data class StringLiteral(
    override val value: String,
    override val position: SourcePosition,
) : Literal

data class NumberLiteral(
    override val value: Number,
    override val position: SourcePosition,
) : Literal

data class BooleanLiteral(
    override val value: Boolean,
    override val position: SourcePosition,
) : Literal

data class NullLiteral(
    override val position: SourcePosition,
) : Literal {
    override val value: Any? = null
}

data class Identifier(
    val name: String,
    val quoted: Boolean = false,
    override val position: SourcePosition,
) : Expression

data class QualifiedIdentifier(
    val parts: List<Identifier>,
    override val position: SourcePosition,
) : Expression {
    override val children: List<QueryNode> get() = parts
    val fullName: String get() = parts.joinToString(".") { it.name }
}

// ============================================================================
// Operators
// ============================================================================

enum class ComparisonOperator(
    val symbol: String,
) {
    EQ("="),
    NEQ("!="),
    LT("<"),
    LTE("<="),
    GT(">"),
    GTE(">="),
    LIKE("LIKE"),
    ILIKE("ILIKE"),
    IN("IN"),
    NOT_IN("NOT IN"),
    IS_NULL("IS NULL"),
    IS_NOT_NULL("IS NOT NULL"),
    BETWEEN("BETWEEN"),
    REGEX("~"),
    NOT_REGEX("!~"),
}

enum class LogicalOperator(
    val symbol: String,
) {
    AND("AND"),
    OR("OR"),
    NOT("NOT"),
}

enum class ArithmeticOperator(
    val symbol: String,
) {
    ADD("+"),
    SUB("-"),
    MUL("*"),
    DIV("/"),
    MOD("%"),
}

// ============================================================================
// Expression Nodes
// ============================================================================

data class BinaryExpression(
    val left: Expression,
    val operator: String,
    val right: Expression,
    override val position: SourcePosition,
) : Expression {
    override val children: List<QueryNode> get() = listOf(left, right)
}

data class UnaryExpression(
    val operator: String,
    val operand: Expression,
    override val position: SourcePosition,
) : Expression {
    override val children: List<QueryNode> get() = listOf(operand)
}

data class FunctionCall(
    val name: Identifier,
    val arguments: List<Expression>,
    val distinct: Boolean = false,
    override val position: SourcePosition,
) : Expression {
    override val children: List<QueryNode> get() = listOf(name) + arguments
}

data class ListExpression(
    val elements: List<Expression>,
    override val position: SourcePosition,
) : Expression {
    override val children: List<QueryNode> get() = elements
}

data class CaseExpression(
    val operand: Expression?,
    val whenClauses: List<WhenClause>,
    val elseResult: Expression?,
    override val position: SourcePosition,
) : Expression

data class WhenClause(
    val condition: Expression,
    val result: Expression,
    override val position: SourcePosition,
) : QueryNode {
    override val children: List<QueryNode> get() = listOf(condition, result)
}

// ============================================================================
// SQL-Specific Nodes
// ============================================================================

sealed interface SqlStatement : QueryNode

data class SelectStatement(
    val columns: List<SelectColumn>,
    val from: FromClause?,
    val joins: List<JoinClause> = emptyList(),
    val where: Expression? = null,
    val groupBy: List<Expression> = emptyList(),
    val having: Expression? = null,
    val orderBy: List<OrderByClause> = emptyList(),
    val limit: Expression? = null,
    val offset: Expression? = null,
    override val position: SourcePosition,
) : SqlStatement,
    Expression {
    override val children: List<QueryNode> get() =
        buildList {
            addAll(columns)
            from?.let { add(it) }
            addAll(joins)
            where?.let { add(it) }
            addAll(groupBy)
            having?.let { add(it) }
            addAll(orderBy)
            limit?.let { add(it) }
            offset?.let { add(it) }
        }
}

data class SelectColumn(
    val expression: Expression,
    val alias: Identifier? = null,
    override val position: SourcePosition,
) : QueryNode {
    override val children: List<QueryNode> get() = listOfNotNull(expression, alias)
}

data class FromClause(
    val source: TableReference,
    override val position: SourcePosition,
) : QueryNode {
    override val children: List<QueryNode> get() = listOf(source)
}

sealed interface TableReference : QueryNode

data class TableName(
    val name: QualifiedIdentifier,
    val alias: Identifier? = null,
    override val position: SourcePosition,
) : TableReference {
    override val children: List<QueryNode> get() = listOfNotNull(name, alias)
}

data class SubqueryReference(
    val subquery: SelectStatement,
    val alias: Identifier,
    override val position: SourcePosition,
) : TableReference {
    override val children: List<QueryNode> get() = listOf(subquery, alias)
}

enum class JoinType { INNER, LEFT, RIGHT, FULL, CROSS }

data class JoinClause(
    val type: JoinType,
    val table: TableReference,
    val condition: Expression?,
    override val position: SourcePosition,
) : QueryNode {
    override val children: List<QueryNode> get() = listOfNotNull(table, condition)
}

data class OrderByClause(
    val expression: Expression,
    val ascending: Boolean = true,
    val nullsFirst: Boolean? = null,
    override val position: SourcePosition,
) : QueryNode {
    override val children: List<QueryNode> get() = listOf(expression)
}

data class InsertStatement(
    val table: QualifiedIdentifier,
    val columns: List<Identifier>?,
    val values: List<List<Expression>>?,
    val select: SelectStatement?,
    val onConflict: OnConflictClause? = null,
    override val position: SourcePosition,
) : SqlStatement

data class OnConflictClause(
    val columns: List<Identifier>,
    val action: ConflictAction,
    override val position: SourcePosition,
) : QueryNode

sealed interface ConflictAction

data object DoNothing : ConflictAction

data class DoUpdate(
    val assignments: List<Assignment>,
) : ConflictAction

data class UpdateStatement(
    val table: QualifiedIdentifier,
    val assignments: List<Assignment>,
    val from: FromClause? = null,
    val where: Expression? = null,
    override val position: SourcePosition,
) : SqlStatement

data class Assignment(
    val column: Identifier,
    val value: Expression,
    override val position: SourcePosition,
) : QueryNode {
    override val children: List<QueryNode> get() = listOf(column, value)
}

data class DeleteStatement(
    val table: QualifiedIdentifier,
    val where: Expression? = null,
    override val position: SourcePosition,
) : SqlStatement

// ============================================================================
// MongoDB-Specific Nodes
// ============================================================================

sealed interface MongoNode : QueryNode

/**
 * MongoDB operation type enum
 */
enum class MongoOperationType {
    FIND,
    FIND_ONE,
    COUNT_DOCUMENTS,
    DISTINCT,
    INSERT_ONE,
    INSERT_MANY,
    UPDATE_ONE,
    UPDATE_MANY,
    DELETE_ONE,
    DELETE_MANY,
    REPLACE_ONE,
    FIND_ONE_AND_UPDATE,
    FIND_ONE_AND_DELETE,
    FIND_ONE_AND_REPLACE,
    CREATE_INDEX,
    DROP_INDEX,
    GET_INDEXES,
}

data class MongoQuery(
    val collection: String?,
    val operationType: MongoOperationType = MongoOperationType.FIND,
    val filter: MongoFilter?,
    val projection: MongoProjection? = null,
    val sort: MongoSort? = null,
    val limit: Int? = null,
    val skip: Int? = null,
    val hint: String? = null,
    val explain: Boolean = false,
    override val position: SourcePosition,
) : MongoNode

/**
 * MongoDB count operation result
 */
data class MongoCount(
    val collection: String,
    val filter: MongoFilter?,
    override val position: SourcePosition,
) : MongoNode

/**
 * MongoDB distinct operation
 */
data class MongoDistinct(
    val collection: String,
    val field: String,
    val filter: MongoFilter?,
    override val position: SourcePosition,
) : MongoNode

/**
 * MongoDB update operation (updateOne, updateMany, replaceOne)
 */
data class MongoUpdate(
    val collection: String,
    val operationType: MongoOperationType,
    val filter: MongoFilter,
    val update: MongoValue,
    val upsert: Boolean = false,
    override val position: SourcePosition,
) : MongoNode

/**
 * MongoDB findOneAnd* operations
 */
data class MongoFindAndModify(
    val collection: String,
    val operationType: MongoOperationType,
    val filter: MongoFilter,
    val update: MongoValue?,
    val projection: MongoProjection? = null,
    val sort: MongoSort? = null,
    val upsert: Boolean = false,
    val returnDocument: String = "before", // "before" or "after"
    override val position: SourcePosition,
) : MongoNode

/**
 * MongoDB insert operation
 */
data class MongoInsert(
    val collection: String,
    val operationType: MongoOperationType,
    val documents: List<MongoObject>,
    override val position: SourcePosition,
) : MongoNode

/**
 * MongoDB delete operation
 */
data class MongoDelete(
    val collection: String,
    val operationType: MongoOperationType,
    val filter: MongoFilter,
    override val position: SourcePosition,
) : MongoNode

/**
 * MongoDB index operations
 */
data class MongoIndexOp(
    val collection: String,
    val operationType: MongoOperationType,
    val keys: MongoObject? = null,
    val options: MongoObject? = null,
    val indexName: String? = null,
    override val position: SourcePosition,
) : MongoNode

sealed interface MongoFilter : MongoNode

data class MongoFieldFilter(
    val field: String,
    val operator: MongoOperator,
    val value: MongoValue,
    override val position: SourcePosition,
) : MongoFilter

data class MongoLogicalFilter(
    val operator: MongoLogicalOperator,
    val filters: List<MongoFilter>,
    override val position: SourcePosition,
) : MongoFilter {
    override val children: List<QueryNode> get() = filters
}

enum class MongoOperator(
    val symbol: String,
) {
    EQ("\$eq"),
    NE("\$ne"),
    GT("\$gt"),
    GTE("\$gte"),
    LT("\$lt"),
    LTE("\$lte"),
    IN("\$in"),
    NIN("\$nin"),
    EXISTS("\$exists"),
    TYPE("\$type"),
    REGEX("\$regex"),
    SIZE("\$size"),
    ALL("\$all"),
    ELEM_MATCH("\$elemMatch"),
}

enum class MongoLogicalOperator(
    val symbol: String,
) {
    AND("\$and"),
    OR("\$or"),
    NOR("\$nor"),
    NOT("\$not"),
}

sealed interface MongoValue : MongoNode

data class MongoScalar(
    val value: Any?,
    override val position: SourcePosition,
) : MongoValue

data class MongoArray(
    val elements: List<MongoValue>,
    override val position: SourcePosition,
) : MongoValue

data class MongoObject(
    val fields: Map<String, MongoValue>,
    override val position: SourcePosition,
) : MongoValue

data class MongoProjection(
    val fields: Map<String, Boolean>,
    override val position: SourcePosition,
) : MongoNode

data class MongoSort(
    val fields: Map<String, Int>,
    override val position: SourcePosition,
) : MongoNode

data class MongoAggregation(
    val collection: String?,
    val pipeline: List<MongoStage>,
    override val position: SourcePosition,
) : MongoNode {
    override val children: List<QueryNode> get() = pipeline
}

sealed interface MongoStage : MongoNode

data class MatchStage(
    val filter: MongoFilter,
    override val position: SourcePosition,
) : MongoStage

data class ProjectStage(
    val projection: MongoProjection,
    override val position: SourcePosition,
) : MongoStage

data class GroupStage(
    val id: MongoValue,
    val accumulators: Map<String, MongoAccumulator>,
    override val position: SourcePosition,
) : MongoStage

data class SortStage(
    val sort: MongoSort,
    override val position: SourcePosition,
) : MongoStage

data class LimitStage(
    val limit: Int,
    override val position: SourcePosition,
) : MongoStage

data class SkipStage(
    val skip: Int,
    override val position: SourcePosition,
) : MongoStage

data class LookupStage(
    val from: String,
    val localField: String,
    val foreignField: String,
    val alias: String,
    override val position: SourcePosition,
) : MongoStage

data class UnwindStage(
    val path: String,
    val preserveNullAndEmpty: Boolean = false,
    override val position: SourcePosition,
) : MongoStage

/**
 * $addFields / $set stage - add computed fields
 */
data class AddFieldsStage(
    val fields: Map<String, MongoValue>,
    override val position: SourcePosition,
) : MongoStage

/**
 * $replaceRoot / $replaceWith stage - replace document
 */
data class ReplaceRootStage(
    val newRoot: MongoValue,
    override val position: SourcePosition,
) : MongoStage

/**
 * $facet stage - multiple pipelines
 */
data class FacetStage(
    val facets: Map<String, List<MongoStage>>,
    override val position: SourcePosition,
) : MongoStage

/**
 * $bucket stage - group by boundaries
 */
data class BucketStage(
    val groupBy: MongoValue,
    val boundaries: MongoArray,
    val defaultBucket: MongoValue? = null,
    val output: Map<String, MongoAccumulator>? = null,
    override val position: SourcePosition,
) : MongoStage

/**
 * $bucketAuto stage - automatic bucketing
 */
data class BucketAutoStage(
    val groupBy: MongoValue,
    val buckets: Int,
    val output: Map<String, MongoAccumulator>? = null,
    val granularity: String? = null,
    override val position: SourcePosition,
) : MongoStage

/**
 * $count stage - count documents in pipeline
 */
data class CountStage(
    val field: String,
    override val position: SourcePosition,
) : MongoStage

/**
 * $out stage - write to collection
 */
data class OutStage(
    val collection: String,
    val db: String? = null,
    override val position: SourcePosition,
) : MongoStage

/**
 * $merge stage - merge into collection
 */
data class MergeStage(
    val into: String,
    val on: List<String>? = null,
    val whenMatched: String? = null,
    val whenNotMatched: String? = null,
    override val position: SourcePosition,
) : MongoStage

/**
 * $sample stage - random sample
 */
data class SampleStage(
    val size: Int,
    override val position: SourcePosition,
) : MongoStage

/**
 * $redact stage - conditional field access
 */
data class RedactStage(
    val expression: MongoValue,
    override val position: SourcePosition,
) : MongoStage

/**
 * Generic stage for unsupported/custom stages
 */
data class GenericStage(
    val name: String,
    val value: MongoValue,
    override val position: SourcePosition,
) : MongoStage

data class MongoAccumulator(
    val operator: String,
    val expression: MongoValue,
    override val position: SourcePosition,
) : MongoNode

// ============================================================================
// Elasticsearch-Specific Nodes
// ============================================================================

sealed interface EsNode : QueryNode

data class EsQuery(
    val index: String?,
    val query: EsQueryClause?,
    val from: Int? = null,
    val size: Int? = null,
    val sort: List<EsSort>? = null,
    val source: EsSource? = null,
    val aggregations: Map<String, EsAggregation>? = null,
    override val position: SourcePosition,
) : EsNode

sealed interface EsQueryClause : EsNode

data class EsBoolQuery(
    val must: List<EsQueryClause> = emptyList(),
    val should: List<EsQueryClause> = emptyList(),
    val mustNot: List<EsQueryClause> = emptyList(),
    val filter: List<EsQueryClause> = emptyList(),
    val minimumShouldMatch: Int? = null,
    override val position: SourcePosition,
) : EsQueryClause {
    override val children: List<QueryNode> get() = must + should + mustNot + filter
}

data class EsMatchQuery(
    val field: String,
    val query: String,
    val operator: String? = null,
    val fuzziness: String? = null,
    override val position: SourcePosition,
) : EsQueryClause

data class EsTermQuery(
    val field: String,
    val value: Any,
    val boost: Float? = null,
    override val position: SourcePosition,
) : EsQueryClause

data class EsTermsQuery(
    val field: String,
    val values: List<Any>,
    override val position: SourcePosition,
) : EsQueryClause

data class EsRangeQuery(
    val field: String,
    val gt: Any? = null,
    val gte: Any? = null,
    val lt: Any? = null,
    val lte: Any? = null,
    val format: String? = null,
    override val position: SourcePosition,
) : EsQueryClause

data class EsExistsQuery(
    val field: String,
    override val position: SourcePosition,
) : EsQueryClause

data class EsWildcardQuery(
    val field: String,
    val value: String,
    override val position: SourcePosition,
) : EsQueryClause

data class EsRegexpQuery(
    val field: String,
    val value: String,
    val flags: String? = null,
    override val position: SourcePosition,
) : EsQueryClause

data class EsMatchAllQuery(
    val boost: Float? = null,
    override val position: SourcePosition,
) : EsQueryClause

data class EsNestedQuery(
    val path: String,
    val query: EsQueryClause,
    val scoreMode: String? = null,
    override val position: SourcePosition,
) : EsQueryClause

data class EsSort(
    val field: String,
    val order: String = "asc",
    val mode: String? = null,
    override val position: SourcePosition,
) : EsNode

data class EsSource(
    val includes: List<String>? = null,
    val excludes: List<String>? = null,
    override val position: SourcePosition,
) : EsNode

sealed interface EsAggregation : EsNode

data class EsTermsAgg(
    val field: String,
    val size: Int? = null,
    val subAggs: Map<String, EsAggregation>? = null,
    override val position: SourcePosition,
) : EsAggregation

data class EsAvgAgg(
    val field: String,
    override val position: SourcePosition,
) : EsAggregation

data class EsSumAgg(
    val field: String,
    override val position: SourcePosition,
) : EsAggregation

data class EsMinAgg(
    val field: String,
    override val position: SourcePosition,
) : EsAggregation

data class EsMaxAgg(
    val field: String,
    override val position: SourcePosition,
) : EsAggregation

data class EsCountAgg(
    val field: String?,
    override val position: SourcePosition,
) : EsAggregation

data class EsDateHistogramAgg(
    val field: String,
    val interval: String,
    val format: String? = null,
    val subAggs: Map<String, EsAggregation>? = null,
    override val position: SourcePosition,
) : EsAggregation
