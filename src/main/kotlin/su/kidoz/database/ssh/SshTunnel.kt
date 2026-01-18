package su.kidoz.database.ssh

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.kidoz.core.model.SshConfig
import java.io.Closeable
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap

data class TunnelConfig(
    val sshConfig: SshConfig,
    val remoteHost: String,
    val remotePort: Int,
    val localPort: Int = 0, // 0 means auto-assign
)

interface SshTunnel : Closeable {
    val localPort: Int
    val isConnected: Boolean

    suspend fun connect(): Result<Int>

    suspend fun disconnect()
}

class SshTunnelManager {
    private val logger = KotlinLogging.logger {}
    private val tunnels = ConcurrentHashMap<String, SshTunnel>()

    suspend fun createTunnel(
        connectionId: String,
        config: TunnelConfig,
    ): Result<SshTunnel> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Close existing tunnel if any
                tunnels[connectionId]?.close()

                val tunnel = JschSshTunnel(config)
                tunnel.connect().getOrThrow()
                tunnels[connectionId] = tunnel

                logger.info { "SSH tunnel created for $connectionId on local port ${tunnel.localPort}" }
                tunnel
            }
        }

    fun getTunnel(connectionId: String): SshTunnel? = tunnels[connectionId]

    suspend fun closeTunnel(connectionId: String) {
        tunnels.remove(connectionId)?.let { tunnel ->
            withContext(Dispatchers.IO) {
                tunnel.close()
            }
            logger.info { "SSH tunnel closed for $connectionId" }
        }
    }

    suspend fun closeAll() {
        tunnels.keys.toList().forEach { closeTunnel(it) }
    }

    companion object {
        fun findAvailablePort(): Int = ServerSocket(0).use { it.localPort }
    }
}

/**
 * SSH Tunnel implementation using JSch library.
 * Note: Requires adding JSch dependency to build.gradle.kts:
 * implementation("com.jcraft:jsch:0.1.55")
 */
class JschSshTunnel(
    private val config: TunnelConfig,
) : SshTunnel {
    private val logger = KotlinLogging.logger {}

    private var session: Any? = null // JSch Session
    private var _localPort: Int = config.localPort
    private var _isConnected: Boolean = false

    override val localPort: Int
        get() = _localPort

    override val isConnected: Boolean
        get() = _isConnected

    override suspend fun connect(): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Dynamically load JSch to avoid hard dependency
                val jschClass = Class.forName("com.jcraft.jsch.JSch")
                val jsch = jschClass.getDeclaredConstructor().newInstance()

                // Configure authentication
                if (config.sshConfig.useKeyAuth && config.sshConfig.privateKeyPath.isNotEmpty()) {
                    val addIdentityMethod =
                        jschClass.getMethod(
                            "addIdentity",
                            String::class.java,
                            String::class.java,
                        )
                    addIdentityMethod.invoke(
                        jsch,
                        config.sshConfig.privateKeyPath,
                        config.sshConfig.passphrase.ifEmpty { null },
                    )
                }

                // Create session
                val getSessionMethod =
                    jschClass.getMethod(
                        "getSession",
                        String::class.java,
                        String::class.java,
                        Int::class.javaPrimitiveType,
                    )
                val session =
                    getSessionMethod.invoke(
                        jsch,
                        config.sshConfig.username,
                        config.sshConfig.host,
                        config.sshConfig.port,
                    )

                val sessionClass = Class.forName("com.jcraft.jsch.Session")

                // Set password if not using key auth
                if (!config.sshConfig.useKeyAuth) {
                    val setPasswordMethod = sessionClass.getMethod("setPassword", String::class.java)
                    setPasswordMethod.invoke(session, config.sshConfig.password)
                }

                // Disable strict host key checking (for simplicity)
                val setConfigMethod = sessionClass.getMethod("setConfig", String::class.java, String::class.java)
                setConfigMethod.invoke(session, "StrictHostKeyChecking", "no")

                // Connect
                val connectMethod = sessionClass.getMethod("connect", Int::class.javaPrimitiveType)
                connectMethod.invoke(session, 30000) // 30 second timeout

                // Set up port forwarding
                val assignedPort =
                    if (_localPort == 0) {
                        SshTunnelManager.findAvailablePort()
                    } else {
                        _localPort
                    }

                val setPortForwardingLMethod =
                    sessionClass.getMethod(
                        "setPortForwardingL",
                        Int::class.javaPrimitiveType,
                        String::class.java,
                        Int::class.javaPrimitiveType,
                    )
                setPortForwardingLMethod.invoke(
                    session,
                    assignedPort,
                    config.remoteHost,
                    config.remotePort,
                )

                this@JschSshTunnel.session = session
                _localPort = assignedPort
                _isConnected = true

                logger.info {
                    "SSH tunnel established: localhost:$_localPort -> " +
                        "${config.sshConfig.host}:${config.sshConfig.port} -> " +
                        "${config.remoteHost}:${config.remotePort}"
                }

                _localPort
            }
        }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                session?.let { session ->
                    val sessionClass = Class.forName("com.jcraft.jsch.Session")
                    val disconnectMethod = sessionClass.getMethod("disconnect")
                    disconnectMethod.invoke(session)
                }
            } catch (e: Exception) {
                logger.error(e) { "Error disconnecting SSH tunnel" }
            } finally {
                session = null
                _isConnected = false
            }
        }
    }

    override fun close() {
        kotlinx.coroutines.runBlocking { disconnect() }
    }
}

/**
 * Mock SSH tunnel for when JSch is not available
 */
class MockSshTunnel(
    config: TunnelConfig,
) : SshTunnel {
    private val logger = KotlinLogging.logger {}
    private var _localPort = config.localPort
    private var _isConnected = false

    override val localPort: Int get() = _localPort
    override val isConnected: Boolean get() = _isConnected

    override suspend fun connect(): Result<Int> {
        logger.warn { "SSH tunneling requires JSch library. Add 'com.jcraft:jsch:0.1.55' to dependencies." }
        return Result.failure(Exception("JSch library not found. SSH tunneling is not available."))
    }

    override suspend fun disconnect() {
        _isConnected = false
    }

    override fun close() {
        _isConnected = false
    }
}
