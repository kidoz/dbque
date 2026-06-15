package su.kidoz.feature.editor.format

import com.github.h0tk3y.betterParse.grammar.parseToEnd
import su.kidoz.feature.parser.ast.*
import su.kidoz.feature.parser.sql.SqlGrammar

class SqlFormatter(
    private val preset: SqlFormatPreset = SqlFormatPreset(),
) {
    private var indentLevel = 0
    private val sb = java.lang.StringBuilder()

    fun format(sql: String): String =
        try {
            val parser = SqlGrammar()
            val ast = parser.parseToEnd(sql)
            formatNode(ast)
        } catch (e: Exception) {
            // Fallback if parsing fails (return original or apply basic regex formatting)
            sql
        }

    private fun appendIndent() {
        sb.append(preset.indentString.repeat(indentLevel))
    }

    private fun appendNewline() {
        sb.append("\n")
    }

    private fun withIndent(block: () -> Unit) {
        indentLevel++
        block()
        indentLevel--
    }

    private fun formatNode(node: QueryNode): String {
        sb.clear()
        indentLevel = 0
        visitNode(node)
        return sb.toString().trim()
    }

    private fun visitNode(node: QueryNode) {
        when (node) {
            is SelectStatement -> visitSelectStatement(node)

            is InsertStatement -> visitInsertStatement(node)

            is UpdateStatement -> visitUpdateStatement(node)

            is DeleteStatement -> visitDeleteStatement(node)

            // Default fallback for unhandled nodes: try to extract text or just stringify
            else -> sb.append(node.toString())
        }
    }

    private fun visitSelectStatement(node: SelectStatement) {
        sb.append(preset.formatKeyword("SELECT"))
        if (preset.expandCommaLists && node.columns.size > 1) {
            appendNewline()
            withIndent {
                node.columns.forEachIndexed { index, column ->
                    appendIndent()
                    visitSelectColumn(column)
                    if (index < node.columns.size - 1) {
                        sb.append(",")
                        appendNewline()
                    }
                }
            }
            appendNewline()
        } else {
            sb.append(" ")
            node.columns.forEachIndexed { index, column ->
                visitSelectColumn(column)
                if (index < node.columns.size - 1) {
                    sb.append(", ")
                }
            }
        }

        node.from?.let {
            if (!preset.expandCommaLists || node.columns.size <= 1) appendNewline()
            visitFromClause(it)
        }

        if (node.joins.isNotEmpty()) {
            appendNewline()
            node.joins.forEachIndexed { index, join ->
                visitJoinClause(join)
                if (index < node.joins.size - 1) appendNewline()
            }
        }

        node.where?.let {
            appendNewline()
            sb.append(preset.formatKeyword("WHERE"))
            sb.append(" ")
            visitExpression(it)
        }

        if (node.groupBy.isNotEmpty()) {
            appendNewline()
            sb.append(preset.formatKeyword("GROUP BY"))
            sb.append(" ")
            node.groupBy.forEachIndexed { index, expr ->
                visitExpression(expr)
                if (index < node.groupBy.size - 1) sb.append(", ")
            }
        }

        node.having?.let {
            appendNewline()
            sb.append(preset.formatKeyword("HAVING"))
            sb.append(" ")
            visitExpression(it)
        }

        if (node.orderBy.isNotEmpty()) {
            appendNewline()
            sb.append(preset.formatKeyword("ORDER BY"))
            sb.append(" ")
            node.orderBy.forEachIndexed { index, expr ->
                visitOrderByClause(expr)
                if (index < node.orderBy.size - 1) sb.append(", ")
            }
        }

        node.limit?.let {
            appendNewline()
            sb.append(preset.formatKeyword("LIMIT"))
            sb.append(" ")
            visitExpression(it)
        }

        node.offset?.let {
            appendNewline()
            sb.append(preset.formatKeyword("OFFSET"))
            sb.append(" ")
            visitExpression(it)
        }
    }

    private fun visitSelectColumn(node: SelectColumn) {
        visitExpression(node.expression)
        node.alias?.let {
            sb.append(" ")
            sb.append(preset.formatKeyword("AS"))
            sb.append(" ")
            visitIdentifier(it)
        }
    }

    private fun visitFromClause(node: FromClause) {
        sb.append(preset.formatKeyword("FROM"))
        sb.append(" ")
        visitTableReference(node.source)
    }

    private fun visitTableReference(node: TableReference) {
        when (node) {
            is TableName -> {
                visitQualifiedIdentifier(node.name)
                node.alias?.let {
                    sb.append(" ")
                    visitIdentifier(it)
                }
            }

            is SubqueryReference -> {
                sb.append("(")
                appendNewline()
                withIndent {
                    appendIndent()
                    visitSelectStatement(node.subquery)
                }
                appendNewline()
                appendIndent()
                sb.append(") ")
                sb.append(preset.formatKeyword("AS"))
                sb.append(" ")
                visitIdentifier(node.alias)
            }
        }
    }

    private fun visitJoinClause(node: JoinClause) {
        val joinKeyword =
            when (node.type) {
                JoinType.INNER -> "JOIN"
                JoinType.LEFT -> "LEFT JOIN"
                JoinType.RIGHT -> "RIGHT JOIN"
                JoinType.FULL -> "FULL JOIN"
                JoinType.CROSS -> "CROSS JOIN"
            }
        sb.append(preset.formatKeyword(joinKeyword))
        sb.append(" ")
        visitTableReference(node.table)
        node.condition?.let {
            sb.append(" ")
            sb.append(preset.formatKeyword("ON"))
            sb.append(" ")
            visitExpression(it)
        }
    }

    private fun visitOrderByClause(node: OrderByClause) {
        visitExpression(node.expression)
        if (!node.ascending) {
            sb.append(" ")
            sb.append(preset.formatKeyword("DESC"))
        }
        node.nullsFirst?.let { first ->
            sb.append(" ")
            sb.append(preset.formatKeyword(if (first) "NULLS FIRST" else "NULLS LAST"))
        }
    }

    private fun visitInsertStatement(node: InsertStatement) {
        sb.append(preset.formatKeyword("INSERT INTO"))
        sb.append(" ")
        visitQualifiedIdentifier(node.table)

        node.columns?.let { cols ->
            sb.append(" (")
            cols.forEachIndexed { index, col ->
                visitIdentifier(col)
                if (index < cols.size - 1) sb.append(", ")
            }
            sb.append(")")
        }

        appendNewline()

        node.values?.let { valsList ->
            sb.append(preset.formatKeyword("VALUES"))
            appendNewline()
            withIndent {
                valsList.forEachIndexed { listIndex, vals ->
                    appendIndent()
                    sb.append("(")
                    vals.forEachIndexed { index, expr ->
                        visitExpression(expr)
                        if (index < vals.size - 1) sb.append(", ")
                    }
                    sb.append(")")
                    if (listIndex < valsList.size - 1) {
                        sb.append(",")
                        appendNewline()
                    }
                }
            }
        } ?: node.select?.let {
            visitSelectStatement(it)
        }
    }

    private fun visitUpdateStatement(node: UpdateStatement) {
        sb.append(preset.formatKeyword("UPDATE"))
        sb.append(" ")
        visitQualifiedIdentifier(node.table)
        appendNewline()
        sb.append(preset.formatKeyword("SET"))

        if (preset.expandCommaLists && node.assignments.size > 1) {
            appendNewline()
            withIndent {
                node.assignments.forEachIndexed { index, assignment ->
                    appendIndent()
                    visitIdentifier(assignment.column)
                    sb.append(if (preset.spaceAroundOperators) " = " else "=")
                    visitExpression(assignment.value)
                    if (index < node.assignments.size - 1) {
                        sb.append(",")
                        appendNewline()
                    }
                }
            }
            appendNewline()
        } else {
            sb.append(" ")
            node.assignments.forEachIndexed { index, assignment ->
                visitIdentifier(assignment.column)
                sb.append(if (preset.spaceAroundOperators) " = " else "=")
                visitExpression(assignment.value)
                if (index < node.assignments.size - 1) sb.append(", ")
            }
        }

        node.where?.let {
            if (!preset.expandCommaLists || node.assignments.size <= 1) appendNewline()
            sb.append(preset.formatKeyword("WHERE"))
            sb.append(" ")
            visitExpression(it)
        }
    }

    private fun visitDeleteStatement(node: DeleteStatement) {
        sb.append(preset.formatKeyword("DELETE FROM"))
        sb.append(" ")
        visitQualifiedIdentifier(node.table)

        node.where?.let {
            appendNewline()
            sb.append(preset.formatKeyword("WHERE"))
            sb.append(" ")
            visitExpression(it)
        }
    }

    private fun visitExpression(node: Expression) {
        when (node) {
            is Identifier -> {
                visitIdentifier(node)
            }

            is QualifiedIdentifier -> {
                visitQualifiedIdentifier(node)
            }

            is StringLiteral -> {
                sb.append("'")
                sb.append(node.value.replace("'", "''"))
                sb.append("'")
            }

            is NumberLiteral -> {
                sb.append(node.value.toString())
            }

            is BooleanLiteral -> {
                sb.append(preset.formatKeyword(node.value.toString().uppercase()))
            }

            is NullLiteral -> {
                sb.append(preset.formatKeyword("NULL"))
            }

            is BinaryExpression -> {
                visitExpression(node.left)
                val space = if (preset.spaceAroundOperators) " " else ""
                sb.append(space)
                sb.append(preset.formatKeyword(node.operator))
                sb.append(space)
                visitExpression(node.right)
            }

            is FunctionCall -> {
                sb.append(preset.formatFunction(node.name.name))
                sb.append("(")
                if (node.distinct) sb.append(preset.formatKeyword("DISTINCT "))
                node.arguments.forEachIndexed { index, arg ->
                    visitExpression(arg)
                    if (index < node.arguments.size - 1) sb.append(", ")
                }
                sb.append(")")
            }

            is ListExpression -> {
                sb.append("(")
                node.elements.forEachIndexed { index, expr ->
                    visitExpression(expr)
                    if (index < node.elements.size - 1) sb.append(", ")
                }
                sb.append(")")
            }

            is SelectStatement -> {
                sb.append("(")
                appendNewline()
                withIndent {
                    appendIndent()
                    visitSelectStatement(node)
                }
                appendNewline()
                appendIndent()
                sb.append(")")
            }

            is UnaryExpression -> {
                sb.append(preset.formatKeyword(node.operator))
                if (node.operator.matches(Regex("[a-zA-Z]+"))) sb.append(" ")
                visitExpression(node.operand)
            }

            // Fallback for missing expression types (CaseExpression, etc.)
            else -> {
                sb.append("/* unsupported expr */")
            }
        }
    }

    private fun visitIdentifier(node: Identifier) {
        val name = preset.formatIdentifier(node.name, node.quoted)
        if (node.quoted) {
            sb.append("\"").append(name.replace("\"", "\"\"")).append("\"")
        } else {
            sb.append(name)
        }
    }

    private fun visitQualifiedIdentifier(node: QualifiedIdentifier) {
        node.parts.forEachIndexed { index, part ->
            visitIdentifier(part)
            if (index < node.parts.size - 1) sb.append(".")
        }
    }
}
