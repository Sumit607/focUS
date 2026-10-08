package app.focusus.launcher.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import app.focusus.launcher.R
import app.focusus.launcher.core.Appearance
import app.focusus.launcher.core.ThemeMode

/** Design tokens from the focUS UI spec: true black, white and grey words, thin outlines, no accent colour. */
@Immutable
data class FocusColors(
    val bg: Color,
    val surface: Color,
    val outline: Color,
    val text: Color,
    val textDim: Color,
    val danger: Color,
    val isDark: Boolean,
)

val DarkColors = FocusColors(
    bg = Color(0xFF000000),
    surface = Color(0xFF1A1A1A),
    outline = Color(0xFF8A8A8A),
    text = Color(0xFFF2F2F2),
    textDim = Color(0xFF8A8A8A),
    danger = Color(0xFFE5484D),
    isDark = true,
)

val LightColors = FocusColors(
    bg = Color(0xFFFAFAFA),
    surface = Color(0xFFEDEDED),
    outline = Color(0xFF7A7A7A),
    text = Color(0xFF111111),
    textDim = Color(0xFF6B6B6B),
    danger = Color(0xFFC62828),
    isDark = false,
)

val Atkinson = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_bold, FontWeight.Bold),
)

val LocalFocusColors = staticCompositionLocalOf { DarkColors }
val LocalTextScale = staticCompositionLocalOf { 1f }

object Focus {
    val colors: FocusColors
        @Composable get() = LocalFocusColors.current
    val scale: Float
        @Composable get() = LocalTextScale.current
}

private fun scheme(c: FocusColors): ColorScheme {
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.text,
        onPrimary = c.bg,
        primaryContainer = c.surface,
        onPrimaryContainer = c.text,
        secondary = c.textDim,
        onSecondary = c.bg,
        secondaryContainer = c.surface,
        onSecondaryContainer = c.text,
        tertiary = c.text,
        onTertiary = c.bg,
        background = c.bg,
        onBackground = c.text,
        surface = c.bg,
        onSurface = c.text,
        surfaceVariant = c.surface,
        onSurfaceVariant = c.textDim,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface,
        surfaceContainerHighest = c.surface,
        surfaceContainerLow = c.bg,
        surfaceContainerLowest = c.bg,
        outline = c.outline,
        outlineVariant = c.outline.copy(alpha = 0.4f),
        error = c.danger,
        onError = c.bg,
    )
}

private fun typography(): Typography {
    val t = Typography()
    fun TextStyle.f() = copy(fontFamily = Atkinson)
    return Typography(
        displayLarge = t.displayLarge.f(), displayMedium = t.displayMedium.f(), displaySmall = t.displaySmall.f(),
        headlineLarge = t.headlineLarge.f(), headlineMedium = t.headlineMedium.f(), headlineSmall = t.headlineSmall.f(),
        titleLarge = t.titleLarge.f(), titleMedium = t.titleMedium.f(), titleSmall = t.titleSmall.f(),
        bodyLarge = t.bodyLarge.f(), bodyMedium = t.bodyMedium.f(), bodySmall = t.bodySmall.f(),
        labelLarge = t.labelLarge.f(), labelMedium = t.labelMedium.f(), labelSmall = t.labelSmall.f(),
    )
}

private val FocusTypography = typography()

@Composable
fun FocusTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val dark = when (appearance.theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(
        LocalFocusColors provides colors,
        LocalTextScale provides appearance.textScale.coerceIn(0.8f, 1.5f),
    ) {
        MaterialTheme(colorScheme = scheme(colors), typography = FocusTypography, content = content)
    }
}
