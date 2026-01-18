package su.kidoz.feature.editor

import app.cash.turbine.test
import io.mockk.coEvery
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
import su.kidoz.core.repository.QueryHistoryRepository
import su.kidoz.database.ConnectionManager
import su.kidoz.database.executor.QueryExecutor
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var connectionManager: ConnectionManager
    private lateinit var queryExecutor: QueryExecutor
    private lateinit var queryHistoryRepository: QueryHistoryRepository
    private lateinit var viewModel: EditorViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        connectionManager = mockk(relaxed = true)
        queryExecutor = mockk(relaxed = true)
        queryHistoryRepository = mockk(relaxed = true)

        coEvery { connectionManager.activeConnectionId } returns MutableStateFlow<String?>(null)
        coEvery { connectionManager.activeConnection } returns null
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): EditorViewModel = EditorViewModel(connectionManager, queryExecutor, queryHistoryRepository)

    @Test
    fun initialState_hasOneTab() =
        runTest {
            viewModel = createViewModel()

            assertEquals(1, viewModel.state.value.tabs.size)
            assertNotNull(viewModel.state.value.activeTab)
        }

    @Test
    fun initialState_notExecuting() =
        runTest {
            viewModel = createViewModel()

            assertFalse(viewModel.state.value.isExecuting)
        }

    @Test
    fun newTab_addsTab() =
        runTest {
            viewModel = createViewModel()

            viewModel.onEvent(EditorEvent.NewTab)

            assertEquals(2, viewModel.state.value.tabs.size)
        }

    @Test
    fun newTab_selectsNewTab() =
        runTest {
            viewModel = createViewModel()
            val initialTabId = viewModel.state.value.activeTabId

            viewModel.onEvent(EditorEvent.NewTab)

            assertNotEquals(initialTabId, viewModel.state.value.activeTabId)
        }

    @Test
    fun newTab_incrementsTitle() =
        runTest {
            viewModel = createViewModel()

            viewModel.onEvent(EditorEvent.NewTab)
            viewModel.onEvent(EditorEvent.NewTab)

            val titles =
                viewModel.state.value.tabs
                    .map { it.title }
            assertTrue(titles.contains("Query 2"))
            assertTrue(titles.contains("Query 3"))
        }

    @Test
    fun closeTab_removesTab() =
        runTest {
            viewModel = createViewModel()
            viewModel.onEvent(EditorEvent.NewTab)
            val tabToClose =
                viewModel.state.value.tabs[1]
                    .id

            viewModel.onEvent(EditorEvent.CloseTab(tabToClose))

            assertEquals(1, viewModel.state.value.tabs.size)
        }

    @Test
    fun closeTab_cannotCloseLastTab() =
        runTest {
            viewModel = createViewModel()
            val onlyTabId =
                viewModel.state.value.tabs[0]
                    .id

            viewModel.onEvent(EditorEvent.CloseTab(onlyTabId))

            assertEquals(1, viewModel.state.value.tabs.size)
        }

    @Test
    fun closeTab_selectsAdjacentTab() =
        runTest {
            viewModel = createViewModel()
            viewModel.onEvent(EditorEvent.NewTab)
            val firstTabId =
                viewModel.state.value.tabs[0]
                    .id
            val secondTabId =
                viewModel.state.value.tabs[1]
                    .id
            viewModel.onEvent(EditorEvent.SelectTab(secondTabId))

            viewModel.onEvent(EditorEvent.CloseTab(secondTabId))

            assertEquals(firstTabId, viewModel.state.value.activeTabId)
        }

    @Test
    fun selectTab_changesActiveTab() =
        runTest {
            viewModel = createViewModel()
            viewModel.onEvent(EditorEvent.NewTab)
            val firstTabId =
                viewModel.state.value.tabs[0]
                    .id

            viewModel.onEvent(EditorEvent.SelectTab(firstTabId))

            assertEquals(firstTabId, viewModel.state.value.activeTabId)
        }

    @Test
    fun renameTab_updatesTitle() =
        runTest {
            viewModel = createViewModel()
            val tabId = viewModel.state.value.activeTabId

            viewModel.onEvent(EditorEvent.RenameTab(tabId, "My Query"))

            assertEquals(
                "My Query",
                viewModel.state.value.activeTab
                    ?.title,
            )
        }

    @Test
    fun updateContent_updatesActiveTab() =
        runTest {
            viewModel = createViewModel()

            viewModel.onEvent(EditorEvent.UpdateContent("SELECT * FROM users"))

            assertEquals(
                "SELECT * FROM users",
                viewModel.state.value.activeTab
                    ?.content,
            )
        }

    @Test
    fun updateContent_marksTabModified() =
        runTest {
            viewModel = createViewModel()

            viewModel.onEvent(EditorEvent.UpdateContent("SELECT 1"))

            assertTrue(
                viewModel.state.value.activeTab
                    ?.isModified == true,
            )
        }

    @Test
    fun updateContent_addsToUndoStack() =
        runTest {
            viewModel = createViewModel()

            viewModel.onEvent(EditorEvent.UpdateContent("SELECT 1"))

            assertTrue(
                viewModel.state.value.activeTab
                    ?.canUndo == true,
            )
        }

    @Test
    fun updateCursor_updatesPosition() =
        runTest {
            viewModel = createViewModel()
            viewModel.onEvent(EditorEvent.UpdateContent("SELECT * FROM users"))

            viewModel.onEvent(EditorEvent.UpdateCursor(7))

            assertEquals(
                7,
                viewModel.state.value.activeTab
                    ?.cursorPosition,
            )
        }

    @Test
    fun updateSelection_updatesRange() =
        runTest {
            viewModel = createViewModel()
            viewModel.onEvent(EditorEvent.UpdateContent("SELECT * FROM users"))

            viewModel.onEvent(EditorEvent.UpdateSelection(0, 6))

            assertEquals(
                0,
                viewModel.state.value.activeTab
                    ?.selectionStart,
            )
            assertEquals(
                6,
                viewModel.state.value.activeTab
                    ?.selectionEnd,
            )
        }

    @Test
    fun undo_restoresPreviousContent() =
        runTest {
            viewModel = createViewModel()
            viewModel.onEvent(EditorEvent.UpdateContent("First"))
            viewModel.onEvent(EditorEvent.UpdateContent("Second"))

            viewModel.onEvent(EditorEvent.Undo)

            assertEquals(
                "First",
                viewModel.state.value.activeTab
                    ?.content,
            )
        }

    @Test
    fun undo_enablesRedo() =
        runTest {
            viewModel = createViewModel()
            viewModel.onEvent(EditorEvent.UpdateContent("Content"))
            viewModel.onEvent(EditorEvent.Undo)

            assertTrue(
                viewModel.state.value.activeTab
                    ?.canRedo == true,
            )
        }

    @Test
    fun redo_restoresUndoneContent() =
        runTest {
            viewModel = createViewModel()
            viewModel.onEvent(EditorEvent.UpdateContent("Content"))
            viewModel.onEvent(EditorEvent.Undo)
            viewModel.onEvent(EditorEvent.Redo)

            assertEquals(
                "Content",
                viewModel.state.value.activeTab
                    ?.content,
            )
        }

    @Test
    fun updateContent_clearsRedoStack() =
        runTest {
            viewModel = createViewModel()
            viewModel.onEvent(EditorEvent.UpdateContent("First"))
            viewModel.onEvent(EditorEvent.Undo)
            viewModel.onEvent(EditorEvent.UpdateContent("New"))

            assertFalse(
                viewModel.state.value.activeTab
                    ?.canRedo == true,
            )
        }

    @Test
    fun findReplace_replacesSingleOccurrence() =
        runTest {
            viewModel = createViewModel()
            viewModel.onEvent(EditorEvent.UpdateContent("foo bar foo"))

            viewModel.onEvent(EditorEvent.FindReplace("foo", "baz", replaceAll = false))

            assertEquals(
                "baz bar foo",
                viewModel.state.value.activeTab
                    ?.content,
            )
        }

    @Test
    fun findReplace_replacesAllOccurrences() =
        runTest {
            viewModel = createViewModel()
            viewModel.onEvent(EditorEvent.UpdateContent("foo bar foo"))

            viewModel.onEvent(EditorEvent.FindReplace("foo", "baz", replaceAll = true))

            assertEquals(
                "baz bar baz",
                viewModel.state.value.activeTab
                    ?.content,
            )
        }

    @Test
    fun format_formatsContent() =
        runTest {
            viewModel = createViewModel()
            viewModel.onEvent(EditorEvent.UpdateContent("SELECT * FROM users WHERE id = 1"))

            viewModel.onEvent(EditorEvent.Format)

            val content =
                viewModel.state.value.activeTab
                    ?.content ?: ""
            assertTrue(content.contains("\n"))
        }

    @Test
    fun executeQuery_withoutConnection_emitsError() =
        runTest {
            viewModel = createViewModel()
            viewModel.onEvent(EditorEvent.UpdateContent("SELECT 1"))

            viewModel.effect.test {
                viewModel.onEvent(EditorEvent.ExecuteQuery)
                val effect = awaitItem() as EditorEffect.QueryError
                assertEquals("No active connection", effect.message)
            }
        }

    @Test
    fun cancelExecution_stopsExecution() =
        runTest {
            viewModel = createViewModel()

            viewModel.onEvent(EditorEvent.CancelExecution)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isExecuting)
        }

    @Test
    fun cancelExecution_showsMessage() =
        runTest {
            viewModel = createViewModel()

            viewModel.effect.test {
                viewModel.onEvent(EditorEvent.CancelExecution)
                val effect = awaitItem() as EditorEffect.ShowMessage
                assertEquals("Query cancelled", effect.message)
            }
        }
}

