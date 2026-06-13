package su.kidoz.di

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import su.kidoz.core.repository.ConnectionRepository
import su.kidoz.core.repository.QueryHistoryRepository
import su.kidoz.core.repository.SettingsRepository
import su.kidoz.database.ConnectionManager
import su.kidoz.database.executor.QueryExecutor
import su.kidoz.database.ssh.SshTunnelManager
import su.kidoz.feature.connection.ConnectionViewModel
import su.kidoz.feature.editor.EditorViewModel
import su.kidoz.feature.editor.autocomplete.AutocompleteProvider
import su.kidoz.feature.explorer.ExplorerViewModel
import su.kidoz.feature.history.HistoryViewModel
import su.kidoz.feature.queryplan.QueryPlanViewModel
import su.kidoz.feature.results.DataEditor
import su.kidoz.feature.results.ResultsViewModel
import su.kidoz.feature.savedqueries.SavedQueryRepository
import su.kidoz.feature.savedqueries.SavedQueryViewModel
import su.kidoz.feature.settings.SettingsViewModel
import su.kidoz.storage.AppDatabase
import java.io.File

val appModule =
    module {
        // Database
        single {
            val dbPath = getAppDataPath()
            val driver = JdbcSqliteDriver("jdbc:sqlite:$dbPath")
            AppDatabase.Schema.create(driver)
            AppDatabase(driver)
        }

        // Repositories
        singleOf(::ConnectionRepository)
        singleOf(::QueryHistoryRepository)
        singleOf(::SettingsRepository)
        singleOf(::SavedQueryRepository)

        // Database services
        singleOf(::SshTunnelManager)
        single { ConnectionManager(get()) }
        singleOf(::QueryExecutor)
        singleOf(::DataEditor)

        // Autocomplete
        single { AutocompleteProvider(get()) }

        // ViewModels
        single { ConnectionViewModel(get(), get()) }
        single { ExplorerViewModel(get()) }
        single { EditorViewModel(get(), get(), get(), get()) }
        single { ResultsViewModel(get()) }
        single { HistoryViewModel(get(), get()) }
        single { QueryPlanViewModel(get()) }
        single { SavedQueryViewModel(get(), get()) }
        single { SettingsViewModel(get()) }
    }

private fun getAppDataPath(): String {
    val userHome = System.getProperty("user.home")
    val appDataDir =
        when {
            System.getProperty("os.name").lowercase().contains("mac") -> {
                File(userHome, "Library/Application Support/DBQue")
            }

            System.getProperty("os.name").lowercase().contains("win") -> {
                File(System.getenv("APPDATA") ?: userHome, "DBQue")
            }

            else -> {
                File(userHome, ".dbque")
            }
        }

    if (!appDataDir.exists()) {
        appDataDir.mkdirs()
    }

    return File(appDataDir, "dbque.db").absolutePath
}
