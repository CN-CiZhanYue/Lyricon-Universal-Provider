package io.github.cn_cizhanyue.lyricon.universal.provider.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class AccentPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryLight: Color,
    val secondary: Color,
    val glowA: Color,
    val glowB: Color,
    val glowC: Color,
)

data class PaletteSpec(
    val primaryDark: Color,
    val onPrimaryDark: Color,
    val primaryLight: Color,
    val onPrimaryLight: Color,
    val secondary: Color,
    val tertiary: Color,
    val glowA: Color,
    val glowB: Color,
    val glowC: Color,
    val darkBase: Color,
    val lightBase: Color,
)

data class AppStyle(
    val uiTheme: UiTheme,
    val panelRadius: Dp,
    val controlRadius: Dp,
    val titleFont: FontFamily,
    val bodyFont: FontFamily,
    val labelFont: FontFamily,
)

val LocalAccentPalette = staticCompositionLocalOf {
    AccentPalette(Color(0xFF7CFFCB), Color(0xFF062019), Color(0xFF0F766E), Color(0xFFB99CFF), Color(0xFF7CFFCB), Color(0xFF8B7CFF), Color(0xFF2BD9C4))
}
val LocalPaletteSpec = staticCompositionLocalOf { specOf(Palette.AURORA) }
val LocalUiTheme = staticCompositionLocalOf { UiTheme.GLASS }
val LocalDarkTheme = staticCompositionLocalOf { true }
val LocalGlassEnabled = staticCompositionLocalOf { true }
val LocalAppStyle = staticCompositionLocalOf {
    AppStyle(UiTheme.GLASS, 28.dp, 16.dp, FontFamily.SansSerif, FontFamily.SansSerif, FontFamily.SansSerif)
}

