package io.github.cn_cizhanyue.lyricon.universal.provider.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.UiTheme
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.AppDimens
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.LocalAppDesign

/**
 * The single card primitive for all themes.
 *
 * A translucent card is a plain clipped [Box] rather than a Material Card: Card's
 * surface/shadow internals can tint through a transparent fill and produce the
 * grey patch that was visible in the glass theme. Keeping the fill and texture on
 * one layer also makes paper/neon and glass use the same geometry.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(AppDimens.cardRadius),
    content: @Composable ColumnScope.() -> Unit,
) {
    val design = LocalAppDesign.current
    val colors = design.colors
    val paper = design.uiTheme == UiTheme.PAPER
    val neon = design.uiTheme == UiTheme.NEON
    val glass = design.glassEnabled

    val fill: Brush = when {
        glass && paper -> Brush.verticalGradient(
            if (design.dark) listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.03f))
            else listOf(Color.White.copy(alpha = 0.54f), Color.White.copy(alpha = 0.24f))
        )
        glass && neon -> Brush.verticalGradient(
            listOf(colors.primary.copy(alpha = if (design.dark) 0.16f else 0.20f), Color.Transparent)
        )
        glass && design.dark -> Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f))
        )
        glass -> Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.54f), Color.White.copy(alpha = 0.24f))
        )
        paper -> SolidColor(if (design.dark) Color(0xFF201B13) else Color(0xFFFAF1DF))
        neon -> SolidColor(if (design.dark) Color(0xFF05070A) else Color(0xFF0B0F14))
        else -> SolidColor(colors.surface)
    }
    val outline = when {
        neon -> colors.primary.copy(alpha = 0.72f)
        paper -> colors.outline
        design.dark -> Color.White.copy(alpha = 0.26f)
        else -> Color.White.copy(alpha = 0.62f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (glass) Modifier else Modifier.shadow(
                    elevation = 3.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = colors.shadow,
                    spotColor = colors.shadow,
                )
            )
            .clip(shape)
            .background(fill, shape)
            .drawBehind {
                if (paper) {
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            colors.onSurfaceVariant.copy(alpha = 0.035f),
                            Offset(0f, y),
                            Offset(size.width, y),
                            1f,
                        )
                        y += 18f
                    }
                }
                if (neon) {
                    var x = 0f
                    while (x < size.width) {
                        drawLine(colors.primary.copy(alpha = 0.035f), Offset(x, 0f), Offset(x, size.height), 1f)
                        x += 28f
                    }
                    drawLine(colors.primary.copy(alpha = 0.22f), Offset(0f, 1f), Offset(size.width, 1f), 1f)
                }
            }
            .border(if (neon) 1.5.dp else 1.dp, outline, shape),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
            Column(
                Modifier
                    .padding(AppDimens.cardInnerPadding)
                    .drawWithContent {
                        drawContent()
                        if (glass && !paper && !neon) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.42f),
                                start = Offset(1.dp.toPx(), 1.dp.toPx()),
                                end = Offset(size.width - 1.dp.toPx(), 1.dp.toPx()),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }
                    },
                content = content,
            )
        }
    }
}
