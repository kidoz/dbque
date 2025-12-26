package su.kidoz.database

import co.elastic.clients.elasticsearch.ElasticsearchClient
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.elasticsearch.client.RestClient
import su.kidoz.core.model.ConnectionConfig
import java.sql.Connection

/**
 * Abstraction layer for database connections.
 * Supports both JDBC-based connections (PostgreSQL, MySQL, SQLite, H2)
 * and non-JDBC connections (MongoDB, Elasticsearch).
 */
sealed interface DatabaseConnection {
    val config: ConnectionConfig

    fun close()

    val isValid: Boolean
}

/**
 * JDBC-based database connection using HikariCP connection pool.
 */
class JdbcDatabaseConnection(
    override val config: ConnectionConfig,
    private val dataSource: HikariDataSource,
) : DatabaseConnection {
    fun getJdbcConnection(): Connection = dataSource.connection

    override fun close() {
        dataSource.close()
    }

    override val isValid: Boolean
        get() = !dataSource.isClosed
}

/**
 * MongoDB database connection using the official MongoDB Kotlin driver.
 */
class MongoDatabaseConnection(
    override val config: ConnectionConfig,
    val client: MongoClient,
    private val databaseName: String,
) : DatabaseConnection {
    val database: MongoDatabase
        get() = client.getDatabase(databaseName)

    override fun close() {
        client.close()
    }

    override val isValid: Boolean
        get() =
            try {
                runBlocking {
                    client.getDatabase("admin").runCommand(Document("ping", 1))
                }
                true
            } catch (e: Exception) {
                false
            }

    /**
     * Get a specific database by name.
     */
    fun getDatabase(name: String): MongoDatabase = client.getDatabase(name)
}

/**
 * Elasticsearch database connection using the official Elasticsearch Java client.
 */
class ElasticsearchDatabaseConnection(
    override val config: ConnectionConfig,
    val client: ElasticsearchClient,
    val lowLevelClient: RestClient,
) : DatabaseConnection {
    override fun close() {
        lowLevelClient.close()
    }

    override val isValid: Boolean
        get() =
            try {
                client.ping().value()
            } catch (e: Exception) {
                false
            }
}
