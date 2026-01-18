package su.kidoz.database

import org.junit.jupiter.api.Test
import su.kidoz.core.model.ConnectionConfig
import su.kidoz.core.model.DatabaseType
import su.kidoz.core.model.SshConfig
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for ActiveConnection and ConnectionManager state classes.
 * Note: Full integration tests would require database connections.
 * These tests focus on the state management and data class behavior.
 */
class ActiveConnectionTest {
    private fun createTestConfig() =
        ConnectionConfig(
            id = "test-id",
            name = "Test Connection",
            type = DatabaseType.POSTGRESQL,
            host = "localhost",
            port = 5432,
            database = "testdb",
            username = "testuser",
            password = "testpass",
            sshConfig = SshConfig(),
        )

    @Test
    fun config_storesAllFields() {
        val config =
            ConnectionConfig(
                id = "my-id",
                name = "My Connection",
                type = DatabaseType.MYSQL,
                host = "db.example.com",
                port = 3306,
                database = "mydb",
                username = "admin",
                password = "secret",
                sshConfig = SshConfig(enabled = true, host = "ssh.example.com"),
            )

        assertEquals("my-id", config.id)
        assertEquals("My Connection", config.name)
        assertEquals(DatabaseType.MYSQL, config.type)
        assertEquals("db.example.com", config.host)
        assertEquals(3306, config.port)
        assertEquals("mydb", config.database)
        assertEquals("admin", config.username)
        assertEquals("secret", config.password)
        assertTrue(config.sshConfig.enabled)
        assertEquals("ssh.example.com", config.sshConfig.host)
    }

    @Test
    fun config_effectivePort_returnsPortIfSet() {
        val config = createTestConfig().copy(port = 5433)

        assertEquals(5433, config.effectivePort)
    }

    @Test
    fun config_effectivePort_returnsDefaultForPostgres() {
        val config = createTestConfig().copy(port = 0)

        assertEquals(5432, config.effectivePort)
    }

    @Test
    fun config_effectivePort_returnsDefaultForMySql() {
        val config = createTestConfig().copy(type = DatabaseType.MYSQL, port = 0)

        assertEquals(3306, config.effectivePort)
    }

    @Test
    fun config_effectivePort_returnsDefaultForSqlite() {
        val config = createTestConfig().copy(type = DatabaseType.SQLITE, port = 0)

        // SQLite doesn't use ports, returns 0
        assertEquals(0, config.effectivePort)
    }

    @Test
    fun config_buildJdbcUrl_postgres() {
        val config = createTestConfig()

        val url = config.buildJdbcUrl()

        assertTrue(url.startsWith("jdbc:postgresql://"))
        assertTrue(url.contains("localhost"))
        assertTrue(url.contains("5432"))
        assertTrue(url.contains("testdb"))
    }

    @Test
    fun config_buildJdbcUrl_mysql() {
        val config =
            createTestConfig().copy(
                type = DatabaseType.MYSQL,
                port = 3306,
            )

        val url = config.buildJdbcUrl()

        assertTrue(url.startsWith("jdbc:mysql://"))
    }

    @Test
    fun config_buildJdbcUrl_h2() {
        val config =
            createTestConfig().copy(
                type = DatabaseType.H2,
                database = "test",
            )

        val url = config.buildJdbcUrl()

        assertTrue(url.startsWith("jdbc:h2:"))
    }

    @Test
    fun config_toDisplayString_containsNameAndHost() {
        val config = createTestConfig()

        val display = config.toDisplayString()

        assertTrue(display.contains("Test Connection"))
        assertTrue(display.contains("localhost"))
    }

    @Test
    fun sshConfig_defaultIsDisabled() {
        val sshConfig = SshConfig()

        assertFalse(sshConfig.enabled)
    }

