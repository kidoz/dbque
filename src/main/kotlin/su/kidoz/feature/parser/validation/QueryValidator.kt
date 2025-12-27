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
    // Deprecated operators with suggestions
    private val deprecatedOperators =
        mapOf(
            "\$where" to "Use \$expr instead for better security and performance",
            "\$isolated" to "Removed in MongoDB 4.0",
            "\$pushAll" to "Use \$push with \$each instead",
            "\$pullAll" to "Use \$pull with \$in instead",
        )

    // Version-specific features - when operators/stages were introduced
    private val versionFeatures =
        mapOf(
            // Query operators
            "\$expr" to DatabaseVersion(3, 6),
            "\$jsonSchema" to DatabaseVersion(3, 6),
            // Aggregation stages
            "\$merge" to DatabaseVersion(4, 2),
            "\$set" to DatabaseVersion(4, 2),
            "\$unset" to DatabaseVersion(4, 2),
            "\$replaceWith" to DatabaseVersion(4, 2),
            "\$unionWith" to DatabaseVersion(4, 4),
            "\$setWindowFields" to DatabaseVersion(5, 0),
            "\$densify" to DatabaseVersion(5, 1),
            "\$fill" to DatabaseVersion(5, 3),
            "\$documents" to DatabaseVersion(5, 1),
            // Accumulators
            "\$accumulator" to DatabaseVersion(4, 4),
            "\$function" to DatabaseVersion(4, 4),
            "\$bottom" to DatabaseVersion(5, 2),
            "\$bottomN" to DatabaseVersion(5, 2),
            "\$top" to DatabaseVersion(5, 2),
            "\$topN" to DatabaseVersion(5, 2),
            "\$firstN" to DatabaseVersion(5, 2),
            "\$lastN" to DatabaseVersion(5, 2),
            "\$maxN" to DatabaseVersion(5, 2),
            "\$minN" to DatabaseVersion(5, 2),
            "\$median" to DatabaseVersion(7, 0),
            "\$percentile" to DatabaseVersion(7, 0),
            // Expressions
            "\$getField" to DatabaseVersion(5, 0),
            "\$setField" to DatabaseVersion(5, 0),
            "\$unsetField" to DatabaseVersion(5, 0),
            "\$sortArray" to DatabaseVersion(5, 2),
        )

    // Performance anti-patterns
    private val performanceWarnings =
        mapOf(
            "\$where" to "JavaScript execution is slower than native operators",
            "\$regex" to "Consider text indexes for better full-text search performance",
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
            is MongoUpdate -> validateUpdate(node, issues)
            is MongoInsert -> validateInsert(node, issues)
            is MongoDelete -> validateDelete(node, issues)
            is MongoFindAndModify -> validateFindAndModify(node, issues)
            is MongoCount -> validateCount(node, issues)
            is MongoDistinct -> validateDistinct(node, issues)
            is MongoIndexOp -> validateIndexOp(node, issues)
            else -> {}
        }
    }

    private fun validateQuery(
        query: MongoQuery,
        issues: MutableList<ValidationIssue>,
    ) {
        // Validate collection exists
        validateCollection(query.collection, query.position, issues)

        // Validate filter
        query.filter?.let { validateFilter(it, issues) }

        // Check for explain mode
        if (query.explain) {
            issues.add(
                ValidationIssue(
                    message = "Query will return execution plan, not results",
                    severity = IssueSeverity.INFO,
                    position = query.position,
                    code = "MONGO102",
                ),
            )
        }

        // Warn about large skip values
        query.skip?.let { skip ->
            if (skip > 1000) {
                issues.add(
                    ValidationIssue(
                        message = "Large skip values (>1000) can be slow. Consider using range queries instead",
                        severity = IssueSeverity.WARNING,
                        position = query.position,
                        code = "MONGO103",
                        suggestion = "Use \$gt/\$lt on an indexed field for pagination",
                    ),
                )
            }
        }

        // Check for very large limit
        query.limit?.let { limit ->
            if (limit > 10000) {
                issues.add(
                    ValidationIssue(
                        message = "Returning more than 10000 documents may cause memory issues",
                        severity = IssueSeverity.WARNING,
                        position = query.position,
                        code = "MONGO104",
                    ),
                )
            }
        }
    }

    private fun validateAggregation(
        agg: MongoAggregation,
        issues: MutableList<ValidationIssue>,
    ) {
        validateCollection(agg.collection, agg.position, issues)

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
            return
        }

        // Validate each stage
        agg.pipeline.forEachIndexed { index, stage ->
            validateStage(stage, index, agg.pipeline.size, issues)
        }

        // Check for $match at the beginning (performance)
        if (agg.pipeline.first() !is MatchStage) {
            issues.add(
                ValidationIssue(
                    message = "Consider putting \$match at the beginning for better performance",
                    severity = IssueSeverity.HINT,
                    position = agg.position,
                    code = "MONGO202",
                    suggestion = "An early \$match can use indexes and reduce documents processed",
                ),
            )
        }

        // Check for $out or $merge not at the end
        agg.pipeline.forEachIndexed { index, stage ->
            if ((stage is OutStage || stage is MergeStage) && index != agg.pipeline.size - 1) {
                issues.add(
                    ValidationIssue(
                        message = "\$out and \$merge must be the last stage in the pipeline",
                        severity = IssueSeverity.ERROR,
                        position = stage.position,
                        code = "MONGO203",
                    ),
                )
            }
        }

        // Check for consecutive $sort and $limit (optimization opportunity)
        for (i in 0 until agg.pipeline.size - 1) {
            if (agg.pipeline[i] is SortStage && agg.pipeline[i + 1] is LimitStage) {
                issues.add(
                    ValidationIssue(
                        message = "Good: \$sort followed by \$limit allows MongoDB to optimize the sort",
                        severity = IssueSeverity.INFO,
                        position = agg.pipeline[i].position,
                        code = "MONGO204",
                    ),
                )
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun validateStage(
        stage: MongoStage,
        index: Int,
        totalStages: Int,
        issues: MutableList<ValidationIssue>,
    ) {
        // Check version requirements for stages
        val stageName = getStageOperator(stage)
        if (version != null && stageName != null) {
            versionFeatures[stageName]?.let { requiredVersion ->
                if (version < requiredVersion) {
                    issues.add(
                        ValidationIssue(
                            message = "$stageName requires MongoDB $requiredVersion or higher",
                            severity = IssueSeverity.ERROR,
                            position = stage.position,
                            code = "MONGO205",
                        ),
                    )
                }
            }
        }

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
                if (stage.alias.isEmpty()) {
                    issues.add(
                        ValidationIssue(
                            message = "\$lookup requires 'as' field",
                            severity = IssueSeverity.ERROR,
                            position = stage.position,
                            code = "MONGO302",
                        ),
                    )
                }
            }
            is GroupStage -> {
                // Validate _id field
                if (stage.id is MongoScalar && (stage.id as MongoScalar).value == null) {
                    issues.add(
                        ValidationIssue(
                            message = "\$group with _id: null groups all documents into one",
                            severity = IssueSeverity.INFO,
                            position = stage.position,
                            code = "MONGO303",
                        ),
                    )
                }
            }
            is UnwindStage -> {
                if (stage.path.isEmpty()) {
                    issues.add(
                        ValidationIssue(
                            message = "\$unwind requires a path field",
                            severity = IssueSeverity.ERROR,
                            position = stage.position,
                            code = "MONGO304",
                        ),
                    )
                }
                if (!stage.path.startsWith("\$") && stage.path.isNotEmpty()) {
                    issues.add(
                        ValidationIssue(
                            message = "\$unwind path should start with \$",
                            severity = IssueSeverity.WARNING,
                            position = stage.position,
                            code = "MONGO305",
                            suggestion = "Use '\$${stage.path}' instead of '${stage.path}'",
                        ),
                    )
                }
            }
            is BucketStage -> {
                if (stage.boundaries.elements.size < 2) {
                    issues.add(
                        ValidationIssue(
                            message = "\$bucket requires at least 2 boundaries",
                            severity = IssueSeverity.ERROR,
                            position = stage.position,
                            code = "MONGO306",
                        ),
                    )
                }
            }
            is FacetStage -> {
                // Check for nested $out or $merge in facets (not allowed)
                stage.facets.values.forEach { pipeline ->
                    pipeline.forEach { subStage ->
                        if (subStage is OutStage || subStage is MergeStage) {
                            issues.add(
                                ValidationIssue(
                                    message = "\$out and \$merge are not allowed inside \$facet",
                                    severity = IssueSeverity.ERROR,
                                    position = subStage.position,
                                    code = "MONGO307",
                                ),
                            )
                        }
                    }
                }
            }
            is SampleStage -> {
                if (stage.size <= 0) {
                    issues.add(
                        ValidationIssue(
                            message = "\$sample size must be positive",
                            severity = IssueSeverity.ERROR,
                            position = stage.position,
                            code = "MONGO308",
                        ),
                    )
                }
            }
            is GenericStage -> {
                // Unknown stage - might be a typo or unsupported
                issues.add(
                    ValidationIssue(
                        message = "Unknown or unsupported aggregation stage: ${stage.name}",
                        severity = IssueSeverity.WARNING,
                        position = stage.position,
                        code = "MONGO309",
                    ),
                )
            }
            else -> {}
        }
    }

    private fun getStageOperator(stage: MongoStage): String? =
        when (stage) {
            is MatchStage -> "\$match"
            is ProjectStage -> "\$project"
            is GroupStage -> "\$group"
            is SortStage -> "\$sort"
            is LimitStage -> "\$limit"
            is SkipStage -> "\$skip"
            is LookupStage -> "\$lookup"
            is UnwindStage -> "\$unwind"
            is AddFieldsStage -> "\$addFields"
            is ReplaceRootStage -> "\$replaceRoot"
            is FacetStage -> "\$facet"
            is BucketStage -> "\$bucket"
            is BucketAutoStage -> "\$bucketAuto"
            is CountStage -> "\$count"
            is OutStage -> "\$out"
            is MergeStage -> "\$merge"
            is SampleStage -> "\$sample"
            is RedactStage -> "\$redact"
            is GenericStage -> stage.name
            else -> null
        }

    private fun validateUpdate(
        update: MongoUpdate,
        issues: MutableList<ValidationIssue>,
    ) {
        validateCollection(update.collection, update.position, issues)
        validateFilter(update.filter, issues)

        // Check update operators in the update document
        if (update.update is MongoObject) {
            val updateObj = update.update as MongoObject
            val hasOperators = updateObj.fields.keys.any { it.startsWith("\$") }
            val hasFields = updateObj.fields.keys.any { !it.startsWith("\$") }

            if (hasOperators && hasFields) {
                issues.add(
                    ValidationIssue(
                        message = "Cannot mix update operators with field updates",
                        severity = IssueSeverity.ERROR,
                        position = update.position,
                        code = "MONGO501",
                    ),
                )
            }

            if (!hasOperators && update.operationType != MongoOperationType.REPLACE_ONE) {
                issues.add(
                    ValidationIssue(
                        message = "Update without operators will replace the entire document",
                        severity = IssueSeverity.WARNING,
                        position = update.position,
                        code = "MONGO502",
                        suggestion = "Use \$set to update specific fields",
                    ),
                )
            }
        }

        // Upsert warning
        if (update.upsert) {
            issues.add(
                ValidationIssue(
                    message = "Upsert enabled - will insert if no match found",
                    severity = IssueSeverity.INFO,
                    position = update.position,
                    code = "MONGO503",
                ),
            )
        }
    }

    private fun validateInsert(
        insert: MongoInsert,
        issues: MutableList<ValidationIssue>,
    ) {
        validateCollection(insert.collection, insert.position, issues)

        if (insert.documents.isEmpty()) {
            issues.add(
                ValidationIssue(
                    message = "No documents to insert",
                    severity = IssueSeverity.ERROR,
                    position = insert.position,
                    code = "MONGO601",
                ),
            )
        }

        // Check for _id in documents
        insert.documents.forEach { doc ->
            if ("_id" !in doc.fields) {
                issues.add(
                    ValidationIssue(
                        message = "Document without _id - MongoDB will auto-generate ObjectId",
                        severity = IssueSeverity.INFO,
                        position = doc.position,
                        code = "MONGO602",
                    ),
                )
            }
        }
    }

    private fun validateDelete(
        delete: MongoDelete,
        issues: MutableList<ValidationIssue>,
    ) {
        validateCollection(delete.collection, delete.position, issues)

        // Check for empty filter (will delete all documents)
        if (delete.filter is MongoFieldFilter) {
            val filter = delete.filter as MongoFieldFilter
            if (filter.field == "_id" && filter.operator == MongoOperator.EXISTS) {
                // This is the default "match all" filter
                if (delete.operationType == MongoOperationType.DELETE_MANY) {
                    issues.add(
                        ValidationIssue(
                            message = "deleteMany with empty filter will delete ALL documents",
                            severity = IssueSeverity.WARNING,
                            position = delete.position,
                            code = "MONGO701",
                        ),
                    )
                }
            }
        }

        validateFilter(delete.filter, issues)
    }

    private fun validateFindAndModify(
        findAndModify: MongoFindAndModify,
        issues: MutableList<ValidationIssue>,
    ) {
        validateCollection(findAndModify.collection, findAndModify.position, issues)
        validateFilter(findAndModify.filter, issues)

        // Info about return document option
        if (findAndModify.operationType != MongoOperationType.FIND_ONE_AND_DELETE) {
            issues.add(
                ValidationIssue(
                    message = "Will return ${findAndModify.returnDocument} document",
                    severity = IssueSeverity.INFO,
                    position = findAndModify.position,
                    code = "MONGO801",
                ),
            )
        }
    }

    private fun validateCount(
        count: MongoCount,
        issues: MutableList<ValidationIssue>,
    ) {
        validateCollection(count.collection, count.position, issues)
        count.filter?.let { validateFilter(it, issues) }
    }

    private fun validateDistinct(
        distinct: MongoDistinct,
        issues: MutableList<ValidationIssue>,
    ) {
        validateCollection(distinct.collection, distinct.position, issues)
        distinct.filter?.let { validateFilter(it, issues) }

        if (distinct.field.isEmpty()) {
            issues.add(
                ValidationIssue(
                    message = "distinct requires a field name",
                    severity = IssueSeverity.ERROR,
                    position = distinct.position,
                    code = "MONGO901",
                ),
            )
        }
    }

    private fun validateIndexOp(
        indexOp: MongoIndexOp,
        issues: MutableList<ValidationIssue>,
    ) {
        validateCollection(indexOp.collection, indexOp.position, issues)

        when (indexOp.operationType) {
            MongoOperationType.CREATE_INDEX -> {
                if (indexOp.keys == null) {
                    issues.add(
                        ValidationIssue(
                            message = "createIndex requires index keys specification",
                            severity = IssueSeverity.ERROR,
                            position = indexOp.position,
                            code = "MONGOA01",
                        ),
                    )
                }
            }
            MongoOperationType.DROP_INDEX -> {
                if (indexOp.keys == null && indexOp.indexName == null) {
                    issues.add(
                        ValidationIssue(
                            message = "dropIndex requires index name or keys",
                            severity = IssueSeverity.ERROR,
                            position = indexOp.position,
                            code = "MONGOA02",
                        ),
                    )
                }
            }
            else -> {}
        }
    }

    private fun validateCollection(
        collection: String?,
        position: SourcePosition,
        issues: MutableList<ValidationIssue>,
    ) {
        if (collection != null &&
            availableCollections.isNotEmpty() &&
            collection !in availableCollections
        ) {
            issues.add(
                ValidationIssue(
                    message = "Unknown collection: $collection",
                    severity = IssueSeverity.WARNING,
                    position = position,
                    code = "MONGO101",
                ),
            )
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
                            suggestion = suggestion,
                        ),
                    )
                }

                // Check performance warnings
                performanceWarnings[op]?.let { warning ->
                    issues.add(
                        ValidationIssue(
                            message = warning,
                            severity = IssueSeverity.HINT,
                            position = filter.position,
                            code = "MONGO403",
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

                // Validate operator-specific rules
                validateOperatorValue(filter, issues)
            }
            is MongoLogicalFilter -> {
                filter.filters.forEach { validateFilter(it, issues) }

                // Check for empty logical operators
                if (filter.filters.isEmpty()) {
                    issues.add(
                        ValidationIssue(
                            message = "${filter.operator.symbol} has no conditions",
                            severity = IssueSeverity.WARNING,
                            position = filter.position,
                            code = "MONGO404",
                        ),
                    )
                }

                // $not requires exactly one condition
                if (filter.operator == MongoLogicalOperator.NOT && filter.filters.size != 1) {
                    issues.add(
                        ValidationIssue(
                            message = "\$not requires exactly one condition",
                            severity = IssueSeverity.ERROR,
                            position = filter.position,
                            code = "MONGO405",
                        ),
                    )
                }
            }
        }
    }

    private fun validateOperatorValue(
        filter: MongoFieldFilter,
        issues: MutableList<ValidationIssue>,
    ) {
        when (filter.operator) {
            MongoOperator.IN, MongoOperator.NIN, MongoOperator.ALL -> {
                if (filter.value !is MongoArray) {
                    issues.add(
                        ValidationIssue(
                            message = "${filter.operator.symbol} requires an array value",
                            severity = IssueSeverity.ERROR,
                            position = filter.position,
                            code = "MONGO406",
                        ),
                    )
                }
            }
            MongoOperator.EXISTS -> {
                val value = (filter.value as? MongoScalar)?.value
                if (value !is Boolean) {
                    issues.add(
                        ValidationIssue(
                            message = "\$exists requires a boolean value",
                            severity = IssueSeverity.ERROR,
                            position = filter.position,
                            code = "MONGO407",
                        ),
                    )
                }
            }
            MongoOperator.SIZE -> {
                val value = (filter.value as? MongoScalar)?.value
                if (value !is Number || value.toInt() < 0) {
                    issues.add(
                        ValidationIssue(
                            message = "\$size requires a non-negative integer",
                            severity = IssueSeverity.ERROR,
                            position = filter.position,
                            code = "MONGO408",
                        ),
                    )
                }
            }
            MongoOperator.ELEM_MATCH -> {
                if (filter.value !is MongoObject) {
                    issues.add(
                        ValidationIssue(
                            message = "\$elemMatch requires an object with query conditions",
                            severity = IssueSeverity.ERROR,
                            position = filter.position,
                            code = "MONGO409",
                        ),
                    )
                }
            }
            else -> {}
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
