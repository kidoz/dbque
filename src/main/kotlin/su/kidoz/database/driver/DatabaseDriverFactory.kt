package su.kidoz.database.driver

import su.kidoz.core.model.DatabaseType

object DatabaseDriverFactory {
    private val drivers =
        mapOf(
            DatabaseType.POSTGRESQL to PostgresDriver(),
            DatabaseType.MYSQL to MySqlDriver(),
            DatabaseType.SQLITE to SqliteDriver(),
            DatabaseType.H2 to H2Driver(),
            DatabaseType.MONGODB to MongoDbDriver(),
            DatabaseType.ELASTICSEARCH to ElasticsearchDriver(),
        )

    fun getDriver(type: DatabaseType): DatabaseDriver = drivers[type] ?: throw IllegalArgumentException("Unsupported database type: $type")

    /**
     * Get MongoDB-specific driver for MongoDB connections.
     */
    fun getMongoDriver(): MongoDbDriver = drivers[DatabaseType.MONGODB] as MongoDbDriver

    /**
     * Get Elasticsearch-specific driver for Elasticsearch connections.
     */
    fun getElasticsearchDriver(): ElasticsearchDriver = drivers[DatabaseType.ELASTICSEARCH] as ElasticsearchDriver
}