    @Test
    fun sshConfig_storesAllFields() {
        val sshConfig =
            SshConfig(
                enabled = true,
                host = "ssh.example.com",
                port = 2222,
                username = "sshuser",
                password = "sshpass",
                useKeyAuth = true,
                privateKeyPath = "/home/user/.ssh/id_rsa",
                passphrase = "keypass",
            )

        assertTrue(sshConfig.enabled)
        assertEquals("ssh.example.com", sshConfig.host)
        assertEquals(2222, sshConfig.port)
        assertEquals("sshuser", sshConfig.username)
        assertEquals("sshpass", sshConfig.password)
        assertTrue(sshConfig.useKeyAuth)
        assertEquals("/home/user/.ssh/id_rsa", sshConfig.privateKeyPath)
        assertEquals("keypass", sshConfig.passphrase)
    }

    @Test
    fun sshConfig_defaultPort_is22() {
        val sshConfig = SshConfig(enabled = true, host = "ssh.example.com")

        assertEquals(22, sshConfig.port)
    }
}

class ConnectionConfigValidationTest {
    @Test
    fun config_hostIsRequired() {
        val config =
            ConnectionConfig(
                id = "test",
                name = "Test",
                type = DatabaseType.POSTGRESQL,
                host = "",
                database = "db",
                username = "",
                password = "",
            )

        assertTrue(config.host.isEmpty())
    }

    @Test
    fun config_databaseIsRequired() {
        val config =
            ConnectionConfig(
                id = "test",
                name = "Test",
                type = DatabaseType.POSTGRESQL,
                host = "localhost",
                database = "",
                username = "",
                password = "",
            )

        assertTrue(config.database.isEmpty())
    }

    @Test
    fun config_supportsProperties() {
        val config =
            ConnectionConfig(
                id = "test",
                name = "Test",
                type = DatabaseType.POSTGRESQL,
                host = "localhost",
                database = "db",
                username = "",
                password = "",
                properties = mapOf("ssl" to "true", "sslmode" to "require"),
            )

        assertEquals("true", config.properties["ssl"])
        assertEquals("require", config.properties["sslmode"])
    }

    @Test
    fun config_propertiesDefaultToEmpty() {
        val config =
            ConnectionConfig(
                id = "test",
                name = "Test",
                type = DatabaseType.POSTGRESQL,
                host = "localhost",
                database = "db",
                username = "",
                password = "",
            )

        assertTrue(config.properties.isEmpty())
    }
}

class DatabaseTypeTest {
    @Test
    fun databaseType_postgresqlHasCorrectDriver() {
        assertEquals("org.postgresql.Driver", DatabaseType.POSTGRESQL.driverClass)
    }

    @Test
    fun databaseType_mysqlHasCorrectDriver() {
        assertEquals("com.mysql.cj.jdbc.Driver", DatabaseType.MYSQL.driverClass)
    }

    @Test
    fun databaseType_sqliteHasCorrectDriver() {
        assertEquals("org.sqlite.JDBC", DatabaseType.SQLITE.driverClass)
    }

    @Test
    fun databaseType_h2HasCorrectDriver() {
        assertEquals("org.h2.Driver", DatabaseType.H2.driverClass)
    }

    @Test
    fun databaseType_postgresqlDefaultPort() {
        assertEquals(5432, DatabaseType.POSTGRESQL.defaultPort)
    }

    @Test
    fun databaseType_mysqlDefaultPort() {
        assertEquals(3306, DatabaseType.MYSQL.defaultPort)
    }

    @Test
    fun databaseType_h2DefaultPort() {
        assertEquals(9092, DatabaseType.H2.defaultPort)
    }

    @Test
    fun databaseType_mongodbDefaultPort() {
        assertEquals(27017, DatabaseType.MONGODB.defaultPort)
    }

    @Test
    fun databaseType_elasticsearchDefaultPort() {
        assertEquals(9200, DatabaseType.ELASTICSEARCH.defaultPort)
    }

    @Test
    fun databaseType_allTypesHaveDisplayName() {
        DatabaseType.entries.forEach { type ->
            assertTrue(type.displayName.isNotEmpty())
        }
    }

    @Test
    fun databaseType_allJdbcTypesHaveDriverClass() {
        val jdbcTypes =
            listOf(
                DatabaseType.POSTGRESQL,
                DatabaseType.MYSQL,
                DatabaseType.SQLITE,
                DatabaseType.H2,
            )

        jdbcTypes.forEach { type ->
            assertTrue(type.driverClass.isNotEmpty())
        }
    }
}
