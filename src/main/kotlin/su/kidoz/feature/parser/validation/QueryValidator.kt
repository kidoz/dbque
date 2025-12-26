package su.kidoz.feature.parser.validation

import su.kidoz.feature.parser.ast.*
import su.kidoz.feature.parser.elasticsearch.ElasticsearchParser
import su.kidoz.feature.parser.mongo.MongoParser
import su.kidoz.feature.parser.sql.SqlDialect
import su.kidoz.feature.parser.sql.SqlParser

/**
 * Validation issue severity
 */
enum class IssueSeverity {
    ERROR,
    WARNING,
    INFO,
    HINT,
}

/**
 * Validation issue
 */
data class ValidationIssue(
    val message: String,
    val severity: IssueSeverity,
    val position: SourcePosition,
    val code: String,
    val suggestion: String? = null,
)

/**
 * Validation result
 */
data class ValidationResult(
    val issues: List<ValidationIssue>,
    val ast: QueryNode?,
) {
    val hasErrors: Boolean get() = issues.any { it.severity == IssueSeverity.ERROR }
    val hasWarnings: Boolean get() = issues.any { it.severity == IssueSeverity.WARNING }
    val isValid: Boolean get() = !hasErrors
}

/**
 * Database version for version-aware validation
 */
data class DatabaseVersion(
    val major: Int,
    val minor: Int = 0,
    val patch: Int = 0,
) : Comparable<DatabaseVersion> {
    override fun compareTo(other: DatabaseVersion): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    companion object {
        fun parse(version: String): DatabaseVersion {
            val parts = version.split(".").map { it.toIntOrNull() ?: 0 }
            return DatabaseVersion(
                parts.getOrElse(0) { 0 },
                parts.getOrElse(1) { 0 },
                parts.getOrElse(2) { 0 },
            )
        }
    }
}

// ============================================================================
// SQL Validator
// ============================================================================

