package io.github.cn_cizhanyue.lyricon.universal.provider.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0B57D0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF5A5C7C),
    secondaryContainer = Color(0xFFDCE2F9),
    onSecondaryContainer = Color(0xFF131C2B),
    tertiaryContainer = Color(0xFFFFD8EE),
    onTertiaryContainer = Color(0xFF2E1125),
    surface = Color(0xFFFAF9FD),
    surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainer = Color(0xFFEEEDF3),
    surfaceContainerHigh = Color(0xFFE9E8EF),
    surfaceContainerHighest = Color(0xFFE3E2E6),
    onSurface = Color(0xFF1B1B1F),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    inverseSurface = Color(0xFF303034),
    inverseOnSurface = Color(0xFFF2F0F4),
    inversePrimary = Color(0xFFA8C7FA),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFC0C2FD),
    onPrimary = Color(0xFF042C71),
    primaryContainer = Color(0xFF04409F),
    onPrimaryContainer = Color(0xFFE0E0FC),
    secondary = Color(0xFFC3C3E9),
    secondaryContainer = Color(0xFF424463),
    onSecondaryContainer = Color(0xFFE0E0FE),
    tertiaryContainer = Color(0xFF6E334E),
    onTertiaryContainer = Color(0xFFFFD8E7),
    surface = Color(0xFF131317),
    surfaceContainerLow = Color(0xFF1B1B1F),
    surfaceContainer = Color(0xFF1F1F23),
    surfaceContainerHigh = Color(0xFF2A2A2E),
    surfaceContainerHighest = Color(0xFF343439),
    onSurface = Color(0xFFE2E2E8),
    onSurfaceVariant = Color(0xFFC6C5D2),
    outline = Color(0xFF90909C),
    outlineVariant = Color(0xFF464651),
    inverseSurface = Color(0xFFE2E2E8),
    inverseOnSurface = Color(0xFF303034),
    inversePrimary = Color(0xFF3757BA),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

@Composable
fun UniversalM3Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
