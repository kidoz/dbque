package su.kidoz.feature.diagram

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.kidoz.feature.editor.QuerySplitter
import java.sql.Connection

object DiagramDdlApplier {
    private val logger = KotlinLogging.logger {}

    suspend fun apply(
        connection: Connection,
        ddl: String,
    ): Int =
        withContext(Dispatchers.IO) {
            val statements = QuerySplitter.getAllQueries(ddl).map { it.query.trim() }.filter { it.isNotBlank() }
            if (statements.isEmpty()) {
                return@withContext 0
            }

            val originalAutoCommit = connection.autoCommit
            try {
                connection.autoCommit = false
                connection.createStatement().use { statement ->
                    statements.forEach { sql ->
                        statement.execute(sql)
                    }
                }
                connection.commit()
                statements.size
            } catch (e: Exception) {
                try {
                    connection.rollback()
                } catch (rollbackError: Exception) {
                    logger.warn(rollbackError) { "Failed to roll back diagram DDL transaction" }
                }
                throw e
            } finally {
                try {
                    connection.autoCommit = originalAutoCommit
                } catch (restoreError: Exception) {
                    logger.warn(restoreError) { "Failed to restore auto-commit after applying diagram DDL" }
                }
            }
        }
}
