package io.github.cn_cizhanyue.lyricon.universal.provider.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.Palette
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.PaletteSpec
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.ThemeMode
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.UiTheme
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.ThemeSettings
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.GlassEffect
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.specOf

data class AppColors(
    val primary: Color,
    val onPrimary: Color,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val glassTint: Color,
    val glassBorder: Color,
    val shadow: Color,
    val wallpaperA: Color,
    val wallpaperB: Color,
    val wallpaperC: Color,
)

object AppDimens {
    val pagePadding: Dp = 20.dp
    val cardRadius: Dp = 28.dp
    val glassRadius: Dp = 26.dp
    val navRadius: Dp = 30.dp
    val cardInnerPadding: Dp = 16.dp
    val itemSpacing: Dp = 10.dp
    val navHeight: Dp = 58.dp
    val iconSmall: Dp = 20.dp
    val iconMedium: Dp = 28.dp
    val coverRadius: Dp = 32.dp
    val minTouch: Dp = 48.dp
}

data class AppTypography(
    val title: TextStyle,
    val hero: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
)

data class AppDesign(
    val uiTheme: UiTheme,
    val dark: Boolean,
    val colors: AppColors,
    val typography: AppTypography,
    val glassEnabled: Boolean,
) {
    fun shape(radius: Dp = AppDimens.cardRadius): Shape = RoundedCornerShape(radius)
}

val LocalAppDesign = staticCompositionLocalOf<AppDesign> {
    error("AppDesign not provided")
}

@Composable
fun rememberAppDesign(settings: ThemeSettings): AppDesign {
    val spec = specOf(settings.palette)
    val dark = when (settings.mode) {
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) darkColors(spec) else lightColors(spec)
    return AppDesign(
        uiTheme = settings.uiTheme,
        dark = dark,
        colors = colors,
        typography = when (settings.uiTheme) {
            UiTheme.PAPER -> AppTypography(
                title = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = colors.onSurface),
                hero = TextStyle(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = Color.White),
                body = TextStyle(fontSize = 15.sp, fontFamily = FontFamily.Serif, color = colors.onSurface),
                label = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Serif, color = colors.onSurfaceVariant),
                caption = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Serif, color = colors.onSurfaceVariant),
            )
            UiTheme.NEON -> AppTypography(
                title = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = colors.onSurface),
                hero = TextStyle(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White),
                body = TextStyle(fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = colors.onSurface),
                label = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = colors.primary),
                caption = TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = colors.onSurfaceVariant),
            )
            else -> AppTypography(
                title = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = colors.onSurface),
                hero = TextStyle(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = Color.White),
                body = TextStyle(fontSize = 15.sp, color = colors.onSurface),
                label = TextStyle(fontSize = 12.sp, color = colors.onSurfaceVariant),
                caption = TextStyle(fontSize = 11.sp, color = colors.onSurfaceVariant),
            )
        },
        glassEnabled = settings.effect == GlassEffect.TRANSLUCENT,
    )
}

private fun lightColors(spec: PaletteSpec) = AppColors(
    primary = spec.primaryLight,
    onPrimary = spec.onPrimaryLight,
    background = spec.lightBase,
    surface = Color.White,
    surfaceHigh = Color.White,
    onSurface = Color(0xFF14151A),
    onSurfaceVariant = Color(0xFF5F6368),
    outline = Color(0xFFD9DCE3),
    glassTint = Color.White.copy(alpha = 0.72f),
    glassBorder = Color.White.copy(alpha = 0.55f),
    shadow = Color.Black.copy(alpha = 0.10f),
    wallpaperA = spec.glowA,
    wallpaperB = spec.glowB,
    wallpaperC = spec.glowC,
)

private fun darkColors(spec: PaletteSpec) = AppColors(
    primary = spec.primaryDark,
    onPrimary = spec.onPrimaryDark,
    background = lerp(spec.darkBase, Color.White, 0.10f),
    surface = lerp(spec.darkBase, Color.White, 0.15f),
    surfaceHigh = lerp(spec.darkBase, Color.White, 0.21f),
    onSurface = Color(0xFFF7F8FA),
    onSurfaceVariant = Color(0xFFC8CDD6),
    outline = Color(0xFF3A3E46),
    glassTint = Color.White.copy(alpha = 0.08f),
    glassBorder = Color.White.copy(alpha = 0.30f),
    shadow = Color.Black.copy(alpha = 0.45f),
    wallpaperA = spec.glowA,
    wallpaperB = spec.glowB,
    wallpaperC = spec.glowC,
)

object Motion {
    const val fast = 180
    const val normal = 240
    const val slow = 320
}

fun appShape(radius: Dp) = RoundedCornerShape(radius)
