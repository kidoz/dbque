package su.kidoz.feature.parser.sql

import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.lexer.regexToken

/**
 * SQL Lexer tokens - dialect-aware
 */
object SqlTokens {
    // Keywords
    val SELECT = regexToken("SELECT", "(?i)SELECT")
    val FROM = regexToken("FROM", "(?i)FROM")
    val WHERE = regexToken("WHERE", "(?i)WHERE")
    val AND = regexToken("AND", "(?i)AND")
    val OR = regexToken("OR", "(?i)OR")
    val NOT = regexToken("NOT", "(?i)NOT")
    val AS = regexToken("AS", "(?i)AS")
    val ON = regexToken("ON", "(?i)ON")
    val JOIN = regexToken("JOIN", "(?i)JOIN")
    val LEFT = regexToken("LEFT", "(?i)LEFT")
    val RIGHT = regexToken("RIGHT", "(?i)RIGHT")
    val INNER = regexToken("INNER", "(?i)INNER")
    val OUTER = regexToken("OUTER", "(?i)OUTER")
    val FULL = regexToken("FULL", "(?i)FULL")
    val CROSS = regexToken("CROSS", "(?i)CROSS")
    val GROUP = regexToken("GROUP", "(?i)GROUP")
    val BY = regexToken("BY", "(?i)BY")
    val HAVING = regexToken("HAVING", "(?i)HAVING")
    val ORDER = regexToken("ORDER", "(?i)ORDER")
    val ASC = regexToken("ASC", "(?i)ASC")
    val DESC = regexToken("DESC", "(?i)DESC")
    val LIMIT = regexToken("LIMIT", "(?i)LIMIT")
    val OFFSET = regexToken("OFFSET", "(?i)OFFSET")
    val NULLS = regexToken("NULLS", "(?i)NULLS")
    val FIRST = regexToken("FIRST", "(?i)FIRST")
    val LAST = regexToken("LAST", "(?i)LAST")
    val DISTINCT = regexToken("DISTINCT", "(?i)DISTINCT")
    val ALL = regexToken("ALL", "(?i)ALL")
    val UNION = regexToken("UNION", "(?i)UNION")
    val INTERSECT = regexToken("INTERSECT", "(?i)INTERSECT")
    val EXCEPT = regexToken("EXCEPT", "(?i)EXCEPT")
    val INSERT = regexToken("INSERT", "(?i)INSERT")
    val INTO = regexToken("INTO", "(?i)INTO")
    val VALUES = regexToken("VALUES", "(?i)VALUES")
    val UPDATE = regexToken("UPDATE", "(?i)UPDATE")
    val SET = regexToken("SET", "(?i)SET")
    val DELETE = regexToken("DELETE", "(?i)DELETE")
    val CREATE = regexToken("CREATE", "(?i)CREATE")
    val TABLE = regexToken("TABLE", "(?i)TABLE")
    val INDEX = regexToken("INDEX", "(?i)INDEX")
    val DROP = regexToken("DROP", "(?i)DROP")
    val ALTER = regexToken("ALTER", "(?i)ALTER")
    val ADD = regexToken("ADD", "(?i)ADD")
    val COLUMN = regexToken("COLUMN", "(?i)COLUMN")
    val PRIMARY = regexToken("PRIMARY", "(?i)PRIMARY")
    val KEY = regexToken("KEY", "(?i)KEY")
    val FOREIGN = regexToken("FOREIGN", "(?i)FOREIGN")
    val REFERENCES = regexToken("REFERENCES", "(?i)REFERENCES")
    val CONSTRAINT = regexToken("CONSTRAINT", "(?i)CONSTRAINT")
    val UNIQUE = regexToken("UNIQUE", "(?i)UNIQUE")
    val CHECK = regexToken("CHECK", "(?i)CHECK")
    val DEFAULT = regexToken("DEFAULT", "(?i)DEFAULT")
    val NULL = regexToken("NULL", "(?i)NULL")
    val TRUE = regexToken("TRUE", "(?i)TRUE")
    val FALSE = regexToken("FALSE", "(?i)FALSE")
    val IS = regexToken("IS", "(?i)IS")
    val IN = regexToken("IN", "(?i)IN")
    val BETWEEN = regexToken("BETWEEN", "(?i)BETWEEN")
    val LIKE = regexToken("LIKE", "(?i)LIKE")
    val ILIKE = regexToken("ILIKE", "(?i)ILIKE")
    val SIMILAR = regexToken("SIMILAR", "(?i)SIMILAR")
    val TO = regexToken("TO", "(?i)TO")
    val ESCAPE = regexToken("ESCAPE", "(?i)ESCAPE")
    val CASE = regexToken("CASE", "(?i)CASE")
    val WHEN = regexToken("WHEN", "(?i)WHEN")
    val THEN = regexToken("THEN", "(?i)THEN")
    val ELSE = regexToken("ELSE", "(?i)ELSE")
    val END = regexToken("END", "(?i)END")
    val CAST = regexToken("CAST", "(?i)CAST")
    val EXISTS = regexToken("EXISTS", "(?i)EXISTS")
    val ANY = regexToken("ANY", "(?i)ANY")
    val SOME = regexToken("SOME", "(?i)SOME")
    val WITH = regexToken("WITH", "(?i)WITH")
    val RECURSIVE = regexToken("RECURSIVE", "(?i)RECURSIVE")
    val OVER = regexToken("OVER", "(?i)OVER")
    val PARTITION = regexToken("PARTITION", "(?i)PARTITION")
    val ROWS = regexToken("ROWS", "(?i)ROWS")
    val RANGE = regexToken("RANGE", "(?i)RANGE")
    val UNBOUNDED = regexToken("UNBOUNDED", "(?i)UNBOUNDED")
    val PRECEDING = regexToken("PRECEDING", "(?i)PRECEDING")
    val FOLLOWING = regexToken("FOLLOWING", "(?i)FOLLOWING")
    val CURRENT = regexToken("CURRENT", "(?i)CURRENT")
    val ROW = regexToken("ROW", "(?i)ROW")
    val USING = regexToken("USING", "(?i)USING")

