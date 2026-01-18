package su.kidoz.feature.editor.autocomplete

/**
 * Parameter information for a function
 */
data class FunctionParameter(
    val name: String,
    val type: String,
    val isOptional: Boolean = false,
    val defaultValue: String? = null,
)

/**
 * Complete function signature with documentation
 */
data class FunctionSignature(
    val name: String,
    val parameters: List<FunctionParameter>,
    val returnType: String,
    val description: String,
    val category: FunctionCategory,
)

/**
 * Function categories for grouping
 */
enum class FunctionCategory {
    AGGREGATE,
    STRING,
    NUMERIC,
    DATE_TIME,
    WINDOW,
    CONDITIONAL,
    TYPE_CONVERSION,
    JSON,
    ARRAY,
    OTHER,
}

/**
 * Provides function signatures for SQL autocompletion with parameter hints.
 */
class FunctionSignatureProvider {
    private val functions: Map<String, FunctionSignature> =
        buildMap {
            // Aggregate Functions
            put(
                "COUNT",
                FunctionSignature(
                    name = "COUNT",
                    parameters = listOf(FunctionParameter("expression", "any")),
                    returnType = "bigint",
                    description = "Returns the number of input rows for which the expression is not null",
                    category = FunctionCategory.AGGREGATE,
                ),
            )
            put(
                "SUM",
                FunctionSignature(
                    name = "SUM",
                    parameters = listOf(FunctionParameter("expression", "numeric")),
                    returnType = "numeric",
                    description = "Returns the sum of all input values",
                    category = FunctionCategory.AGGREGATE,
                ),
            )
            put(
                "AVG",
                FunctionSignature(
                    name = "AVG",
                    parameters = listOf(FunctionParameter("expression", "numeric")),
                    returnType = "numeric",
                    description = "Returns the average of all input values",
                    category = FunctionCategory.AGGREGATE,
                ),
            )
            put(
                "MIN",
                FunctionSignature(
                    name = "MIN",
                    parameters = listOf(FunctionParameter("expression", "any")),
                    returnType = "same as input",
                    description = "Returns the minimum value of the expression",
                    category = FunctionCategory.AGGREGATE,
                ),
            )
            put(
                "MAX",
                FunctionSignature(
                    name = "MAX",
                    parameters = listOf(FunctionParameter("expression", "any")),
                    returnType = "same as input",
                    description = "Returns the maximum value of the expression",
                    category = FunctionCategory.AGGREGATE,
                ),
            )
            put(
                "STRING_AGG",
                FunctionSignature(
                    name = "STRING_AGG",
                    parameters =
                        listOf(
                            FunctionParameter("expression", "text"),
                            FunctionParameter("delimiter", "text"),
                        ),
                    returnType = "text",
                    description = "Concatenates input values into a string, separated by delimiter",
                    category = FunctionCategory.AGGREGATE,
                ),
            )
            put(
                "ARRAY_AGG",
                FunctionSignature(
                    name = "ARRAY_AGG",
                    parameters = listOf(FunctionParameter("expression", "any")),
                    returnType = "array",
                    description = "Collects all input values into an array",
                    category = FunctionCategory.AGGREGATE,
                ),
            )

            // String Functions
            put(
                "CONCAT",
                FunctionSignature(
                    name = "CONCAT",
                    parameters =
                        listOf(
                            FunctionParameter("str1", "text"),
                            FunctionParameter("str2", "text"),
                            FunctionParameter("...", "text", isOptional = true),
                        ),
                    returnType = "text",
                    description = "Concatenates all arguments",
                    category = FunctionCategory.STRING,
                ),
            )
            put(
                "SUBSTRING",
                FunctionSignature(
                    name = "SUBSTRING",
                    parameters =
                        listOf(
                            FunctionParameter("string", "text"),
                            FunctionParameter("start", "int"),
                            FunctionParameter("length", "int", isOptional = true),
                        ),
                    returnType = "text",
                    description = "Extracts substring from string starting at position",
                    category = FunctionCategory.STRING,
                ),
            )
            put(
                "TRIM",
                FunctionSignature(
                    name = "TRIM",
                    parameters = listOf(FunctionParameter("string", "text")),
                    returnType = "text",
                    description = "Removes leading and trailing whitespace",
                    category = FunctionCategory.STRING,
                ),
            )
            put(
                "UPPER",
                FunctionSignature(
                    name = "UPPER",
                    parameters = listOf(FunctionParameter("string", "text")),
                    returnType = "text",
                    description = "Converts string to uppercase",
                    category = FunctionCategory.STRING,
                ),
            )
            put(
                "LOWER",
                FunctionSignature(
                    name = "LOWER",
                    parameters = listOf(FunctionParameter("string", "text")),
                    returnType = "text",
                    description = "Converts string to lowercase",
                    category = FunctionCategory.STRING,
                ),
            )
            put(
                "LENGTH",
                FunctionSignature(
                    name = "LENGTH",
                    parameters = listOf(FunctionParameter("string", "text")),
                    returnType = "int",
                    description = "Returns the number of characters in string",
                    category = FunctionCategory.STRING,
                ),
            )
            put(
                "REPLACE",
                FunctionSignature(
                    name = "REPLACE",
                    parameters =
                        listOf(
                            FunctionParameter("string", "text"),
                            FunctionParameter("from", "text"),
                            FunctionParameter("to", "text"),
                        ),
                    returnType = "text",
                    description = "Replaces all occurrences of 'from' with 'to' in string",
                    category = FunctionCategory.STRING,
                ),
            )
            put(
                "SPLIT_PART",
                FunctionSignature(
                    name = "SPLIT_PART",
                    parameters =
                        listOf(
                            FunctionParameter("string", "text"),
                            FunctionParameter("delimiter", "text"),
                            FunctionParameter("field", "int"),
                        ),
                    returnType = "text",
                    description = "Splits string on delimiter and returns the given field (1-indexed)",
                    category = FunctionCategory.STRING,
                ),
            )
            put(
                "LEFT",
                FunctionSignature(
                    name = "LEFT",
                    parameters =
                        listOf(
                            FunctionParameter("string", "text"),
                            FunctionParameter("n", "int"),
                        ),
                    returnType = "text",
                    description = "Returns first n characters of string",
                    category = FunctionCategory.STRING,
                ),
            )
            put(
                "RIGHT",
                FunctionSignature(
                    name = "RIGHT",
                    parameters =
                        listOf(
                            FunctionParameter("string", "text"),
                            FunctionParameter("n", "int"),
                        ),
                    returnType = "text",
                    description = "Returns last n characters of string",
                    category = FunctionCategory.STRING,
                ),
            )

            // Numeric Functions
            put(
                "ABS",
                FunctionSignature(
                    name = "ABS",
                    parameters = listOf(FunctionParameter("x", "numeric")),
                    returnType = "numeric",
                    description = "Returns the absolute value of x",
                    category = FunctionCategory.NUMERIC,
                ),
            )
            put(
                "CEIL",
                FunctionSignature(
                    name = "CEIL",
                    parameters = listOf(FunctionParameter("x", "numeric")),
                    returnType = "numeric",
                    description = "Returns smallest integer >= x",
                    category = FunctionCategory.NUMERIC,
                ),
            )
            put(
                "FLOOR",
                FunctionSignature(
                    name = "FLOOR",
                    parameters = listOf(FunctionParameter("x", "numeric")),
                    returnType = "numeric",
                    description = "Returns largest integer <= x",
                    category = FunctionCategory.NUMERIC,
                ),
            )
            put(
                "ROUND",
                FunctionSignature(
                    name = "ROUND",
                    parameters =
                        listOf(
                            FunctionParameter("x", "numeric"),
                            FunctionParameter("precision", "int", isOptional = true, defaultValue = "0"),
                        ),
                    returnType = "numeric",
                    description = "Rounds x to specified decimal places",
                    category = FunctionCategory.NUMERIC,
                ),
            )
            put(
                "MOD",
                FunctionSignature(
                    name = "MOD",
                    parameters =
                        listOf(
                            FunctionParameter("x", "numeric"),
                            FunctionParameter("y", "numeric"),
                        ),
                    returnType = "numeric",
                    description = "Returns remainder of x divided by y",
                    category = FunctionCategory.NUMERIC,
                ),
            )
            put(
                "POWER",
                FunctionSignature(
                    name = "POWER",
                    parameters =
                        listOf(
                            FunctionParameter("base", "numeric"),
                            FunctionParameter("exponent", "numeric"),
                        ),
                    returnType = "numeric",
                    description = "Returns base raised to the power of exponent",
                    category = FunctionCategory.NUMERIC,
                ),
            )
            put(
                "SQRT",
                FunctionSignature(
                    name = "SQRT",
                    parameters = listOf(FunctionParameter("x", "numeric")),
                    returnType = "numeric",
                    description = "Returns the square root of x",
                    category = FunctionCategory.NUMERIC,
                ),
            )

            // Date/Time Functions
            put(
                "NOW",
                FunctionSignature(
                    name = "NOW",
                    parameters = emptyList(),
                    returnType = "timestamp with time zone",
                    description = "Returns current date and time",
                    category = FunctionCategory.DATE_TIME,
                ),
            )
            put(
                "CURRENT_DATE",
                FunctionSignature(
                    name = "CURRENT_DATE",
                    parameters = emptyList(),
                    returnType = "date",
                    description = "Returns current date",
                    category = FunctionCategory.DATE_TIME,
                ),
            )
            put(
                "CURRENT_TIME",
                FunctionSignature(
                    name = "CURRENT_TIME",
                    parameters = emptyList(),
                    returnType = "time with time zone",
                    description = "Returns current time",
                    category = FunctionCategory.DATE_TIME,
                ),
            )
            put(
                "CURRENT_TIMESTAMP",
                FunctionSignature(
                    name = "CURRENT_TIMESTAMP",
                    parameters = emptyList(),
                    returnType = "timestamp with time zone",
                    description = "Returns current date and time",
                    category = FunctionCategory.DATE_TIME,
                ),
            )
            put(
                "DATE_TRUNC",
                FunctionSignature(
                    name = "DATE_TRUNC",
                    parameters =
                        listOf(
                            FunctionParameter("precision", "text"),
                            FunctionParameter("timestamp", "timestamp"),
                        ),
                    returnType = "timestamp",
                    description = "Truncates timestamp to specified precision (year, month, day, hour, minute, second)",
                    category = FunctionCategory.DATE_TIME,
                ),
            )
            put(
                "EXTRACT",
                FunctionSignature(
                    name = "EXTRACT",
                    parameters =
                        listOf(
                            FunctionParameter("field", "text"),
                            FunctionParameter("source", "timestamp"),
                        ),
                    returnType = "numeric",
                    description = "Extracts field from timestamp (year, month, day, hour, minute, second)",
                    category = FunctionCategory.DATE_TIME,
                ),
            )
            put(
                "DATE_PART",
                FunctionSignature(
                    name = "DATE_PART",
                    parameters =
                        listOf(
                            FunctionParameter("field", "text"),
                            FunctionParameter("source", "timestamp"),
                        ),
                    returnType = "double precision",
                    description = "Extracts field from timestamp (year, month, day, hour, minute, second)",
                    category = FunctionCategory.DATE_TIME,
                ),
            )
            put(
                "AGE",
                FunctionSignature(
                    name = "AGE",
                    parameters =
                        listOf(
                            FunctionParameter("timestamp1", "timestamp"),
                            FunctionParameter("timestamp2", "timestamp", isOptional = true),
                        ),
                    returnType = "interval",
                    description = "Returns interval between timestamps (or from now if only one argument)",
                    category = FunctionCategory.DATE_TIME,
                ),
            )

            // Window Functions
            put(
                "ROW_NUMBER",
                FunctionSignature(
                    name = "ROW_NUMBER",
                    parameters = emptyList(),
                    returnType = "bigint",
                    description = "Returns sequential row number within partition",
                    category = FunctionCategory.WINDOW,
                ),
            )
            put(
                "RANK",
                FunctionSignature(
                    name = "RANK",
                    parameters = emptyList(),
                    returnType = "bigint",
                    description = "Returns rank with gaps for ties",
                    category = FunctionCategory.WINDOW,
                ),
            )
            put(
                "DENSE_RANK",
                FunctionSignature(
                    name = "DENSE_RANK",
                    parameters = emptyList(),
                    returnType = "bigint",
                    description = "Returns rank without gaps for ties",
                    category = FunctionCategory.WINDOW,
                ),
            )
            put(
                "NTILE",
                FunctionSignature(
                    name = "NTILE",
                    parameters = listOf(FunctionParameter("num_buckets", "int")),
                    returnType = "int",
                    description = "Divides rows into n roughly equal groups",
                    category = FunctionCategory.WINDOW,
                ),
            )
            put(
                "LAG",
                FunctionSignature(
                    name = "LAG",
                    parameters =
                        listOf(
                            FunctionParameter("value", "any"),
                            FunctionParameter("offset", "int", isOptional = true, defaultValue = "1"),
                            FunctionParameter("default", "any", isOptional = true),
                        ),
                    returnType = "same as input",
                    description = "Returns value from previous row",
                    category = FunctionCategory.WINDOW,
                ),
            )
            put(
                "LEAD",
                FunctionSignature(
                    name = "LEAD",
                    parameters =
                        listOf(
                            FunctionParameter("value", "any"),
                            FunctionParameter("offset", "int", isOptional = true, defaultValue = "1"),
                            FunctionParameter("default", "any", isOptional = true),
                        ),
                    returnType = "same as input",
                    description = "Returns value from next row",
                    category = FunctionCategory.WINDOW,
                ),
            )
            put(
                "FIRST_VALUE",
                FunctionSignature(
                    name = "FIRST_VALUE",
                    parameters = listOf(FunctionParameter("value", "any")),
                    returnType = "same as input",
                    description = "Returns first value in the window frame",
                    category = FunctionCategory.WINDOW,
                ),
            )
            put(
                "LAST_VALUE",
                FunctionSignature(
                    name = "LAST_VALUE",
                    parameters = listOf(FunctionParameter("value", "any")),
                    returnType = "same as input",
                    description = "Returns last value in the window frame",
                    category = FunctionCategory.WINDOW,
                ),
            )
            put(
                "NTH_VALUE",
                FunctionSignature(
                    name = "NTH_VALUE",
                    parameters =
                        listOf(
                            FunctionParameter("value", "any"),
                            FunctionParameter("n", "int"),
                        ),
                    returnType = "same as input",
                    description = "Returns nth value in the window frame",
                    category = FunctionCategory.WINDOW,
                ),
            )

            // Conditional Functions
            put(
                "COALESCE",
                FunctionSignature(
                    name = "COALESCE",
                    parameters =
                        listOf(
                            FunctionParameter("value1", "any"),
                            FunctionParameter("value2", "any"),
                            FunctionParameter("...", "any", isOptional = true),
                        ),
                    returnType = "same as input",
                    description = "Returns first non-null value",
                    category = FunctionCategory.CONDITIONAL,
                ),
            )
            put(
                "NULLIF",
                FunctionSignature(
                    name = "NULLIF",
                    parameters =
                        listOf(
                            FunctionParameter("value1", "any"),
                            FunctionParameter("value2", "any"),
                        ),
                    returnType = "same as input",
                    description = "Returns null if value1 equals value2, otherwise value1",
                    category = FunctionCategory.CONDITIONAL,
                ),
            )
            put(
                "GREATEST",
                FunctionSignature(
                    name = "GREATEST",
                    parameters =
                        listOf(
                            FunctionParameter("value1", "any"),
                            FunctionParameter("value2", "any"),
                            FunctionParameter("...", "any", isOptional = true),
                        ),
                    returnType = "same as input",
                    description = "Returns the largest value from the list",
                    category = FunctionCategory.CONDITIONAL,
                ),
            )
            put(
                "LEAST",
                FunctionSignature(
                    name = "LEAST",
                    parameters =
                        listOf(
                            FunctionParameter("value1", "any"),
                            FunctionParameter("value2", "any"),
                            FunctionParameter("...", "any", isOptional = true),
                        ),
                    returnType = "same as input",
                    description = "Returns the smallest value from the list",
                    category = FunctionCategory.CONDITIONAL,
                ),
            )

            // Type Conversion
            put(
                "CAST",
                FunctionSignature(
                    name = "CAST",
                    parameters =
                        listOf(
                            FunctionParameter("expression", "any"),
                            FunctionParameter("type", "type"),
                        ),
                    returnType = "specified type",
                    description = "Converts expression to specified type",
                    category = FunctionCategory.TYPE_CONVERSION,
                ),
            )
            put(
                "TO_CHAR",
                FunctionSignature(
                    name = "TO_CHAR",
                    parameters =
                        listOf(
                            FunctionParameter("value", "any"),
                            FunctionParameter("format", "text"),
                        ),
                    returnType = "text",
                    description = "Converts value to text using format",
                    category = FunctionCategory.TYPE_CONVERSION,
                ),
            )
            put(
                "TO_DATE",
                FunctionSignature(
                    name = "TO_DATE",
                    parameters =
                        listOf(
                            FunctionParameter("text", "text"),
                            FunctionParameter("format", "text"),
                        ),
                    returnType = "date",
                    description = "Converts text to date using format",
                    category = FunctionCategory.TYPE_CONVERSION,
                ),
            )
            put(
                "TO_TIMESTAMP",
                FunctionSignature(
                    name = "TO_TIMESTAMP",
                    parameters =
                        listOf(
                            FunctionParameter("text", "text"),
                            FunctionParameter("format", "text"),
                        ),
                    returnType = "timestamp",
                    description = "Converts text to timestamp using format",
                    category = FunctionCategory.TYPE_CONVERSION,
                ),
            )
            put(
                "TO_NUMBER",
                FunctionSignature(
                    name = "TO_NUMBER",
                    parameters =
                        listOf(
                            FunctionParameter("text", "text"),
                            FunctionParameter("format", "text"),
                        ),
                    returnType = "numeric",
                    description = "Converts text to number using format",
                    category = FunctionCategory.TYPE_CONVERSION,
                ),
            )

            // JSON Functions (PostgreSQL)
            put(
                "JSON_BUILD_OBJECT",
                FunctionSignature(
                    name = "JSON_BUILD_OBJECT",
                    parameters =
                        listOf(
                            FunctionParameter("key1", "text"),
                            FunctionParameter("value1", "any"),
                            FunctionParameter("...", "any", isOptional = true),
                        ),
                    returnType = "json",
                    description = "Builds a JSON object from key-value pairs",
                    category = FunctionCategory.JSON,
                ),
            )
            put(
                "JSON_AGG",
                FunctionSignature(
                    name = "JSON_AGG",
                    parameters = listOf(FunctionParameter("expression", "any")),
                    returnType = "json",
                    description = "Aggregates values as a JSON array",
                    category = FunctionCategory.JSON,
                ),
            )
            put(
                "JSONB_BUILD_OBJECT",
                FunctionSignature(
                    name = "JSONB_BUILD_OBJECT",
                    parameters =
                        listOf(
                            FunctionParameter("key1", "text"),
                            FunctionParameter("value1", "any"),
                            FunctionParameter("...", "any", isOptional = true),
                        ),
                    returnType = "jsonb",
                    description = "Builds a JSONB object from key-value pairs",
                    category = FunctionCategory.JSON,
                ),
            )
        }

