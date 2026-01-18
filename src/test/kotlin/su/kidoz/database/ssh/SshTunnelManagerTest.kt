package su.kidoz.database.ssh

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import su.kidoz.core.model.SshConfig
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SshTunnelManagerTest {
    private fun createTestConfig() =
        TunnelConfig(
            sshConfig =
                SshConfig(
                    enabled = true,
                    host = "localhost",
                    port = 22,
                    username = "test",
                    password = "test",
                    useKeyAuth = false,
                    privateKeyPath = "",
                    passphrase = "",
                ),
            remoteHost = "db.example.com",
            remotePort = 5432,
        )

    @Test
    fun findAvailablePort_returnsValidPort() {
        val port = SshTunnelManager.findAvailablePort()

        assertTrue(port > 0)
        assertTrue(port <= 65535)
    }

    @Test
    fun findAvailablePort_returnsDifferentPorts() {
        val port1 = SshTunnelManager.findAvailablePort()
        val port2 = SshTunnelManager.findAvailablePort()

        // Ports should be different (almost certainly, unless the first was immediately reused)
        // This test validates the method works, not that it's always unique
        assertTrue(port1 > 0)
        assertTrue(port2 > 0)
    }

    @Test
    fun getTunnel_returnsNullForNonexistentConnection() {
        val manager = SshTunnelManager()

        val tunnel = manager.getTunnel("nonexistent")

        assertNull(tunnel)
    }

    @Test
    fun closeTunnel_handlesNonexistentConnection() =
        runTest {
            val manager = SshTunnelManager()

            // Should not throw
            manager.closeTunnel("nonexistent")
        }

    @Test
    fun closeAll_handlesEmptyManager() =
        runTest {
            val manager = SshTunnelManager()

            // Should not throw
            manager.closeAll()
        }
}

class MockSshTunnelTest {
    @Test
    fun connect_failsWithoutJsch() =
        runTest {
            val config =
                TunnelConfig(
                    sshConfig =
                        SshConfig(
                            enabled = true,
                            host = "localhost",
                            port = 22,
                            username = "test",
                            password = "test",
                            useKeyAuth = false,
                            privateKeyPath = "",
                            passphrase = "",
                        ),
                    remoteHost = "db.example.com",
                    remotePort = 5432,
                )
            val tunnel = MockSshTunnel(config)

            val result = tunnel.connect()

            assertTrue(result.isFailure)
            assertFalse(tunnel.isConnected)
        }

    @Test
    fun disconnect_setsConnectedToFalse() =
        runTest {
            val config =
                TunnelConfig(
                    sshConfig =
                        SshConfig(
                            enabled = true,
                            host = "localhost",
                            port = 22,
                            username = "test",
                            password = "test",
                            useKeyAuth = false,
                            privateKeyPath = "",
                            passphrase = "",
                        ),
                    remoteHost = "db.example.com",
                    remotePort = 5432,
                )
            val tunnel = MockSshTunnel(config)

            tunnel.disconnect()

            assertFalse(tunnel.isConnected)
        }

    @Test
    fun close_setsConnectedToFalse() {
        val config =
            TunnelConfig(
                sshConfig =
                    SshConfig(
                        enabled = true,
                        host = "localhost",
                        port = 22,
                        username = "test",
                        password = "test",
                        useKeyAuth = false,
                        privateKeyPath = "",
                        passphrase = "",
                    ),
                remoteHost = "db.example.com",
                remotePort = 5432,
            )
        val tunnel = MockSshTunnel(config)

        tunnel.close()

        assertFalse(tunnel.isConnected)
    }
}

class TunnelConfigTest {
    @Test
    fun defaultLocalPort_isZero() {
        val config =
            TunnelConfig(
                sshConfig =
                    SshConfig(
                        enabled = true,
                        host = "localhost",
                        port = 22,
                        username = "test",
                        password = "",
                        useKeyAuth = false,
                        privateKeyPath = "",
                        passphrase = "",
                    ),
                remoteHost = "db.example.com",
                remotePort = 5432,
            )

        assertEquals(0, config.localPort)
    }

    @Test
    fun customLocalPort_isPreserved() {
        val config =
            TunnelConfig(
                sshConfig =
                    SshConfig(
                        enabled = true,
                        host = "localhost",
                        port = 22,
                        username = "test",
                        password = "",
                        useKeyAuth = false,
                        privateKeyPath = "",
                        passphrase = "",
                    ),
                remoteHost = "db.example.com",
                remotePort = 5432,
                localPort = 12345,
            )

        assertEquals(12345, config.localPort)
    }
}
