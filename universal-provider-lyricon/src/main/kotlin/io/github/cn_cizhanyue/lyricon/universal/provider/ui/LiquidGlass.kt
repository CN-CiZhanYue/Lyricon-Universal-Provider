package io.github.cn_cizhanyue.lyricon.universal.provider.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cn_cizhanyue.lyricon.universal.provider.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun ThemedBackground(content: @Composable BoxScope.() -> Unit) {
    val context = LocalContext.current
    val spec = LocalPaletteSpec.current
    val dark = LocalDarkTheme.current
    val theme = LocalUiTheme.current
    val glass = LocalGlassEnabled.current
    val base = MaterialTheme.colorScheme.background
    val customPath = ThemeStore.settings.customBackground
    val custom by produceState<ImageBitmap?>(initialValue = null, customPath) {
        value = withContext(Dispatchers.IO) {
            customPath?.let { decodeBackground(it) }
        }
    }
    Box(Modifier.fillMaxSize().background(base)) {
        if (custom != null) {
            Image(
                bitmap = custom!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (dark) 0.45f else 0.25f))
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, base.copy(alpha = 0.56f))))
            )
        } else {
            when (theme) {
                UiTheme.GLASS -> GlassField(spec, dark, glass)
                UiTheme.PAPER -> PaperField(dark)
                UiTheme.NEON -> NeonField(spec)
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, base.copy(alpha = 0.56f))))
            )
        }
        content()
    }
}

@Composable
private fun GlassField(spec: PaletteSpec, dark: Boolean, glass: Boolean) {
    val glow = if (dark) 0.50f else 0.30f
    val layer = if (glass && Build.VERSION.SDK_INT >= 31) {
        Modifier.fillMaxSize().blur(58.dp, BlurredEdgeTreatment.Unbounded)
    } else {
        Modifier.fillMaxSize()
    }
    Canvas(layer) {
        val w = size.width
        val h = size.height
        val radius = size.maxDimension * 0.78f
        drawCircle(
            brush = Brush.radialGradient(listOf(spec.glowA.copy(alpha = glow), Color.Transparent), Offset(w * 0.12f, h * 0.08f), radius),
            radius = radius,
            center = Offset(w * 0.12f, h * 0.08f),
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(spec.glowB.copy(alpha = glow * 0.8f), Color.Transparent), Offset(w * 0.92f, h * 0.24f), radius * 0.9f),
            radius = radius * 0.9f,
            center = Offset(w * 0.92f, h * 0.24f),
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(spec.glowC.copy(alpha = glow * 0.55f), Color.Transparent), Offset(w * 0.55f, h * 0.92f), radius * 0.85f),
            radius = radius * 0.85f,
            center = Offset(w * 0.55f, h * 0.92f),
        )
    }
}

@Composable
private fun PaperField(dark: Boolean) {
    val line = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (dark) 0.22f else 0.34f)
    val accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    Canvas(Modifier.fillMaxSize()) {
        val step = 30.dp.toPx()
        var y = step
        while (y < size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
        drawLine(
            accent,
            Offset(24.dp.toPx(), 0f),
            Offset(24.dp.toPx(), size.height),
            strokeWidth = 1.5f,
        )
    }
}

@Composable
private fun NeonField(spec: PaletteSpec) {
    Canvas(Modifier.fillMaxSize()) {
        val step = 34.dp.toPx()
        val line = spec.primaryDark.copy(alpha = 0.15f)
        var x = 0f
        while (x < size.width) {
            drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
        drawCircle(
            brush = Brush.radialGradient(
                listOf(spec.glowB.copy(alpha = 0.30f), Color.Transparent),
                Offset(size.width * 0.5f, size.height * 0.15f),
                size.maxDimension * 0.7f,
            ),
            radius = size.maxDimension * 0.7f,
            center = Offset(size.width * 0.5f, size.height * 0.15f),
        )
    }
}

private fun decodeBackground(path: String): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}

