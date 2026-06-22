package su.kidoz.feature.diagram

import app.cash.turbine.test
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import su.kidoz.core.model.ConnectionConfig
import su.kidoz.core.model.DatabaseType
import su.kidoz.database.ActiveConnection
import su.kidoz.database.ConnectionManager
import su.kidoz.database.JdbcDatabaseConnection
import su.kidoz.database.driver.H2Driver
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DiagramViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var activeConnectionId: MutableStateFlow<String?>
    private lateinit var connectionManager: ConnectionManager
    private val dataSources = mutableListOf<HikariDataSource>()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        activeConnectionId = MutableStateFlow(null)
        connectionManager = mockk(relaxed = true)
        every { connectionManager.activeConnectionId } returns activeConnectionId
        every { connectionManager.activeConnection } returns null
    }

    @AfterEach
    fun tearDown() {
        dataSources.forEach { it.close() }
        Dispatchers.resetMain()
    }

    @Test
    fun requestApplyDdl_setsPendingDdlForValidDraft() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onEvent(DiagramEvent.AddTable)
            viewModel.onEvent(DiagramEvent.RequestApplyDdl)

            assertNotNull(viewModel.state.value.pendingApplyDdl)
            assertFalse(viewModel.state.value.isApplyingDdl)
        }

    @Test
    fun cancelApplyDdl_clearsPendingDdl() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onEvent(DiagramEvent.AddTable)
            viewModel.onEvent(DiagramEvent.RequestApplyDdl)
            viewModel.onEvent(DiagramEvent.CancelApplyDdl)

            assertNull(viewModel.state.value.pendingApplyDdl)
        }

    @Test
    fun confirmApplyDdl_withoutConnection_emitsErrorAndClearsPendingDdl() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onEvent(DiagramEvent.AddTable)
            viewModel.onEvent(DiagramEvent.RequestApplyDdl)

            viewModel.effect.test {
                viewModel.onEvent(DiagramEvent.ConfirmApplyDdl)

                val effect = awaitItem() as DiagramEffect.ShowError
                assertEquals("Connect to a database before applying DDL", effect.message)
                assertNull(viewModel.state.value.pendingApplyDdl)
            }
        }

    @Test
    fun confirmApplyDdl_appliesDdlAndRefreshesDiagram() =
        runTest {
            val activeConnection = createH2ActiveConnection("diagram_vm_apply_success")
            every { connectionManager.activeConnection } returns activeConnection
            activeConnectionId.value = activeConnection.config.id
            val viewModel = createViewModel()

            viewModel.onEvent(DiagramEvent.AddTable)
            viewModel.onEvent(DiagramEvent.RequestApplyDdl)

            viewModel.effect.test {
                viewModel.onEvent(DiagramEvent.ConfirmApplyDdl)
                advanceUntilIdle()

                val effect = awaitItem() as DiagramEffect.DdlApplied
                assertEquals(1, effect.statementCount)
                assertFalse(viewModel.state.value.isApplyingDdl)
                assertNull(viewModel.state.value.pendingApplyDdl)
                assertTableExists(activeConnection, "table_1")
            }
        }

    @Test
    fun confirmApplyDdl_whenExecutionFails_emitsErrorAndClearsApplyingState() =
        runTest {
            val activeConnection = createH2ActiveConnection("diagram_vm_apply_failure")
            every { connectionManager.activeConnection } returns activeConnection
            activeConnectionId.value = activeConnection.config.id
            val viewModel = createViewModel()

            viewModel.onEvent(DiagramEvent.AddTable)
            viewModel.onEvent(DiagramEvent.ChangeColumnType("draft.table_1:id", "NOT_A_TYPE @"))
            viewModel.onEvent(DiagramEvent.RequestApplyDdl)

            viewModel.effect.test {
                viewModel.onEvent(DiagramEvent.ConfirmApplyDdl)
                advanceUntilIdle()

                awaitItem() as DiagramEffect.ShowError
                assertFalse(viewModel.state.value.isApplyingDdl)
                assertNull(viewModel.state.value.pendingApplyDdl)
            }
        }

    private fun createViewModel(): DiagramViewModel = DiagramViewModel(connectionManager)

    private fun createH2ActiveConnection(name: String): ActiveConnection {
        val config =
            ConnectionConfig(
                id = name,
                name = name,
                type = DatabaseType.H2,
                path = "mem:$name;DB_CLOSE_DELAY=-1",
            )
        val dataSource =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = "jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1"
                    driverClassName = DatabaseType.H2.driverClass
                    maximumPoolSize = 2
                    minimumIdle = 1
                },
            )
        dataSources += dataSource
        return ActiveConnection(
            config = config,
            driver = H2Driver(),
            databaseConnection = JdbcDatabaseConnection(config, dataSource),
        )
    }

    private fun assertTableExists(
        activeConnection: ActiveConnection,
        tableName: String,
    ) {
        activeConnection.getConnection().use { connection ->
            connection.metaData.getTables(null, null, tableName, arrayOf("TABLE")).use { resultSet ->
                assertEquals(true, resultSet.next())
            }
        }
    }
}
