package io.github.cn_cizhanyue.lyricon.universal.provider.ui.glass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.AppDimens
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.LocalAppDesign

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(AppDimens.cardRadius),
    content: @Composable ColumnScope.() -> Unit,
) {
    val design = LocalAppDesign.current
    val colors = design.colors
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(3.dp, shape, clip = false, ambientColor = colors.shadow, spotColor = colors.shadow),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = when {
                !design.glassEnabled -> colors.surface
                design.dark -> colors.surface.copy(alpha = 0.62f)
                else -> Color.White.copy(alpha = 0.56f)
            },
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (design.dark) 0.28f else 0.68f)),
    ) {
        Column(
            Modifier
                .padding(AppDimens.cardInnerPadding)
                .drawWithContent {
                    drawContent()
                    drawLine(
                        color = Color.White.copy(alpha = 0.45f),
                        start = Offset(1.dp.toPx(), 1.dp.toPx()),
                        end = Offset(size.width - 1.dp.toPx(), 1.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
            content = content,
        )
    }
}
