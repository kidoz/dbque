package su.kidoz.feature.parser.sql

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.lexer.TokenMatch
import com.github.h0tk3y.betterParse.parser.Parser
import su.kidoz.feature.parser.ast.*

/**
 * SQL Parser using better-parse combinators
 * Supports PostgreSQL, MySQL, SQLite dialects
 */
@Suppress("unused")
class SqlGrammar(
    private val dialect: SqlDialect = SqlDialect.POSTGRESQL,
) : Grammar<SqlStatement>() {
    // Import tokens
    private val t = SqlTokens
    override val tokens = t.allTokens

    // Helper to create position
    private fun TokenMatch.pos() = SourcePosition(offset, offset + text.length)

    private fun pos(
        start: TokenMatch,
        end: TokenMatch,
    ) = SourcePosition(start.offset, end.offset + end.text.length)

    // ========================================================================
    // Literals
    // ========================================================================

    private val stringLiteral: Parser<StringLiteral> =
        t.STRING map {
            StringLiteral(it.text.removeSurrounding("'").replace("''", "'"), it.pos())
        }

    private val numberLiteral: Parser<NumberLiteral> =
        t.NUMBER map {
            val value =
                if (it.text.contains('.') || it.text.contains('e', ignoreCase = true)) {
                    it.text.toDouble()
                } else {
                    it.text.toLong()
                }
            NumberLiteral(value, it.pos())
        }

    private val booleanLiteral: Parser<BooleanLiteral> =
        (t.TRUE or t.FALSE) map {
            BooleanLiteral(it.text.equals("true", ignoreCase = true), it.pos())
        }

    private val nullLiteral: Parser<NullLiteral> = t.NULL map { NullLiteral(it.pos()) }

    private val literal: Parser<Literal> = stringLiteral or numberLiteral or booleanLiteral or nullLiteral

    // ========================================================================
    // Identifiers
    // ========================================================================

    private val identifier: Parser<Identifier> =
        (t.ID or t.QUOTED_ID or t.BACKTICK_ID) map { match ->
            val name =
                when {
                    match.text.startsWith("\"") -> match.text.removeSurrounding("\"").replace("\"\"", "\"")
                    match.text.startsWith("`") -> match.text.removeSurrounding("`")
                    else -> match.text
                }
            Identifier(name, match.text.startsWith("\"") || match.text.startsWith("`"), match.pos())
        }

    private val qualifiedIdentifier: Parser<QualifiedIdentifier> =
        separatedTerms(identifier, t.DOT, acceptZero = false) map { ids ->
            QualifiedIdentifier(ids, SourcePosition(ids.first().position.start, ids.last().position.end))
        }

    private val parameter: Parser<Expression> =
        t.PARAM map { match ->
            Identifier(match.text, false, match.pos())
        }

    // ========================================================================
    // Expressions (with precedence)
    // ========================================================================

    private val atom: Parser<Expression> by lazy {
        literal or
            parameter or
            (t.LPAREN and parser { expression } and t.RPAREN map { result -> result.t2 }) or
            parser { functionCall } or
            parser { caseExpression } or
            parser { existsExpression } or
            parser { castExpression } or
            (t.STAR map { Identifier("*", false, it.pos()) }) or
            qualifiedIdentifier
    }

    private val functionCall: Parser<FunctionCall> by lazy {
        (
            identifier and
                t.LPAREN and
                optional(t.DISTINCT) and
                optional(separatedTerms(parser { expression }, t.COMMA)) and
                t.RPAREN
        ) map { result ->
            val name = result.t1
            val lp = result.t2
            val distinct = result.t3
            val args = result.t4
            val rp = result.t5
            FunctionCall(name, args ?: emptyList(), distinct != null, pos(lp, rp))
        }
    }

    private val caseExpression: Parser<CaseExpression> by lazy {
        (
            t.CASE and
                optional(parser { expression }) and
                oneOrMore(
                    t.WHEN and parser { expression } and t.THEN and parser { expression } map { result ->
                        val cond = result.t2
                        val resultExpr = result.t4
                        WhenClause(cond, resultExpr, cond.position)
                    },
                ) and
                optional(t.ELSE and parser { expression } map { it.t2 }) and
                t.END
        ) map { result ->
            val c = result.t1
            val operand = result.t2
            val whens = result.t3
            val elseExpr = result.t4
            val e = result.t5
            CaseExpression(operand, whens, elseExpr, pos(c, e))
        }
    }

    private val existsExpression: Parser<Expression> by lazy {
        (
            t.EXISTS and t.LPAREN and parser { selectStatement } and t.RPAREN
        ) map { result ->
            val ex = result.t1
            val subquery = result.t3
            val rp = result.t4
            FunctionCall(Identifier("EXISTS", false, ex.pos()), listOf(subquery), false, pos(ex, rp))
        }
    }

    private val castExpression: Parser<Expression> by lazy {
        (
            t.CAST and t.LPAREN and parser { expression } and t.AS and identifier and t.RPAREN
        ) map { result ->
            val c = result.t1
            val expr = result.t3
            val type = result.t5
            val rp = result.t6
            FunctionCall(
                Identifier("CAST", false, c.pos()),
                listOf(expr, type),
                false,
                pos(c, rp),
            )
        }
    }

    // Unary operators
    private val unaryExpr: Parser<Expression> by lazy {
        val unaryOp =
            (t.NOT or t.MINUS or t.PLUS) and atom map { (op, operand) ->
                UnaryExpression(op.text.uppercase(), operand, SourcePosition(op.offset, operand.position.end))
            }
        unaryOp or atom
    }

    // Multiplicative: *, /, %
    private val multiplicativeExpr: Parser<Expression> by lazy {
        leftAssociative(unaryExpr, t.STAR or t.SLASH or t.PERCENT) { l, op, r ->
            BinaryExpression(l, op.text, r, SourcePosition(l.position.start, r.position.end))
        }
    }

    // Additive: +, -, ||
    private val additiveExpr: Parser<Expression> by lazy {
        leftAssociative(multiplicativeExpr, t.PLUS or t.MINUS or t.CONCAT) { l, op, r ->
            BinaryExpression(l, op.text, r, SourcePosition(l.position.start, r.position.end))
        }
    }

    // Comparison: =, !=, <, <=, >, >=, LIKE, ILIKE, IN, BETWEEN, IS
    private val comparisonOp =
        t.EQ or t.NEQ or t.LT or t.LTE or t.GT or t.GTE or
            t.LIKE or t.ILIKE or t.REGEX_MATCH or t.REGEX_NOT_MATCH

    private val inExpression: Parser<(Expression) -> Expression> by lazy {
        (
            optional(t.NOT) and t.IN and t.LPAREN and
                (parser { selectStatement } map { listOf(it as Expression) } or separatedTerms(parser { expression }, t.COMMA)) and
                t.RPAREN
        ) map { result ->
            val not = result.t1
            val values = result.t4
            val rp = result.t5
            { left: Expression ->
                val op = if (not != null) "NOT IN" else "IN"
                BinaryExpression(
                    left,
                    op,
                    ListExpression(values, values.first().position),
                    SourcePosition(left.position.start, rp.offset + 1),
                )
            }
        }
    }

    private val betweenExpression: Parser<(Expression) -> Expression> by lazy {
        (
            optional(t.NOT) and t.BETWEEN and additiveExpr and t.AND and additiveExpr
        ) map { result ->
            val not = result.t1
            val low = result.t3
            val high = result.t5
            { left: Expression ->
                val op = if (not != null) "NOT BETWEEN" else "BETWEEN"
                BinaryExpression(
                    left,
                    op,
                    ListExpression(listOf(low, high), SourcePosition(low.position.start, high.position.end)),
                    SourcePosition(left.position.start, high.position.end),
                )
            }
        }
    }

    private val isNullExpression: Parser<(Expression) -> Expression> =
        (t.IS and optional(t.NOT) and t.NULL) map { result ->
            val not = result.t2
            val n = result.t3
            { left: Expression ->
                val op = if (not != null) "IS NOT NULL" else "IS NULL"
                UnaryExpression(op, left, SourcePosition(left.position.start, n.offset + n.text.length))
            }
        }

    private val comparisonExpr: Parser<Expression> by lazy {
        (
            additiveExpr and
                optional(
                    (
                        comparisonOp and additiveExpr map { (op, r) ->
                            { l: Expression ->
                                BinaryExpression(l, op.text, r, SourcePosition(l.position.start, r.position.end))
                            }
                        }
                    ) or
                        inExpression or
                        betweenExpression or
                        isNullExpression,
                )
        ) map { (left, transform) ->
            transform?.invoke(left) ?: left
        }
    }

    // Logical AND
    private val andExpr: Parser<Expression> by lazy {
        leftAssociative(comparisonExpr, t.AND) { l, _, r ->
            BinaryExpression(l, "AND", r, SourcePosition(l.position.start, r.position.end))
        }
    }

    // Logical OR
    private val orExpr: Parser<Expression> by lazy {
        leftAssociative(andExpr, t.OR) { l, _, r ->
            BinaryExpression(l, "OR", r, SourcePosition(l.position.start, r.position.end))
        }
    }

    private val expression: Parser<Expression> by lazy { orExpr }

    // ========================================================================
    // SELECT Statement
    // ========================================================================

    private val selectColumn: Parser<SelectColumn> by lazy {
        (
            expression and
                optional(optional(t.AS) and identifier map { it.t2 })
        ) map { (expr, alias) ->
            SelectColumn(expr, alias, expr.position)
        }
    }

    private val selectColumns: Parser<List<SelectColumn>> by lazy {
        separatedTerms(selectColumn, t.COMMA)
    }

    private val tableName: Parser<TableName> by lazy {
        (
            qualifiedIdentifier and optional(optional(t.AS) and identifier map { it.t2 })
        ) map { (name, alias) ->
            TableName(name, alias, name.position)
        }
    }

    private val subqueryRef: Parser<SubqueryReference> by lazy {
        (
            t.LPAREN and parser { selectStatement } and t.RPAREN and
                optional(t.AS) and identifier
        ) map { result ->
            val lp = result.t1
            val subq = result.t2
            val alias = result.t5
            SubqueryReference(subq, alias, SourcePosition(lp.offset, alias.position.end))
        }
    }

    private val tableRef: Parser<TableReference> by lazy { subqueryRef or tableName }

    private val fromClause: Parser<FromClause> by lazy {
        (t.FROM and tableRef) map { (_, ref) ->
            FromClause(ref, ref.position)
        }
    }

    private val joinType: Parser<JoinType> =
        (
            optional(t.LEFT or t.RIGHT or t.FULL or t.CROSS or t.INNER) and
                optional(t.OUTER) and
                t.JOIN
        ) map { result ->
            val type = result.t1
            when (type?.text?.uppercase()) {
                "LEFT" -> JoinType.LEFT
                "RIGHT" -> JoinType.RIGHT
                "FULL" -> JoinType.FULL
                "CROSS" -> JoinType.CROSS
                else -> JoinType.INNER
            }
        }

    private val joinClause: Parser<JoinClause> by lazy {
        (
            joinType and tableRef and optional(t.ON and expression map { it.t2 })
        ) map { result ->
            val type = result.t1
            val table = result.t2
            val cond = result.t3
            JoinClause(type, table, cond, table.position)
        }
    }

    private val whereClause: Parser<Expression> by lazy {
        (t.WHERE and expression) map { (_, e) -> e }
    }

    private val groupByClause: Parser<List<Expression>> by lazy {
        (t.GROUP and t.BY and separatedTerms(expression, t.COMMA)) map { result -> result.t3 }
    }

    private val havingClause: Parser<Expression> by lazy {
        (t.HAVING and expression) map { (_, e) -> e }
    }

    private val orderDirection: Parser<Boolean> = optional(t.ASC or t.DESC) map { it?.text?.uppercase() != "DESC" }

    private val nullsOrder: Parser<Boolean?> =
        optional(
            t.NULLS and (t.FIRST or t.LAST) map { result -> result.t2.text.uppercase() == "FIRST" },
        )

    private val orderByItem: Parser<OrderByClause> by lazy {
        (expression and orderDirection and nullsOrder) map { result ->
            val expr = result.t1
            val asc = result.t2
            val nullsFirst = result.t3
            OrderByClause(expr, asc, nullsFirst, expr.position)
        }
    }

    private val orderByClause: Parser<List<OrderByClause>> by lazy {
        (t.ORDER and t.BY and separatedTerms(orderByItem, t.COMMA)) map { result -> result.t3 }
    }

    private val limitClause: Parser<Expression> by lazy { (t.LIMIT and expression) map { (_, e) -> e } }
    private val offsetClause: Parser<Expression> by lazy { (t.OFFSET and expression) map { (_, e) -> e } }

    private val selectStatement: Parser<SelectStatement> by lazy {
        (
            t.SELECT and
                optional(t.DISTINCT or t.ALL) and
                selectColumns and
                optional(fromClause) and
                zeroOrMore(joinClause) and
                optional(whereClause) and
                optional(groupByClause) and
                optional(havingClause) and
                optional(orderByClause) and
                optional(limitClause) and
                optional(offsetClause)
        ) map { result ->
            val sel = result.t1
            val cols = result.t3
            val from = result.t4
            val joins = result.t5
            val where = result.t6
            val groupBy = result.t7
            val having = result.t8
            val orderBy = result.t9
            val limit = result.t10
            val offset = result.t11
            SelectStatement(
                columns = cols,
                from = from,
                joins = joins,
                where = where,
                groupBy = groupBy ?: emptyList(),
                having = having,
                orderBy = orderBy ?: emptyList(),
                limit = limit,
                offset = offset,
                position = sel.pos(),
            )
        }
    }

    // ========================================================================
    // INSERT Statement
    // ========================================================================

    private val columnList: Parser<List<Identifier>> by lazy {
        (t.LPAREN and separatedTerms(identifier, t.COMMA) and t.RPAREN) map { result -> result.t2 }
    }

    private val valuesList: Parser<List<Expression>> by lazy {
        (t.LPAREN and separatedTerms(expression, t.COMMA) and t.RPAREN) map { result -> result.t2 }
    }

    private val insertStatement: Parser<InsertStatement> by lazy {
        (
            t.INSERT and t.INTO and qualifiedIdentifier and
                optional(columnList) and
                (
                    (t.VALUES and separatedTerms(valuesList, t.COMMA) map { it.t2 to null }) or
                        (selectStatement map { null to it })
                )
        ) map { result ->
            val ins = result.t1
            val table = result.t3
            val cols = result.t4
            val valuesOrSelect = result.t5
            val (values, select) = valuesOrSelect
            InsertStatement(
                table = table,
                columns = cols,
                values = values,
                select = select,
                position = ins.pos(),
            )
        }
    }

    // ========================================================================
    // UPDATE Statement
    // ========================================================================

    private val assignment: Parser<Assignment> by lazy {
        (identifier and t.EQ and expression) map { result ->
            val col = result.t1
            val value = result.t3
            Assignment(col, value, col.position)
        }
    }

    private val updateStatement: Parser<UpdateStatement> by lazy {
        (
            t.UPDATE and qualifiedIdentifier and
                t.SET and separatedTerms(assignment, t.COMMA) and
                optional(fromClause) and
                optional(whereClause)
        ) map { result ->
            val upd = result.t1
            val table = result.t2
            val assignments = result.t4
            val from = result.t5
            val where = result.t6
            UpdateStatement(table, assignments, from, where, upd.pos())
        }
    }

    // ========================================================================
    // DELETE Statement
    // ========================================================================

    private val deleteStatement: Parser<DeleteStatement> by lazy {
        (
            t.DELETE and t.FROM and qualifiedIdentifier and optional(whereClause)
        ) map { result ->
            val del = result.t1
            val table = result.t3
            val where = result.t4
            DeleteStatement(table, where, del.pos())
        }
    }

    // ========================================================================
    // Root Parser
    // ========================================================================

    override val rootParser: Parser<SqlStatement> by lazy {
        (
            selectStatement or insertStatement or updateStatement or deleteStatement
        ) and optional(t.SEMICOLON) map { (stmt, _) -> stmt }
    }
}

/**
 * SQL Parser facade
 */
object SqlParser {
    fun parse(
        sql: String,
        dialect: SqlDialect = SqlDialect.POSTGRESQL,
    ): Result<SqlStatement> =
        try {
            val grammar = SqlGrammar(dialect)
            Result.success(grammar.parseToEnd(sql))
        } catch (e: Exception) {
            Result.failure(e)
        }
}
