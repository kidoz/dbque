package su.kidoz.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColorScheme =
    lightColorScheme(
        primary = Primary,
        onPrimary = OnPrimary,
        primaryContainer = Primary.copy(alpha = 0.12f),
        onPrimaryContainer = Primary,
        secondary = Secondary,
        onSecondary = OnSecondary,
        secondaryContainer = Secondary.copy(alpha = 0.12f),
        onSecondaryContainer = Secondary,
        background = BackgroundLight,
        onBackground = OnBackgroundLight,
        surface = SurfaceLight,
        onSurface = OnSurfaceLight,
        surfaceVariant = Color(0xFFF5F5F5),
        onSurfaceVariant = Color(0xFF49454F),
        outline = BorderLight,
        error = Error,
        onError = Color.White,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = Primary,
        onPrimary = OnPrimary,
        primaryContainer = Primary.copy(alpha = 0.12f),
        onPrimaryContainer = Primary,
        secondary = Secondary,
        onSecondary = OnSecondary,
        secondaryContainer = Secondary.copy(alpha = 0.12f),
        onSecondaryContainer = Secondary,
        background = BackgroundDark,
        onBackground = OnBackgroundDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = Color(0xFF2D2D2D),
        onSurfaceVariant = Color(0xFFCAC4D0),
        outline = BorderDark,
        error = Error,
        onError = Color.White,
    )

data class ExtendedColors(
    val editorBackground: Color,
    val editorLineNumber: Color,
    val editorSelection: Color,
    val editorCurrentLine: Color,
    val syntaxKeyword: Color,
    val syntaxString: Color,
    val syntaxNumber: Color,
    val syntaxComment: Color,
    val syntaxFunction: Color,
    val syntaxType: Color,
    val syntaxOperator: Color,
    val syntaxVariable: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val treeFolder: Color,
    val treeTable: Color,
    val treeColumn: Color,
    val treeIndex: Color,
    val treeView: Color,
)

val LightExtendedColors =
    ExtendedColors(
        editorBackground = Color.White,
        editorLineNumber = Color(0xFF999999),
        editorSelection = Color(0xFFADD6FF),
        editorCurrentLine = Color(0xFFF5F5F5),
        syntaxKeyword = Color(0xFF0000FF),
        syntaxString = Color(0xFFA31515),
        syntaxNumber = Color(0xFF098658),
        syntaxComment = Color(0xFF008000),
        syntaxFunction = Color(0xFF795E26),
        syntaxType = Color(0xFF267F99),
        syntaxOperator = Color(0xFF000000),
        syntaxVariable = Color(0xFF001080),
        success = Success,
        warning = Warning,
        info = Info,
        treeFolder = Color(0xFFB8860B),
        treeTable = Color(0xFF267F99),
        treeColumn = Color(0xFF001080),
        treeIndex = Color(0xFF795E26),
        treeView = Color(0xFFA31515),
    )

val DarkExtendedColors =
    ExtendedColors(
        editorBackground = EditorBackground,
        editorLineNumber = EditorLineNumber,
        editorSelection = EditorSelection,
        editorCurrentLine = EditorCurrentLine,
        syntaxKeyword = SyntaxKeyword,
        syntaxString = SyntaxString,
        syntaxNumber = SyntaxNumber,
        syntaxComment = SyntaxComment,
        syntaxFunction = SyntaxFunction,
        syntaxType = SyntaxType,
        syntaxOperator = SyntaxOperator,
        syntaxVariable = SyntaxVariable,
        success = Success,
        warning = Warning,
        info = Info,
        treeFolder = TreeFolder,
        treeTable = TreeTable,
        treeColumn = TreeColumn,
        treeIndex = TreeIndex,
        treeView = TreeView,
    )

val LocalExtendedColors = staticCompositionLocalOf { DarkExtendedColors }

@Composable
fun DBQueTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}

object DBQueTheme {
    val extendedColors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}
