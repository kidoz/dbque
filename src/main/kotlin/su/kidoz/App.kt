package su.kidoz

import androidx.compose.runtime.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration
import su.kidoz.di.appModule
import su.kidoz.ui.MainWindow
import su.kidoz.ui.theme.DBQueTheme

private val logger = KotlinLogging.logger {}

fun main() =
    application {
        logger.info { "Starting DBQue..." }

        val windowState =
            rememberWindowState(
                size = DpSize(1400.dp, 900.dp),
            )

        KoinApplication(
            koinConfiguration {
                modules(appModule)
            },
        ) {
            var darkTheme by remember { mutableStateOf(true) }

            Window(
                onCloseRequest = ::exitApplication,
                title = "DBQue - Database Management Tool",
                state = windowState,
            ) {
                DBQueTheme(darkTheme = darkTheme) {
                    MainWindow()
                }
            }
        }
    }
