package io.github.cn_cizhanyue.lyricon.universal.provider.ui.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.AppDesign
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.LocalAppDesign
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.drawWallpaper

@Composable
fun GlassScaffold(
    design: AppDesign,
    customBackground: ImageBitmap?,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAppDesign provides design) {
        Box(Modifier.fillMaxSize().background(design.colors.background)) {
            Canvas(Modifier.fillMaxSize()) { drawWallpaper(design, customBackground) }
            content()
        }
    }
}
