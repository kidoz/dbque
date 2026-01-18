package su.kidoz.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import su.kidoz.core.model.ConnectionConfig
import su.kidoz.core.model.DatabaseType
import su.kidoz.database.driver.DatabaseDriver
import su.kidoz.database.driver.DatabaseDriverFactory
import su.kidoz.database.ssh.SshTunnel
import su.kidoz.database.ssh.SshTunnelManager
import su.kidoz.database.ssh.TunnelConfig
import java.sql.Connection

data class ActiveConnection(
    val config: ConnectionConfig,
    val driver: DatabaseDriver,
    val databaseConnection: DatabaseConnection,
    val sshTunnel: SshTunnel? = null,
) {
    /**
     * Get JDBC connection for SQL databases.
     * @throws IllegalStateException if this is a MongoDB or Elasticsearch connection
     */
    fun getConnection(): Connection =
        when (val conn = databaseConnection) {
            is JdbcDatabaseConnection -> conn.getJdbcConnection()
            is MongoDatabaseConnection -> throw IllegalStateException(
                "Cannot get JDBC Connection from MongoDB. Use getMongoConnection() instead.",
            )
            is ElasticsearchDatabaseConnection -> throw IllegalStateException(
                "Cannot get JDBC Connection from Elasticsearch. Use getElasticsearchConnection() instead.",
            )
        }

    /**
     * Get MongoDB connection for MongoDB databases.
     * @throws IllegalStateException if this is not a MongoDB connection
     */
    fun getMongoConnection(): MongoDatabaseConnection =
        when (val conn = databaseConnection) {
            is MongoDatabaseConnection -> conn
            is JdbcDatabaseConnection -> throw IllegalStateException(
                "Cannot get MongoDB connection from JDBC database. Use getConnection() instead.",
            )
            is ElasticsearchDatabaseConnection -> throw IllegalStateException(
                "Cannot get MongoDB connection from Elasticsearch. Use getElasticsearchConnection() instead.",
            )
        }

    /**
     * Get Elasticsearch connection for Elasticsearch databases.
     * @throws IllegalStateException if this is not an Elasticsearch connection
     */
    fun getElasticsearchConnection(): ElasticsearchDatabaseConnection =
        when (val conn = databaseConnection) {
            is ElasticsearchDatabaseConnection -> conn
            is JdbcDatabaseConnection -> throw IllegalStateException(
                "Cannot get Elasticsearch connection from JDBC database. Use getConnection() instead.",
            )
            is MongoDatabaseConnection -> throw IllegalStateException(
                "Cannot get Elasticsearch connection from MongoDB. Use getMongoConnection() instead.",
            )
        }

    /**
     * Check if this is a MongoDB connection.
     */
    val isMongo: Boolean
        get() = databaseConnection is MongoDatabaseConnection

    /**
     * Check if this is an Elasticsearch connection.
     */
    val isElasticsearch: Boolean
        get() = databaseConnection is ElasticsearchDatabaseConnection

    fun close() {
        databaseConnection.close()
        sshTunnel?.close()
    }

    val isValid: Boolean
        get() = databaseConnection.isValid
}