    // PostgreSQL-specific
    val RETURNING = regexToken("RETURNING", "(?i)RETURNING")
    val CONFLICT = regexToken("CONFLICT", "(?i)CONFLICT")
    val DO = regexToken("DO", "(?i)DO")
    val NOTHING = regexToken("NOTHING", "(?i)NOTHING")
    val LATERAL = regexToken("LATERAL", "(?i)LATERAL")

    // MySQL-specific
    val REPLACE = regexToken("REPLACE", "(?i)REPLACE")
    val IGNORE = regexToken("IGNORE", "(?i)IGNORE")
    val DUPLICATE = regexToken("DUPLICATE", "(?i)DUPLICATE")

    // Operators
    val EQ = literalToken("=")
    val NEQ = regexToken("NEQ", "(<>|!=)")
    val LT = literalToken("<")
    val LTE = literalToken("<=")
    val GT = literalToken(">")
    val GTE = literalToken(">=")
    val PLUS = literalToken("+")
    val MINUS = literalToken("-")
    val STAR = literalToken("*")
    val SLASH = literalToken("/")
    val PERCENT = literalToken("%")
    val CONCAT = literalToken("||")
    val REGEX_MATCH = literalToken("~")
    val REGEX_NOT_MATCH = literalToken("!~")
    val REGEX_MATCH_CI = literalToken("~*")
    val REGEX_NOT_MATCH_CI = literalToken("!~*")
    val JSON_ACCESS = literalToken("->")
    val JSON_ACCESS_TEXT = literalToken("->>")
    val JSON_PATH = literalToken("#>")
    val JSON_PATH_TEXT = literalToken("#>>")
    val DOUBLE_COLON = literalToken("::")

    // Punctuation
    val LPAREN = literalToken("(")
    val RPAREN = literalToken(")")
    val LBRACKET = literalToken("[")
    val RBRACKET = literalToken("]")
    val COMMA = literalToken(",")
    val DOT = literalToken(".")
    val SEMICOLON = literalToken(";")
    val COLON = literalToken(":")