class SqlValidator(
    private val dialect: SqlDialect = SqlDialect.POSTGRESQL,
    private val version: DatabaseVersion? = null,
    private val availableTables: Set<String> = emptySet(),
) {
    // Features introduced in specific versions
    private val postgresFeatures =
        mapOf(
            "MERGE" to DatabaseVersion(15),
            "JSON_TABLE" to DatabaseVersion(17),
            "LATERAL" to DatabaseVersion(9, 3),
            "WITH RECURSIVE" to DatabaseVersion(8, 4),
            "WINDOW" to DatabaseVersion(8, 4),
            "RETURNING" to DatabaseVersion(8, 2),
        )

    private val mysqlFeatures =
        mapOf(
            "CTE" to DatabaseVersion(8, 0),
            "WINDOW" to DatabaseVersion(8, 0),
            "JSON_TABLE" to DatabaseVersion(8, 0),
            "LATERAL" to DatabaseVersion(8, 0, 14),
            "INTERSECT" to DatabaseVersion(8, 0),
            "EXCEPT" to DatabaseVersion(8, 0),
        )

    fun validate(sql: String): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        // Parse
        val parseResult = SqlParser.parse(sql, dialect)
        if (parseResult.isFailure) {
            val error = parseResult.exceptionOrNull()
            issues.add(
                ValidationIssue(
                    message = "Syntax error: ${error?.message ?: "Unknown error"}",
                    severity = IssueSeverity.ERROR,
                    position = SourcePosition(0, sql.length),
                    code = "SQL001",
                ),
            )
            return ValidationResult(issues, null)
        }

        val ast = parseResult.getOrNull() ?: return ValidationResult(issues, null)

        // Semantic validation
        validateStatement(ast, issues)

        return ValidationResult(issues, ast)
    }

    private fun validateStatement(
        stmt: SqlStatement,
        issues: MutableList<ValidationIssue>,
    ) {
        when (stmt) {
            is SelectStatement -> validateSelect(stmt, issues)
            is InsertStatement -> validateInsert(stmt, issues)
            is UpdateStatement -> validateUpdate(stmt, issues)
            is DeleteStatement -> validateDelete(stmt, issues)
        }
    }

    private fun validateSelect(
        select: SelectStatement,
        issues: MutableList<ValidationIssue>,
    ) {
        // Check for SELECT *
        if (select.columns.any { (it.expression as? Identifier)?.name == "*" }) {
            issues.add(
                ValidationIssue(
                    message = "Avoid SELECT * in production code",
                    severity = IssueSeverity.WARNING,
                    position = select.position,
                    code = "SQL101",
                    suggestion = "Explicitly list required columns",
                ),
            )
        }

        // Check for missing WHERE clause in subqueries
        if (select.where == null && select.from != null) {
            val tableName = (select.from?.source as? TableName)?.name?.fullName
            if (tableName != null && tableName.lowercase() !in setOf("dual", "generate_series")) {
                issues.add(
                    ValidationIssue(
                        message = "SELECT without WHERE clause may return large result set",
                        severity = IssueSeverity.INFO,
                        position = select.position,
                        code = "SQL102",
                    ),
                )
            }
        }

        // Validate table references
        select.from?.let { from ->
            validateTableReference(from.source, issues)
        }

        // Validate joins
        select.joins.forEach { join ->
            validateTableReference(join.table, issues)
            if (join.type != JoinType.CROSS && join.condition == null) {
                issues.add(
                    ValidationIssue(
                        message = "JOIN without ON clause",
                        severity = IssueSeverity.WARNING,
                        position = join.position,
                        code = "SQL103",
                        suggestion = "Add ON clause or use CROSS JOIN",
                    ),
                )
            }
        }

        // Validate expressions
        select.columns.forEach { col -> validateExpression(col.expression, issues) }
        select.where?.let { validateExpression(it, issues) }
        select.groupBy.forEach { validateExpression(it, issues) }
        select.having?.let { validateExpression(it, issues) }

        // Check for version-specific features
        validateVersionFeatures(select, issues)
    }

    private fun validateTableReference(
        ref: TableReference,
        issues: MutableList<ValidationIssue>,
    ) {
        when (ref) {
            is TableName -> {
                if (availableTables.isNotEmpty() && ref.name.fullName !in availableTables) {
                    issues.add(
                        ValidationIssue(
                            message = "Unknown table: ${ref.name.fullName}",
                            severity = IssueSeverity.WARNING,
                            position = ref.position,
                            code = "SQL201",
                        ),
                    )
                }
            }
            is SubqueryReference -> {
                validateSelect(ref.subquery, issues)
            }
        }
    }

    private fun validateExpression(
        expr: Expression,
        issues: MutableList<ValidationIssue>,
    ) {
        when (expr) {
            is BinaryExpression -> {
                validateExpression(expr.left, issues)
                validateExpression(expr.right, issues)

                // Check for NULL comparisons with =
                if (expr.operator == "=" && (expr.right is NullLiteral || expr.left is NullLiteral)) {
                    issues.add(
                        ValidationIssue(
                            message = "Use IS NULL instead of = NULL",
                            severity = IssueSeverity.WARNING,
                            position = expr.position,
                            code = "SQL301",
                            suggestion = "Replace '= NULL' with 'IS NULL'",
                        ),
                    )
                }

                // Check for implicit type coercion
                if (expr.operator in listOf("=", "<", ">", "<=", ">=")) {
                    checkTypeCoercion(expr.left, expr.right, issues)
                }
            }
            is UnaryExpression -> validateExpression(expr.operand, issues)
            is FunctionCall -> {
                expr.arguments.forEach { validateExpression(it, issues) }
                validateFunctionCall(expr, issues)
            }
            is ListExpression -> expr.elements.forEach { validateExpression(it, issues) }
            is CaseExpression -> {
                expr.operand?.let { validateExpression(it, issues) }
                expr.whenClauses.forEach {
                    validateExpression(it.condition, issues)
                    validateExpression(it.result, issues)
                }
                expr.elseResult?.let { validateExpression(it, issues) }
            }
            is SelectStatement -> validateSelect(expr, issues)
            else -> {}
        }
    }

    private fun validateFunctionCall(
        func: FunctionCall,
        issues: MutableList<ValidationIssue>,
    ) {
        val funcName = func.name.name.uppercase()

        // Check for common function mistakes
        when (funcName) {
            "COUNT" -> {
                if (func.arguments.isEmpty()) {
                    issues.add(
                        ValidationIssue(
                            message = "COUNT requires an argument",
                            severity = IssueSeverity.ERROR,
                            position = func.position,
                            code = "SQL401",
                        ),
                    )
                }
            }
            "COALESCE" -> {
                if (func.arguments.size < 2) {
                    issues.add(
                        ValidationIssue(
                            message = "COALESCE should have at least 2 arguments",
                            severity = IssueSeverity.WARNING,
                            position = func.position,
                            code = "SQL402",
                        ),
                    )
                }
            }
        }
    }

    private fun checkTypeCoercion(
        left: Expression,
        right: Expression,
        issues: MutableList<ValidationIssue>,
    ) {
        val leftType = inferType(left)
        val rightType = inferType(right)

        if (leftType != null && rightType != null && leftType != rightType) {
            if ((leftType == "string" && rightType == "number") ||
                (leftType == "number" && rightType == "string")
            ) {
                issues.add(
                    ValidationIssue(
                        message = "Implicit type coercion between $leftType and $rightType",
                        severity = IssueSeverity.INFO,
                        position = SourcePosition(left.position.start, right.position.end),
                        code = "SQL501",
                    ),
                )
            }
        }
    }

    private fun inferType(expr: Expression): String? =
        when (expr) {
            is StringLiteral -> "string"
            is NumberLiteral -> "number"
            is BooleanLiteral -> "boolean"
            is NullLiteral -> null
            else -> null
        }

    private fun validateVersionFeatures(
        select: SelectStatement,
        issues: MutableList<ValidationIssue>,
    ) {
        if (version == null) return

        val features =
            when (dialect) {
                SqlDialect.POSTGRESQL -> postgresFeatures
                SqlDialect.MYSQL -> mysqlFeatures
                else -> emptyMap()
            }

        // Check for window functions
        select.columns.forEach { col ->
            if (hasWindowFunction(col.expression)) {
                val requiredVersion = features["WINDOW"]
                if (requiredVersion != null && version < requiredVersion) {
                    issues.add(
                        ValidationIssue(
                            message = "Window functions require version $requiredVersion or higher",
                            severity = IssueSeverity.ERROR,
                            position = col.position,
                            code = "SQL601",
                        ),
                    )
                }
            }
        }
    }

    private fun hasWindowFunction(expr: Expression): Boolean =
        when (expr) {
            is FunctionCall -> {
                val windowFunctions =
                    setOf(
                        "ROW_NUMBER",
                        "RANK",
                        "DENSE_RANK",
                        "NTILE",
                        "LAG",
                        "LEAD",
                        "FIRST_VALUE",
                        "LAST_VALUE",
                        "NTH_VALUE",
                    )
                expr.name.name.uppercase() in windowFunctions ||
                    expr.arguments.any { hasWindowFunction(it) }
            }
            is BinaryExpression -> hasWindowFunction(expr.left) || hasWindowFunction(expr.right)
            is UnaryExpression -> hasWindowFunction(expr.operand)
            else -> false
        }

    private fun validateInsert(
        insert: InsertStatement,
        issues: MutableList<ValidationIssue>,
    ) {
        // Check column count matches values
        if (insert.columns != null && insert.values != null) {
            insert.values.forEach { row ->
                if (row.size != insert.columns.size) {
                    issues.add(
                        ValidationIssue(
                            message = "Column count (${insert.columns.size}) doesn't match value count (${row.size})",
                            severity = IssueSeverity.ERROR,
                            position = insert.position,
                            code = "SQL701",
                        ),
                    )
                }
            }
        }
    }

    private fun validateUpdate(
        update: UpdateStatement,
        issues: MutableList<ValidationIssue>,
    ) {
        // Check for UPDATE without WHERE
        if (update.where == null) {
            issues.add(
                ValidationIssue(
                    message = "UPDATE without WHERE clause will affect all rows",
                    severity = IssueSeverity.WARNING,
                    position = update.position,
                    code = "SQL801",
                ),
            )
        }
    }

    private fun validateDelete(
        delete: DeleteStatement,
        issues: MutableList<ValidationIssue>,
    ) {
        // Check for DELETE without WHERE
        if (delete.where == null) {
            issues.add(
                ValidationIssue(
                    message = "DELETE without WHERE clause will delete all rows",
                    severity = IssueSeverity.WARNING,
                    position = delete.position,
                    code = "SQL901",
                ),
            )
        }
    }
}

