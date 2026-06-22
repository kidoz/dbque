package su.kidoz.feature.diagram

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagramDdlApplierTest {
    @Test
    fun apply_executesMultipleStatementsAndRestoresAutoCommit() =
        runTest {
            DriverManager.getConnection("jdbc:h2:mem:diagram_ddl_apply_success;DB_CLOSE_DELAY=-1").use { connection ->
                val count =
                    DiagramDdlApplier.apply(
                        connection,
                        """
                        CREATE TABLE customers(id INT PRIMARY KEY);
                        CREATE TABLE orders(id INT PRIMARY KEY, customer_id INT);
                        """.trimIndent(),
                    )

                assertEquals(2, count)
                assertEquals(true, connection.autoCommit)
                assertTableExists(connection, "CUSTOMERS")
                assertTableExists(connection, "ORDERS")
            }
        }

    @Test
    fun apply_rollsBackPriorStatementsOnFailure() =
        runTest {
            DriverManager.getConnection("jdbc:h2:mem:diagram_ddl_apply_failure;DB_CLOSE_DELAY=-1").use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("CREATE TABLE audit(id INT PRIMARY KEY)")
                }

                val result =
                    runCatching {
                        DiagramDdlApplier.apply(
                            connection,
                            """
                            INSERT INTO audit(id) VALUES (1);
                            CREATE TABLE broken(;
                            """.trimIndent(),
                        )
                    }

                assertTrue(result.isFailure)
                assertEquals(true, connection.autoCommit)
                assertEquals(0, rowCount(connection, "audit"))
            }
        }

    @Test
    fun apply_ignoresCommentOnlyDdl() =
        runTest {
            DriverManager.getConnection("jdbc:h2:mem:diagram_ddl_apply_comments;DB_CLOSE_DELAY=-1").use { connection ->
                val count = DiagramDdlApplier.apply(connection, "-- Rebuild this table to add the foreign key.")

                assertEquals(0, count)
            }
        }

    private fun assertTableExists(
        connection: java.sql.Connection,
        tableName: String,
    ) {
        connection.metaData.getTables(null, null, tableName, arrayOf("TABLE")).use { resultSet ->
            assertEquals(true, resultSet.next())
        }
    }

    private fun rowCount(
        connection: java.sql.Connection,
        tableName: String,
    ): Int =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM $tableName").use { resultSet ->
                assertEquals(true, resultSet.next())
                resultSet.getInt(1)
            }
        }
}