class EditorStateTest {
    @Test
    fun activeTab_returnsCurrentTab() {
        val tab = EditorTab(id = "test-id")
        val state = EditorState(tabs = listOf(tab), activeTabId = "test-id")

        assertEquals(tab, state.activeTab)
    }

    @Test
    fun activeTab_returnsNullForInvalidId() {
        val state = EditorState(tabs = listOf(EditorTab()), activeTabId = "invalid")

        assertEquals(null, state.activeTab)
    }
}

class EditorTabTest {
    @Test
    fun selectedText_returnsEmptyForNoSelection() {
        val tab = EditorTab(content = "Hello World", selectionStart = 0, selectionEnd = 0)

        assertEquals("", tab.selectedText)
    }

    @Test
    fun selectedText_returnsSelectedRange() {
        val tab = EditorTab(content = "Hello World", selectionStart = 0, selectionEnd = 5)

        assertEquals("Hello", tab.selectedText)
    }

    @Test
    fun selectedText_handlesReversedSelection() {
        val tab = EditorTab(content = "Hello World", selectionStart = 5, selectionEnd = 0)

        assertEquals("Hello", tab.selectedText)
    }

    @Test
    fun queryToExecute_returnsContentWhenNoSelection() {
        val tab = EditorTab(content = "SELECT 1", selectionStart = 0, selectionEnd = 0)

        assertEquals("SELECT 1", tab.queryToExecute)
    }

