package su.kidoz.database.executor

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import su.kidoz.core.model.QueryExecution
import su.kidoz.core.model.QueryExecutionResult
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class QueryExecutorTest {
    @Test
    fun execute_returnsMultiResult_forMultipleStatements() =
        runTest {
            DriverManager.getConnection("jdbc:h2:mem:query_exec_test;DB_CLOSE_DELAY=-1").use { connection ->
                connection.createStatement().use { stmt ->
                    stmt.execute("CREATE TABLE items(id INT PRIMARY KEY, name VARCHAR(50))")
                }

                val executor = QueryExecutor()
                val result =
                    executor.execute(
                        connection,
                        QueryExecution(
                            "INSERT INTO items(id, name) VALUES (1, 'alpha'); " +
                                "SELECT name FROM items WHERE id = 1;",
                        ),
                    )

                when (result) {
                    is QueryExecutionResult.MultiResult -> {
                        assertEquals(2, result.results.size)

                        val insertResult = result.results[0]
                        assertFalse(insertResult.isResultSet)
                        assertEquals(1, insertResult.affectedRows)

                        val selectResult = result.results[1]
                        assertTrue(selectResult.isResultSet)
                        assertEquals(1, selectResult.rowCount)
                        assertEquals("alpha", selectResult.rows.first().first())
                    }

                    else -> {
                        fail("Expected MultiResult, got $result")
                    }
                }
            }
        }
}
