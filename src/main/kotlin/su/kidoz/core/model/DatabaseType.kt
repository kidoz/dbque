package su.kidoz.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class DatabaseType(
    val displayName: String,
    val driverClass: String,
    val defaultPort: Int,
    val urlTemplate: String,
) {
    POSTGRESQL(
        displayName = "PostgreSQL",
        driverClass = "org.postgresql.Driver",
        defaultPort = 5432,
        urlTemplate = "jdbc:postgresql://{host}:{port}/{database}",
    ),
    MYSQL(
        displayName = "MySQL",
        driverClass = "com.mysql.cj.jdbc.Driver",
        defaultPort = 3306,
        urlTemplate = "jdbc:mysql://{host}:{port}/{database}",
    ),
    SQLITE(
        displayName = "SQLite",
        driverClass = "org.sqlite.JDBC",
        defaultPort = 0,
        urlTemplate = "jdbc:sqlite:{path}",
    ),
    H2(
        displayName = "H2",
        driverClass = "org.h2.Driver",
        defaultPort = 9092,
        urlTemplate = "jdbc:h2:{mode}:{path}",
    ),
    MONGODB(
        displayName = "MongoDB",
        driverClass = "", // MongoDB doesn't use JDBC
        defaultPort = 27017,
        urlTemplate = "mongodb://{host}:{port}/{database}",
    ),
    ELASTICSEARCH(
        displayName = "Elasticsearch",
        driverClass = "", // Elasticsearch doesn't use JDBC
        defaultPort = 9200,
        urlTemplate = "http://{host}:{port}",
    ),
    ;

    fun buildUrl(
        host: String = "localhost",
        port: Int = defaultPort,
        database: String = "",
        path: String = "",
        mode: String = "file",
    ): String =
        urlTemplate
            .replace("{host}", host)
            .replace("{port}", port.toString())
            .replace("{database}", database)
            .replace("{path}", path)
            .replace("{mode}", mode)
}
