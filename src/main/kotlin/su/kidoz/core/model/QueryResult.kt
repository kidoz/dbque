package su.kidoz.core.model

data class QueryResult(
    val columns: List<ResultColumn>,
    val rows: List<List<Any?>>,
    val rowCount: Int,
    val affectedRows: Int = 0,
    val executionTimeMs: Long,
    val query: String,
    val isResultSet: Boolean = true,
    val warnings: List<String> = emptyList(),
    val error: String? = null,
) {
    val hasError: Boolean get() = error != null
    val isEmpty: Boolean get() = rows.isEmpty()
}

data class ResultColumn(
    val name: String,
    val label: String,
    val typeName: String,
    val jdbcType: Int,
    val displaySize: Int,
    val precision: Int,
    val scale: Int,
    val nullable: Boolean,
    val autoIncrement: Boolean = false,
    val tableName: String? = null,
    val schemaName: String? = null,
)

data class QueryExecution(
    val query: String,
    val parameters: List<Any?> = emptyList(),
    val maxRows: Int = 0,
    val timeout: Int = 0,
    val fetchSize: Int = 100,
)

sealed class QueryExecutionResult {
    data class Success(
        val result: QueryResult,
    ) : QueryExecutionResult()

    data class MultiResult(
        val results: List<QueryResult>,
    ) : QueryExecutionResult()

    data class Error(
        val message: String,
        val sqlState: String? = null,
        val errorCode: Int? = null,
    ) : QueryExecutionResult()
}