fun specOf(palette: Palette): PaletteSpec = when (palette) {
    Palette.AURORA -> PaletteSpec(
        Color(0xFF7CFFCB), Color(0xFF062019), Color(0xFF0F766E), Color.White,
        Color(0xFFB99CFF), Color(0xFFFFB86B), Color(0xFF7CFFCB), Color(0xFF8B7CFF), Color(0xFF2BD9C4),
        Color(0xFF07090E), Color(0xFFF4F6FA),
    )
    Palette.EMBER -> PaletteSpec(
        Color(0xFFFFB27A), Color(0xFF2B1406), Color(0xFFB45309), Color.White,
        Color(0xFFFF7A9C), Color(0xFFFFD166), Color(0xFFFFB27A), Color(0xFFFF6B8B), Color(0xFFFFD166),
        Color(0xFF0E0805), Color(0xFFFBF5EF),
    )
    Palette.TIDE -> PaletteSpec(
        Color(0xFF7AD7FF), Color(0xFF041E2B), Color(0xFF0369A1), Color.White,
        Color(0xFF8EA8FF), Color(0xFF7CFFD9), Color(0xFF7AD7FF), Color(0xFF5C8CFF), Color(0xFF7CFFD9),
        Color(0xFF05090F), Color(0xFFF2F7FB),
    )
    Palette.ORCHID -> PaletteSpec(
        Color(0xFFE0A7FF), Color(0xFF2A0B33), Color(0xFF7E22CE), Color.White,
        Color(0xFFFFA7D1), Color(0xFF9CFFE0), Color(0xFFE0A7FF), Color(0xFFFF8FD8), Color(0xFF9CFFE0),
        Color(0xFF0C0610), Color(0xFFFAF4FC),
    )
    Palette.GLACIER -> PaletteSpec(
        Color(0xFFC7F2FF), Color(0xFF08222B), Color(0xFF0E7490), Color.White,
        Color(0xFF9DB8FF), Color(0xFFC9FFE8), Color(0xFFC7F2FF), Color(0xFF7FA8FF), Color(0xFFC9FFE8),
        Color(0xFF060D12), Color(0xFFF3FAFC),
    )
    Palette.INK -> PaletteSpec(
        Color(0xFFE8E1D5), Color(0xFF15130F), Color(0xFF1C1917), Color.White,
        Color(0xFF8A7F6A), Color(0xFFB45309), Color(0xFFD6C7A8), Color(0xFF8A7F6A), Color(0xFFC98B4A),
        Color(0xFF12100C), Color(0xFFF5F1E8),
    )
    Palette.SEPIA -> PaletteSpec(
        Color(0xFFD9B38C), Color(0xFF2A1A0E), Color(0xFF8B5E34), Color.White,
        Color(0xFFB08968), Color(0xFF8F6A3D), Color(0xFFD9B38C), Color(0xFFB08968), Color(0xFFC49A6C),
        Color(0xFF15100B), Color(0xFFF7F0E6),
    )
    Palette.SAGE -> PaletteSpec(
        Color(0xFFB9D3AE), Color(0xFF142014), Color(0xFF5B7052), Color.White,
        Color(0xFF8FAF8B), Color(0xFFC2A878), Color(0xFFB9D3AE), Color(0xFF8FAF8B), Color(0xFFD6C7A8),
        Color(0xFF0C110C), Color(0xFFF2F5EE),
    )
    Palette.COBALT -> PaletteSpec(
        Color(0xFF93B4FF), Color(0xFF0B1633), Color(0xFF1D4ED8), Color.White,
        Color(0xFF7DD3FC), Color(0xFFF0ABFC), Color(0xFF93B4FF), Color(0xFF7DD3FC), Color(0xFFF0ABFC),
        Color(0xFF070B16), Color(0xFFF2F5FC),
    )
    Palette.RUST -> PaletteSpec(
        Color(0xFFFFB27A), Color(0xFF2A1208), Color(0xFFB45309), Color.White,
        Color(0xFFD97706), Color(0xFFFBBF24), Color(0xFFFFB27A), Color(0xFFD97706), Color(0xFFFBBF24),
        Color(0xFF120B06), Color(0xFFFBF3EA),
    )
    Palette.ACID -> PaletteSpec(
        Color(0xFFB6FF3B), Color(0xFF061000), Color(0xFF4D7C0F), Color.White,
        Color(0xFF7CFF00), Color(0xFFE5FF8A), Color(0xFFB6FF3B), Color(0xFF7CFF00), Color(0xFFE5FF8A),
        Color(0xFF020304), Color(0xFFF1F7E9),
    )
    Palette.CYBER -> PaletteSpec(
        Color(0xFF00E5FF), Color(0xFF001A1F), Color(0xFF0E7490), Color.White,
        Color(0xFFFF2BD6), Color(0xFF6CFFEF), Color(0xFF00E5FF), Color(0xFFFF2BD6), Color(0xFF6CFFEF),
        Color(0xFF020304), Color(0xFFEAFBFD),
    )
    Palette.VOLT -> PaletteSpec(
        Color(0xFFFFEA00), Color(0xFF1A1600), Color(0xFFA16207), Color.White,
        Color(0xFFFF8A00), Color(0xFFFFF7A8), Color(0xFFFFEA00), Color(0xFFFF8A00), Color(0xFFFFF7A8),
        Color(0xFF030302), Color(0xFFFBF9E8),
    )
    Palette.PLASMA -> PaletteSpec(
        Color(0xFFFF3BD4), Color(0xFF21001A), Color(0xFFA21CAF), Color.White,
        Color(0xFF8B5CF6), Color(0xFFFF9EE5), Color(0xFFFF3BD4), Color(0xFF8B5CF6), Color(0xFFFF9EE5),
        Color(0xFF030204), Color(0xFFFCF2FA),
    )
    Palette.MATRIX -> PaletteSpec(
        Color(0xFF3BFF8F), Color(0xFF001A0B), Color(0xFF047857), Color.White,
        Color(0xFF00FFC8), Color(0xFFB6FFDA), Color(0xFF3BFF8F), Color(0xFF00FFC8), Color(0xFFB6FFDA),
        Color(0xFF020304), Color(0xFFEDFAF3),
    )
}

private fun styleFor(theme: UiTheme) = when (theme) {
    UiTheme.GLASS -> AppStyle(theme, 28.dp, 16.dp, FontFamily.SansSerif, FontFamily.SansSerif, FontFamily.SansSerif)
    UiTheme.PAPER -> AppStyle(theme, 10.dp, 8.dp, FontFamily.Serif, FontFamily.SansSerif, FontFamily.SansSerif)
    UiTheme.NEON -> AppStyle(theme, 6.dp, 4.dp, FontFamily.Monospace, FontFamily.SansSerif, FontFamily.Monospace)
}