@Composable
fun AppPanel(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val style = LocalAppStyle.current
    val theme = LocalUiTheme.current
    val dark = LocalDarkTheme.current
    val glass = LocalGlassEnabled.current
    val spec = LocalPaletteSpec.current
    val resolvedShape = shape ?: RoundedCornerShape(style.panelRadius)
    val glassMode = theme == UiTheme.GLASS && glass
    val fill: Brush = when (theme) {
        UiTheme.GLASS -> when {
            !glass -> SolidColor(MaterialTheme.colorScheme.surfaceContainerLow)
            dark -> Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.17f), Color.White.copy(alpha = 0.045f))
            )
            else -> Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.80f), Color.White.copy(alpha = 0.42f))
            )
        }
        UiTheme.PAPER -> SolidColor(if (dark) MaterialTheme.colorScheme.surfaceContainerLow else Color.White.copy(alpha = 0.74f))
        UiTheme.NEON -> SolidColor(MaterialTheme.colorScheme.surfaceContainerLowest)
    }
    val border: Brush = when (theme) {
        UiTheme.GLASS -> Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (dark) 0.55f else 0.95f),
                spec.glowA.copy(alpha = 0.30f),
                Color.White.copy(alpha = if (dark) 0.08f else 0.15f),
            )
        )
        UiTheme.PAPER -> SolidColor(MaterialTheme.colorScheme.outlineVariant)
        UiTheme.NEON -> SolidColor(spec.primaryDark.copy(alpha = 0.60f))
    }
    val shadowed = if (glassMode) {
        modifier.shadow(
            elevation = 12.dp,
            shape = resolvedShape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = if (dark) 0.45f else 0.18f),
            spotColor = Color.Black.copy(alpha = if (dark) 0.35f else 0.12f),
        )
    } else {
        modifier
    }
    val highlight = if (glassMode) {
        Modifier.drawWithContent {
            drawContent()
            drawLine(
                color = Color.White.copy(alpha = if (dark) 0.28f else 0.55f),
                start = Offset(1.dp.toPx(), 1.dp.toPx()),
                end = Offset(size.width - 1.dp.toPx(), 1.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
        }
    } else {
        Modifier
    }
    Box(
        modifier = shadowed
            .clip(resolvedShape)
            .background(fill)
            .border(1.dp, border, resolvedShape)
            .then(highlight),
        content = content,
    )
}

@Composable
fun WaveBars(active: Boolean, bars: Int = 7, maxHeight: Int = 26) {
    val palette = LocalAccentPalette.current
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave-phase",
    )
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(bars) { index ->
            val wave = if (active) 0.25f + 0.75f * abs(sin((phase * 6.283f) + index * 0.8f)) else 0.4f
            Box(
                Modifier
                    .width(4.dp)
                    .height((6 + (maxHeight - 6) * wave).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(palette.primary.copy(alpha = 0.75f))
            )
        }
    }
}

@Composable
fun AppNavBar(current: Screen, onSelect: (Screen) -> Unit) {
    val style = LocalAppStyle.current
    val theme = LocalUiTheme.current
    val items = listOf(
        Screen.HOME to Icons.Filled.Home,
        Screen.PLAYERS to Icons.Filled.PlayCircle,
        Screen.SEARCH to Icons.Filled.Search,
        Screen.SETTINGS to Icons.Filled.Settings,
    )
    val labels = mapOf(
        Screen.HOME to R.string.nav_home,
        Screen.PLAYERS to R.string.nav_players,
        Screen.SEARCH to R.string.nav_search,
        Screen.SETTINGS to R.string.nav_settings,
    )
    when (theme) {
        UiTheme.GLASS -> AppPanel(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            shape = RoundedCornerShape(30.dp),
        ) {
            NavRow(current, items, labels, onSelect, style, Modifier.fillMaxWidth().height(62.dp).padding(6.dp))
        }
        UiTheme.PAPER -> Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavRow(
                    current, items, labels, onSelect, style,
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(64.dp)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        UiTheme.NEON -> AppPanel(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            shape = RoundedCornerShape(6.dp),
        ) {
            NavRow(current, items, labels, onSelect, style, Modifier.fillMaxWidth().height(58.dp).padding(4.dp))
        }
    }
}

@Composable
private fun NavRow(
    current: Screen,
    items: List<Pair<Screen, androidx.compose.ui.graphics.vector.ImageVector>>,
    labels: Map<Screen, Int>,
    onSelect: (Screen) -> Unit,
    style: AppStyle,
    modifier: Modifier,
) {
    val palette = LocalAccentPalette.current
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (screen, icon) ->
            val selected = current == screen || (screen == Screen.SETTINGS && current == Screen.PRIORITY)
            val label = stringResource(labels.getValue(screen))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(style.controlRadius))
                    .background(if (selected) palette.primary.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { onSelect(screen) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (selected) palette.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(21.dp),
                )
                Text(
                    label,
                    fontSize = 10.sp,
                    fontFamily = style.labelFont,
                    color = if (selected) palette.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
