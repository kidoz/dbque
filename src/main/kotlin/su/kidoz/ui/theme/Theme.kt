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
        primaryContainer = Color(0xFFE3E9FF),
        onPrimaryContainer = Color(0xFF0F1C4D),
        secondary = Secondary,
        onSecondary = OnSecondary,
        secondaryContainer = Color(0xFFD8F5EF),
        onSecondaryContainer = Color(0xFF003C34),
        tertiary = Color(0xFFF2B863),
        onTertiary = Color(0xFF3A2200),
        tertiaryContainer = Color(0xFFFFE1B3),
        onTertiaryContainer = Color(0xFF2D1B00),
        background = BackgroundLight,
        onBackground = OnBackgroundLight,
        surface = SurfaceLight,
        onSurface = OnSurfaceLight,
        surfaceVariant = Color(0xFFEDE9E3),
        onSurfaceVariant = Color(0xFF504B45),
        outline = BorderLight,
        error = Error,
        onError = Color.White,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = Primary,
        onPrimary = OnPrimary,
        primaryContainer = Color(0xFF213AA3),
        onPrimaryContainer = Color(0xFFDDE2FF),
        secondary = Secondary,
        onSecondary = OnSecondary,
        secondaryContainer = Color(0xFF0B5D4F),
        onSecondaryContainer = Color(0xFFBFF1E8),
        tertiary = Color(0xFFF5C074),
        onTertiary = Color(0xFF2E1A00),
        tertiaryContainer = Color(0xFF6F4A14),
        onTertiaryContainer = Color(0xFFFFE3B8),
        background = BackgroundDark,
        onBackground = OnBackgroundDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = Color(0xFF262626),
        onSurfaceVariant = Color(0xFFC9C5BF),
        outline = BorderDark,
        error = Error,
        onError = Color.White,
    )

data class ExtendedColors(
    val isDarkTheme: Boolean,
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
        isDarkTheme = false,
        editorBackground = Color(0xFFFCFBFA),
        editorLineNumber = Color(0xFF8A8F9A),
        editorSelection = Color(0xFFCAD6FF),
        editorCurrentLine = Color(0xFFF2EFEA),
        syntaxKeyword = Color(0xFF2F5AFF),
        syntaxString = Color(0xFFB85A0A),
        syntaxNumber = Color(0xFF0C8A55),
        syntaxComment = Color(0xFF7A859A),
        syntaxFunction = Color(0xFF9A6A00),
        syntaxType = Color(0xFF0E7C69),
        syntaxOperator = Color(0xFF1B1B1B),
        syntaxVariable = Color(0xFF0A3A8C),
        success = Success,
        warning = Warning,
        info = Info,
        treeFolder = Color(0xFFB86E16),
        treeTable = Color(0xFF0E7C69),
        treeColumn = Color(0xFF0A3A8C),
        treeIndex = Color(0xFF9A6A00),
        treeView = Color(0xFFB85A0A),
    )

val DarkExtendedColors =
    ExtendedColors(
        isDarkTheme = true,
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
            shapes = AppShapes,
            content = content,
        )
    }
}

object DBQueTheme {
    val extendedColors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}