    /**
     * Get function signature by name
     */
    fun getSignature(functionName: String): FunctionSignature? = functions[functionName.uppercase()]

    /**
     * Get all functions matching a prefix
     */
    fun getFunctionsByPrefix(prefix: String): List<FunctionSignature> {
        val upperPrefix = prefix.uppercase()
        return functions.values.filter { it.name.startsWith(upperPrefix) }
    }

    /**
     * Get all functions in a category
     */
    fun getFunctionsByCategory(category: FunctionCategory): List<FunctionSignature> = functions.values.filter { it.category == category }

    /**
     * Get all available function names
     */
    fun getAllFunctionNames(): Set<String> = functions.keys

    /**
     * Format function signature for display
     */
    fun formatSignature(signature: FunctionSignature): String {
        val params =
            signature.parameters.joinToString(", ") { param ->
                buildString {
                    append(param.name)
                    append(": ")
                    append(param.type)
                    if (param.isOptional) {
                        append(" = ")
                        append(param.defaultValue ?: "null")
                    }
                }
            }
        return "${signature.name}($params) -> ${signature.returnType}"
    }

    /**
     * Format short hint for function
     */
    fun formatShortHint(signature: FunctionSignature): String {
        val params =
            signature.parameters
                .filter { !it.isOptional }
                .joinToString(", ") { it.name }
        return "${signature.name}($params)"
    }
}