private fun darkScheme(spec: PaletteSpec, theme: UiTheme): androidx.compose.material3.ColorScheme {
    val base = spec.darkBase
    val primary = spec.primaryDark
    return darkColorScheme(
        primary = primary,
        onPrimary = spec.onPrimaryDark,
        primaryContainer = lerp(base, primary, 0.22f),
        onPrimaryContainer = primary,
        secondary = spec.secondary,
        onSecondary = Color(0xFF160B26),
        secondaryContainer = lerp(base, spec.secondary, 0.20f),
        onSecondaryContainer = spec.secondary,
        tertiary = spec.tertiary,
        onTertiary = Color(0xFF041A16),
        tertiaryContainer = lerp(base, spec.tertiary, 0.18f),
        onTertiaryContainer = spec.tertiary,
        background = base,
        onBackground = Color(0xFFF2F5FA),
        surface = base,
        onSurface = Color(0xFFF2F5FA),
        surfaceVariant = lerp(base, Color.White, 0.08f),
        onSurfaceVariant = Color(0xFFA8B1C2),
        surfaceContainerLowest = lerp(base, Color.Black, 0.15f),
        surfaceContainerLow = lerp(base, Color.White, 0.04f),
        surfaceContainer = lerp(base, Color.White, 0.07f),
        surfaceContainerHigh = lerp(base, Color.White, 0.11f),
        surfaceContainerHighest = lerp(base, Color.White, 0.15f),
        outline = Color(0xFF5A6476),
        outlineVariant = lerp(base, Color.White, 0.20f),
        inverseSurface = Color(0xFFE7ECF4),
        inverseOnSurface = Color(0xFF10141C),
        inversePrimary = spec.primaryLight,
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
    )
}

private fun lightScheme(spec: PaletteSpec, theme: UiTheme): androidx.compose.material3.ColorScheme {
    val base = spec.lightBase
    val primary = spec.primaryLight
    return lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = lerp(base, primary, 0.14f),
        onPrimaryContainer = lerp(Color.Black, primary, 0.62f),
        secondary = lerp(Color.Black, spec.secondary, 0.42f),
        onSecondary = Color.White,
        secondaryContainer = lerp(base, spec.secondary, 0.18f),
        onSecondaryContainer = lerp(Color.Black, spec.secondary, 0.62f),
        tertiary = lerp(Color.Black, spec.tertiary, 0.45f),
        onTertiary = Color.White,
        tertiaryContainer = lerp(base, spec.tertiary, 0.20f),
        onTertiaryContainer = lerp(Color.Black, spec.tertiary, 0.62f),
        background = base,
        onBackground = Color(0xFF10141B),
        surface = base,
        onSurface = Color(0xFF10141B),
        surfaceVariant = lerp(base, Color.Black, 0.07f),
        onSurfaceVariant = Color(0xFF495263),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = lerp(base, Color.White, 0.55f),
        surfaceContainer = lerp(base, Color.White, 0.35f),
        surfaceContainerHigh = lerp(base, Color.White, 0.18f),
        surfaceContainerHighest = lerp(base, Color.Black, 0.06f),
        outline = Color(0xFF6D7789),
        outlineVariant = lerp(base, Color.Black, 0.16f),
        inverseSurface = Color(0xFF1C222D),
        inverseOnSurface = Color(0xFFEDF1F7),
        inversePrimary = spec.primaryDark,
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    )
}

@Composable
fun UniversalM3Theme(
    settings: ThemeSettings = ThemeSettings(),
    content: @Composable () -> Unit,
) {
    val dark = settings.uiTheme == UiTheme.NEON || when (settings.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val spec = specOf(settings.palette)
    val accent = AccentPalette(
        primary = if (dark) spec.primaryDark else spec.primaryLight,
        onPrimary = if (dark) spec.onPrimaryDark else Color.White,
        primaryLight = spec.primaryLight,
        secondary = spec.secondary,
        glowA = spec.glowA,
        glowB = spec.glowB,
        glowC = spec.glowC,
    )
    CompositionLocalProvider(
        LocalAccentPalette provides accent,
        LocalPaletteSpec provides spec,
        LocalUiTheme provides settings.uiTheme,
        LocalDarkTheme provides dark,
        LocalGlassEnabled provides (settings.glass && settings.uiTheme == UiTheme.GLASS),
        LocalAppStyle provides styleFor(settings.uiTheme),
    ) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme(spec, settings.uiTheme) else lightScheme(spec, settings.uiTheme),
            content = content,
        )
    }
}
