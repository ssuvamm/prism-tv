package io.github.prismtv.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import io.github.prismtv.data.AppSettings
import io.github.prismtv.data.DarkMode
import io.github.prismtv.data.ThemeStyle

/** Style knobs that go beyond colours: how translucent surfaces are, corner radii, backdrop, nav style. */
@Immutable
data class StyleSpec(
    val style: ThemeStyle,
    val dark: Boolean,
    val corner: Dp,
    val translucent: Boolean,
    val surfaceAlpha: Float,
    val borderAlpha: Float,
    val backdrop: List<Color>,
    val blobs: List<Color>,
    val floatingNav: Boolean,
)

val LocalStyle = staticCompositionLocalOf {
    StyleSpec(ThemeStyle.MATERIAL_YOU, true, 16.dp, false, 1f, 0f, listOf(Color.Black), emptyList(), false)
}

val AccentPalette = listOf(
    0xFF7C5CFF, 0xFF0A84FF, 0xFF00B8A9, 0xFF34C759, 0xFFFFB020, 0xFFFF6B3D, 0xFFFF3B7F, 0xFFAF52DE,
)

@Composable
fun PrismTheme(settings: AppSettings, forceDark: Boolean = false, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val style = settings.style
    val dark = forceDark || style == ThemeStyle.AMOLED || style == ThemeStyle.NEON || when (settings.darkMode) {
        DarkMode.SYSTEM -> systemDark
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
    val context = LocalContext.current
    val accent = settings.accent?.let { Color(it) }

    val scheme = remember(style, dark, accent) {
        val base = when (style) {
            ThemeStyle.MATERIAL_YOU ->
                if (accent == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } else scheme(dark, accent ?: Color(0xFF6750A4), Color(0xFF625B71),
                    if (dark) Color(0xFF141218) else Color(0xFFFEF7FF), if (dark) Color(0xFF1D1B20) else Color(0xFFFFFFFF))
            ThemeStyle.GLASS -> scheme(dark, accent ?: if (dark) Color(0xFFA990FF) else Color(0xFF5B3FFF), Color(0xFF5AD1FF),
                if (dark) Color(0xFF0D0A22) else Color(0xFFEFEFFF), if (dark) Color(0xFF1B1740) else Color(0xFFFFFFFF))
            ThemeStyle.LIQUID -> scheme(dark, accent ?: if (dark) Color(0xFF0A84FF) else Color(0xFF007AFF), Color(0xFFFF375F),
                if (dark) Color(0xFF000000) else Color(0xFFF2F2F7), if (dark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF))
            ThemeStyle.AMOLED -> scheme(true, accent ?: Color(0xFF4FC3F7), Color(0xFFB39DDB), Color(0xFF000000), Color(0xFF0B0B0B))
            ThemeStyle.NEON -> scheme(true, accent ?: Color(0xFFFF2BD6), Color(0xFF00E5FF), Color(0xFF07060F), Color(0xFF14102A))
            ThemeStyle.SUNSET -> scheme(dark, accent ?: if (dark) Color(0xFFFF8A5B) else Color(0xFFE2522A), Color(0xFFFF4F8B),
                if (dark) Color(0xFF1A0F14) else Color(0xFFFFF4EE), if (dark) Color(0xFF2A1A21) else Color(0xFFFFFFFF))
        }
        base
    }

    val spec = remember(style, dark, scheme) { specFor(style, dark, scheme) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalStyle provides spec) {
        MaterialTheme(colorScheme = scheme, typography = typographyFor(style)) {
            // Text and icons drawn straight on the backdrop (outside a Surface) need the theme's content colour.
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}

private fun specFor(style: ThemeStyle, dark: Boolean, s: ColorScheme): StyleSpec = when (style) {
    ThemeStyle.MATERIAL_YOU -> StyleSpec(style, dark, 16.dp, false, 1f, 0f, listOf(s.background, s.background), emptyList(), false)
    ThemeStyle.AMOLED -> StyleSpec(style, true, 14.dp, false, 1f, 0.06f, listOf(Color.Black, Color.Black), emptyList(), false)
    ThemeStyle.GLASS -> StyleSpec(
        style, dark, 22.dp, true, if (dark) 0.42f else 0.55f, if (dark) 0.22f else 0.7f,
        if (dark) listOf(Color(0xFF130E35), Color(0xFF0A0820), Color(0xFF071526)) else listOf(Color(0xFFE9E4FF), Color(0xFFF4F1FF), Color(0xFFE2F4FF)),
        if (dark) listOf(Color(0xFF7C5CFF), Color(0xFF00B4FF), Color(0xFFFF4FA3)) else listOf(Color(0xFFB9A8FF), Color(0xFF8FDCFF), Color(0xFFFFB8DA)),
        true,
    )
    ThemeStyle.LIQUID -> StyleSpec(
        style, dark, 26.dp, true, if (dark) 0.55f else 0.62f, if (dark) 0.18f else 0.85f,
        if (dark) listOf(Color(0xFF000000), Color(0xFF05070D)) else listOf(Color(0xFFF2F2F7), Color(0xFFF7F7FB)),
        if (dark) listOf(Color(0xFF0A84FF), Color(0xFF5E5CE6), Color(0xFFFF375F)) else listOf(Color(0xFF9CCBFF), Color(0xFFC9C7FF), Color(0xFFFFC2CF)),
        true,
    )
    ThemeStyle.NEON -> StyleSpec(
        style, true, 18.dp, true, 0.5f, 0.28f,
        listOf(Color(0xFF07060F), Color(0xFF0B0820)),
        listOf(Color(0xFFFF2BD6), Color(0xFF00E5FF), Color(0xFF7C4DFF)),
        true,
    )
    ThemeStyle.SUNSET -> StyleSpec(
        style, dark, 20.dp, true, if (dark) 0.5f else 0.6f, if (dark) 0.16f else 0.7f,
        if (dark) listOf(Color(0xFF1E0F17), Color(0xFF130A10)) else listOf(Color(0xFFFFEEE4), Color(0xFFFFF7F1)),
        if (dark) listOf(Color(0xFFFF6B3D), Color(0xFFFF3B7F), Color(0xFFFFB020)) else listOf(Color(0xFFFFC2A8), Color(0xFFFFB3CC), Color(0xFFFFE1A1)),
        false,
    )
}

private fun scheme(dark: Boolean, primary: Color, secondary: Color, background: Color, surface: Color): ColorScheme {
    val onSurface = if (dark) Color(0xFFECEAF4) else Color(0xFF1B1B21)
    val onPrimary = if (primary.luminance() > 0.5f) Color(0xFF111111) else Color.White
    val containerMix = if (dark) 0.28f else 0.16f
    val primaryContainer = lerp(surface, primary, containerMix + 0.1f)
    fun layer(f: Float) = lerp(surface, onSurface, f)
    return if (dark) darkColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primaryContainer, onPrimaryContainer = onSurface,
        secondary = secondary, onSecondary = Color.Black,
        secondaryContainer = lerp(surface, secondary, 0.25f), onSecondaryContainer = onSurface,
        tertiary = secondary, background = background, onBackground = onSurface,
        surface = surface, onSurface = onSurface,
        surfaceVariant = layer(0.08f), onSurfaceVariant = onSurface.copy(alpha = 0.72f),
        surfaceContainerLowest = lerp(background, surface, 0.5f), surfaceContainerLow = surface,
        surfaceContainer = layer(0.04f), surfaceContainerHigh = layer(0.08f), surfaceContainerHighest = layer(0.12f),
        outline = onSurface.copy(alpha = 0.32f), outlineVariant = onSurface.copy(alpha = 0.14f),
        inverseSurface = onSurface, inverseOnSurface = surface,
    ) else lightColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primaryContainer, onPrimaryContainer = onSurface,
        secondary = secondary, onSecondary = Color.White,
        secondaryContainer = lerp(surface, secondary, 0.2f), onSecondaryContainer = onSurface,
        tertiary = secondary, background = background, onBackground = onSurface,
        surface = surface, onSurface = onSurface,
        surfaceVariant = lerp(background, onSurface, 0.05f), onSurfaceVariant = onSurface.copy(alpha = 0.66f),
        surfaceContainerLowest = Color.White, surfaceContainerLow = lerp(background, surface, 0.6f),
        surfaceContainer = lerp(background, onSurface, 0.03f), surfaceContainerHigh = lerp(background, onSurface, 0.06f),
        surfaceContainerHighest = lerp(background, onSurface, 0.09f),
        outline = onSurface.copy(alpha = 0.3f), outlineVariant = onSurface.copy(alpha = 0.12f),
        inverseSurface = onSurface, inverseOnSurface = surface,
    )
}

private fun typographyFor(style: ThemeStyle): Typography {
    val base = Typography()
    if (style != ThemeStyle.LIQUID) {
        return base.copy(
            headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
            headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
    // Apple-ish: heavier large titles, tighter tracking, slightly larger body.
    fun TextStyle.tight(size: Int, weight: FontWeight) = copy(fontSize = size.sp, fontWeight = weight, letterSpacing = (-0.4).sp)
    return base.copy(
        headlineLarge = base.headlineLarge.tight(34, FontWeight.Bold),
        headlineMedium = base.headlineMedium.tight(28, FontWeight.Bold),
        titleLarge = base.titleLarge.tight(22, FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, letterSpacing = (-0.2).sp),
    )
}