    // Literals
    val STRING = regexToken("STRING", "'([^']*('')*)*'")
    val DOLLAR_STRING = regexToken("DOLLAR_STRING", "\\$\\$.*?\\$\\$")
    val NUMBER = regexToken("NUMBER", "-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?")
    val HEX_NUMBER = regexToken("HEX_NUMBER", "0[xX][0-9a-fA-F]+")

    // Identifiers
    val QUOTED_ID = regexToken("QUOTED_ID", "\"([^\"]*(\"\")*)\"")
    val BACKTICK_ID = regexToken("BACKTICK_ID", "`[^`]+`")
    val ID = regexToken("ID", "[a-zA-Z_][a-zA-Z0-9_]*")
    val PARAM = regexToken("PARAM", "\\$\\d+|\\?|:[a-zA-Z_][a-zA-Z0-9_]*")

    // Comments
    val LINE_COMMENT = regexToken("LINE_COMMENT", "--[^\\n]*")
    val BLOCK_COMMENT = regexToken("BLOCK_COMMENT", "/\\*[\\s\\S]*?\\*/")

    // Whitespace
    val WS = regexToken("WS", "\\s+", ignore = true)

    val allTokens =
        listOf(
            // Comments first (to avoid conflicts)
            LINE_COMMENT,
            BLOCK_COMMENT,
            // Multi-char operators before single-char
            JSON_ACCESS_TEXT,
            JSON_ACCESS,
            JSON_PATH_TEXT,
            JSON_PATH,
            DOUBLE_COLON,
            CONCAT,
            LTE,
            GTE,
            NEQ,
            REGEX_MATCH_CI,
            REGEX_NOT_MATCH_CI,
            REGEX_MATCH,
            REGEX_NOT_MATCH,
            // Keywords (before ID)
            SELECT,
            FROM,
            WHERE,
            AND,
            OR,
            NOT,
            AS,
            ON,
            JOIN,
            LEFT,
            RIGHT,
            INNER,
            OUTER,
            FULL,
            CROSS,
            GROUP,
            BY,
            HAVING,
            ORDER,
            ASC,
            DESC,
            LIMIT,
            OFFSET,
            NULLS,
            FIRST,
            LAST,
            DISTINCT,
            ALL,
            UNION,
            INTERSECT,
            EXCEPT,
            INSERT,
            INTO,
            VALUES,
            UPDATE,
            SET,
            DELETE,
            CREATE,
            TABLE,
            INDEX,
            DROP,
            ALTER,
            ADD,
            COLUMN,
            PRIMARY,
            KEY,
            FOREIGN,
            REFERENCES,
            CONSTRAINT,
            UNIQUE,
            CHECK,
            DEFAULT,
            NULL,
            TRUE,
            FALSE,
            IS,
            IN,
            BETWEEN,
            LIKE,
            ILIKE,
            SIMILAR,
            TO,
            ESCAPE,
            CASE,
            WHEN,
            THEN,
            ELSE,
            END,
            CAST,
            EXISTS,
            ANY,
            SOME,
            WITH,
            RECURSIVE,
            OVER,
            PARTITION,
            ROWS,
            RANGE,
            UNBOUNDED,
            PRECEDING,
            FOLLOWING,
            CURRENT,
            ROW,
            RETURNING,
            CONFLICT,
            DO,
            NOTHING,
            LATERAL,
            REPLACE,
            IGNORE,
            DUPLICATE,
            USING,
            // Literals
            STRING,
            DOLLAR_STRING,
            HEX_NUMBER,
            NUMBER,
            // Identifiers
            QUOTED_ID,
            BACKTICK_ID,
            PARAM,
            ID,
            // Single-char operators and punctuation
            EQ,
            LT,
            GT,
            PLUS,
            MINUS,
            STAR,
            SLASH,
            PERCENT,
            LPAREN,
            RPAREN,
            LBRACKET,
            RBRACKET,
            COMMA,
            DOT,
            SEMICOLON,
            COLON,
            // Whitespace
            WS,
        )
}