// ============================================================================
// MongoDB Validator
// ============================================================================

class MongoValidator(
    private val version: DatabaseVersion? = null,
    private val availableCollections: Set<String> = emptySet(),
) {
    private val deprecatedOperators =
        mapOf(
            "\$where" to "Use \$expr instead",
            "\$isolated" to "Removed in MongoDB 4.0",
        )

    private val versionFeatures =
        mapOf(
            "\$expr" to DatabaseVersion(3, 6),
            "\$merge" to DatabaseVersion(4, 2),
            "\$set" to DatabaseVersion(4, 2),
            "\$unset" to DatabaseVersion(4, 2),
            "\$replaceWith" to DatabaseVersion(4, 2),
            "\$unionWith" to DatabaseVersion(4, 4),
            "\$setWindowFields" to DatabaseVersion(5, 0),
        )

    fun validate(query: String): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        val parseResult = MongoParser.parse(query)
        if (parseResult.isFailure) {
            val error = parseResult.exceptionOrNull()
            issues.add(
                ValidationIssue(
                    message = "Parse error: ${error?.message ?: "Unknown error"}",
                    severity = IssueSeverity.ERROR,
                    position = SourcePosition(0, query.length),
                    code = "MONGO001",
                ),
            )
            return ValidationResult(issues, null)
        }

        val ast = parseResult.getOrNull() ?: return ValidationResult(issues, null)
        validateNode(ast, issues)

        return ValidationResult(issues, ast)
    }

    private fun validateNode(
        node: MongoNode,
        issues: MutableList<ValidationIssue>,
    ) {
        when (node) {
            is MongoQuery -> validateQuery(node, issues)
            is MongoAggregation -> validateAggregation(node, issues)
            is MongoFilter -> validateFilter(node, issues)
            else -> {}
        }
    }

    private fun validateQuery(
        query: MongoQuery,
        issues: MutableList<ValidationIssue>,
    ) {
        // Validate collection exists
        if (query.collection != null &&
            availableCollections.isNotEmpty() &&
            query.collection !in availableCollections
        ) {
            issues.add(
                ValidationIssue(
                    message = "Unknown collection: ${query.collection}",
                    severity = IssueSeverity.WARNING,
                    position = query.position,
                    code = "MONGO101",
                ),
            )
        }

        query.filter?.let { validateFilter(it, issues) }
    }

    private fun validateAggregation(
        agg: MongoAggregation,
        issues: MutableList<ValidationIssue>,
    ) {
        // Check for empty pipeline
        if (agg.pipeline.isEmpty()) {
            issues.add(
                ValidationIssue(
                    message = "Empty aggregation pipeline",
                    severity = IssueSeverity.WARNING,
                    position = agg.position,
                    code = "MONGO201",
                ),
            )
        }

        // Validate each stage
        agg.pipeline.forEach { stage ->
            validateStage(stage, issues)
        }

        // Check for $match at the beginning (performance)
        if (agg.pipeline.isNotEmpty() && agg.pipeline.first() !is MatchStage) {
            issues.add(
                ValidationIssue(
                    message = "Consider putting \$match at the beginning of the pipeline for better performance",
                    severity = IssueSeverity.HINT,
                    position = agg.position,
                    code = "MONGO202",
                ),
            )
        }
    }

    private fun validateStage(
        stage: MongoStage,
        issues: MutableList<ValidationIssue>,
    ) {
        when (stage) {
            is MatchStage -> validateFilter(stage.filter, issues)
            is LookupStage -> {
                if (stage.from.isEmpty()) {
                    issues.add(
                        ValidationIssue(
                            message = "\$lookup requires 'from' field",
                            severity = IssueSeverity.ERROR,
                            position = stage.position,
                            code = "MONGO301",
                        ),
                    )
                }
            }
            else -> {}
        }
    }

    private fun validateFilter(
        filter: MongoFilter,
        issues: MutableList<ValidationIssue>,
    ) {
        when (filter) {
            is MongoFieldFilter -> {
                val op = filter.operator.symbol

                // Check deprecated operators
                deprecatedOperators[op]?.let { suggestion ->
                    issues.add(
                        ValidationIssue(
                            message = "Operator $op is deprecated. $suggestion",
                            severity = IssueSeverity.WARNING,
                            position = filter.position,
                            code = "MONGO401",
                        ),
                    )
                }

                // Check version requirements
                if (version != null) {
                    versionFeatures[op]?.let { requiredVersion ->
                        if (version < requiredVersion) {
                            issues.add(
                                ValidationIssue(
                                    message = "Operator $op requires MongoDB $requiredVersion or higher",
                                    severity = IssueSeverity.ERROR,
                                    position = filter.position,
                                    code = "MONGO402",
                                ),
                            )
                        }
                    }
                }
            }
            is MongoLogicalFilter -> {
                filter.filters.forEach { validateFilter(it, issues) }
            }
        }
    }
}