    @Test
    fun queryToExecute_returnsSelectionWhenSelected() {
        val tab = EditorTab(content = "SELECT 1; SELECT 2", selectionStart = 0, selectionEnd = 8)

        assertEquals("SELECT 1", tab.queryToExecute)
    }

    @Test
    fun canUndo_falseForEmptyStack() {
        val tab = EditorTab(undoStack = emptyList())

        assertFalse(tab.canUndo)
    }

    @Test
    fun canUndo_trueForNonEmptyStack() {
        val tab = EditorTab(undoStack = listOf(EditorSnapshot("test", 0)))

        assertTrue(tab.canUndo)
    }

    @Test
    fun canRedo_falseForEmptyStack() {
        val tab = EditorTab(redoStack = emptyList())

        assertFalse(tab.canRedo)
    }

    @Test
    fun canRedo_trueForNonEmptyStack() {
        val tab = EditorTab(redoStack = listOf(EditorSnapshot("test", 0)))

        assertTrue(tab.canRedo)
    }
}

class EditorSnapshotTest {
    @Test
    fun snapshot_storesContentAndPosition() {
        val snapshot = EditorSnapshot(content = "SELECT 1", cursorPosition = 8)

        assertEquals("SELECT 1", snapshot.content)
        assertEquals(8, snapshot.cursorPosition)
    }
}