class ConnectionManager(
    private val sshTunnelManager: SshTunnelManager,
) {
    private val logger = KotlinLogging.logger {}
    private val mutex = Mutex()

    private val _connections = MutableStateFlow<Map<String, ActiveConnection>>(emptyMap())
    val connections: StateFlow<Map<String, ActiveConnection>> = _connections.asStateFlow()

    private val _activeConnectionId = MutableStateFlow<String?>(null)
    val activeConnectionId: StateFlow<String?> = _activeConnectionId.asStateFlow()

    val activeConnection: ActiveConnection?
        get() = _activeConnectionId.value?.let { _connections.value[it] }

    suspend fun connect(config: ConnectionConfig): Result<ActiveConnection> =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    logger.info { "Connecting to ${config.toDisplayString()}" }

                    var sshTunnel: SshTunnel? = null
                    var effectiveConfig = config

                    // Create SSH tunnel if enabled
                    if (config.sshConfig.enabled) {
                        logger.info { "Creating SSH tunnel to ${config.sshConfig.host}:${config.sshConfig.port}" }

                        val tunnelConfig =
                            TunnelConfig(
                                sshConfig = config.sshConfig,
                                remoteHost = config.host,
                                remotePort = config.effectivePort,
                            )

                        val tunnel = sshTunnelManager.createTunnel(config.id, tunnelConfig).getOrThrow()
                        sshTunnel = tunnel

                        // Update config to use tunnel's local port
                        effectiveConfig =
                            config.copy(
                                host = "localhost",
                                port = tunnel.localPort,
                            )

                        logger.info { "SSH tunnel established on local port ${tunnel.localPort}" }
                    }

                    val driver = DatabaseDriverFactory.getDriver(config.type)

                    // Test connection first
                    driver.testConnection(effectiveConfig).getOrThrow()

                    // Create connection based on database type
                    val databaseConnection =
                        when (config.type) {
                            DatabaseType.MONGODB -> connectMongo(effectiveConfig)
                            DatabaseType.ELASTICSEARCH -> connectElasticsearch(effectiveConfig)
                            else -> connectJdbc(effectiveConfig)
                        }

                    val activeConnection = ActiveConnection(config, driver, databaseConnection, sshTunnel)

                    _connections.value = _connections.value + (config.id to activeConnection)
                    _activeConnectionId.value = config.id

                    logger.info { "Connected successfully to ${config.toDisplayString()}" }
                    activeConnection
                }
            }
        }

    private suspend fun connectJdbc(config: ConnectionConfig): JdbcDatabaseConnection {
        val hikariConfig =
            HikariConfig().apply {
                jdbcUrl = config.buildJdbcUrl()
                driverClassName = config.type.driverClass
                if (config.username.isNotBlank()) {
                    username = config.username
                }
                if (config.password.isNotBlank()) {
                    password = config.password
                }
                maximumPoolSize = 5
                minimumIdle = 1
                connectionTimeout = 30000
                idleTimeout = 600000
                maxLifetime = 1800000
                poolName = "DBQue-${config.id}"

                config.properties.forEach { (key, value) ->
                    addDataSourceProperty(key, value)
                }
            }

        val dataSource = HikariDataSource(hikariConfig)
        return JdbcDatabaseConnection(config, dataSource)
    }

    private suspend fun connectMongo(config: ConnectionConfig): MongoDatabaseConnection {
        val mongoDriver = DatabaseDriverFactory.getMongoDriver()
        return mongoDriver.connectMongo(config)
    }

    private suspend fun connectElasticsearch(config: ConnectionConfig): ElasticsearchDatabaseConnection {
        val elasticsearchDriver = DatabaseDriverFactory.getElasticsearchDriver()
        return elasticsearchDriver.connectElasticsearch(config)
    }

    suspend fun disconnect(connectionId: String) =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                _connections.value[connectionId]?.let { connection ->
                    logger.info { "Disconnecting from ${connection.config.toDisplayString()}" }
                    connection.close()
                    sshTunnelManager.closeTunnel(connectionId)
                    _connections.value = _connections.value - connectionId

                    if (_activeConnectionId.value == connectionId) {
                        _activeConnectionId.value = _connections.value.keys.firstOrNull()
                    }
                }
            }
        }

    suspend fun disconnectAll() =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                _connections.value.values.forEach { it.close() }
                sshTunnelManager.closeAll()
                _connections.value = emptyMap()
                _activeConnectionId.value = null
            }
        }

    fun setActiveConnection(connectionId: String) {
        if (_connections.value.containsKey(connectionId)) {
            _activeConnectionId.value = connectionId
        }
    }

    suspend fun testConnection(config: ConnectionConfig): Result<String> =
        withContext(Dispatchers.IO) {
            val driver = DatabaseDriverFactory.getDriver(config.type)
            driver.testConnection(config)
        }
}