// ============================================================================
// Elasticsearch Validator
// ============================================================================

class ElasticsearchValidator(
    private val version: DatabaseVersion? = null,
) {
    fun validate(query: String): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        val parseResult = ElasticsearchParser.parse(query)
        if (parseResult.isFailure) {
            val error = parseResult.exceptionOrNull()
            issues.add(
                ValidationIssue(
                    message = "Parse error: ${error?.message ?: "Unknown error"}",
                    severity = IssueSeverity.ERROR,
                    position = SourcePosition(0, query.length),
                    code = "ES001",
                ),
            )
            return ValidationResult(issues, null)
        }

        val ast = parseResult.getOrNull() ?: return ValidationResult(issues, null)
        validateQuery(ast, issues)

        return ValidationResult(issues, ast)
    }

    private fun validateQuery(
        query: EsQuery,
        issues: MutableList<ValidationIssue>,
    ) {
        query.query?.let { validateClause(it, issues) }
        query.aggregations?.values?.forEach { validateAggregation(it, issues) }

        // Check for very large size
        if (query.size != null && query.size > 10000) {
            issues.add(
                ValidationIssue(
                    message = "Size > 10000 may cause performance issues. Consider using scroll API",
                    severity = IssueSeverity.WARNING,
                    position = query.position,
                    code = "ES101",
                ),
            )
        }
    }

    private fun validateClause(
        clause: EsQueryClause,
        issues: MutableList<ValidationIssue>,
    ) {
        when (clause) {
            is EsBoolQuery -> {
                if (clause.must.isEmpty() &&
                    clause.should.isEmpty() &&
                    clause.filter.isEmpty() &&
                    clause.mustNot.isEmpty()
                ) {
                    issues.add(
                        ValidationIssue(
                            message = "Empty bool query",
                            severity = IssueSeverity.WARNING,
                            position = clause.position,
                            code = "ES201",
                        ),
                    )
                }

                clause.must.forEach { validateClause(it, issues) }
                clause.should.forEach { validateClause(it, issues) }
                clause.mustNot.forEach { validateClause(it, issues) }
                clause.filter.forEach { validateClause(it, issues) }
            }
            is EsMatchQuery -> {
                if (clause.query.isBlank()) {
                    issues.add(
                        ValidationIssue(
                            message = "Empty match query",
                            severity = IssueSeverity.WARNING,
                            position = clause.position,
                            code = "ES301",
                        ),
                    )
                }
            }
            is EsWildcardQuery -> {
                if (clause.value.startsWith("*")) {
                    issues.add(
                        ValidationIssue(
                            message = "Leading wildcard queries are slow",
                            severity = IssueSeverity.WARNING,
                            position = clause.position,
                            code = "ES302",
                        ),
                    )
                }
            }
            is EsRegexpQuery -> {
                issues.add(
                    ValidationIssue(
                        message = "Regexp queries can be slow. Consider alternatives",
                        severity = IssueSeverity.INFO,
                        position = clause.position,
                        code = "ES303",
                    ),
                )
            }
            is EsNestedQuery -> {
                validateClause(clause.query, issues)
            }
            else -> {}
        }
    }

    private fun validateAggregation(
        agg: EsAggregation,
        issues: MutableList<ValidationIssue>,
    ) {
        when (agg) {
            is EsTermsAgg -> {
                if (agg.size != null && agg.size > 10000) {
                    issues.add(
                        ValidationIssue(
                            message = "Large terms aggregation size may be inaccurate",
                            severity = IssueSeverity.WARNING,
                            position = agg.position,
                            code = "ES401",
                        ),
                    )
                }
                agg.subAggs?.values?.forEach { validateAggregation(it, issues) }
            }
            is EsDateHistogramAgg -> {
                agg.subAggs?.values?.forEach { validateAggregation(it, issues) }
            }
            else -> {}
        }
    }
}

// ============================================================================
// Unified Validator Factory
// ============================================================================

object ValidatorFactory {
    fun createSqlValidator(
        dialect: SqlDialect = SqlDialect.POSTGRESQL,
        version: DatabaseVersion? = null,
    ): SqlValidator = SqlValidator(dialect, version)

    fun createMongoValidator(version: DatabaseVersion? = null): MongoValidator = MongoValidator(version)

    fun createElasticsearchValidator(version: DatabaseVersion? = null): ElasticsearchValidator = ElasticsearchValidator(version)
}
