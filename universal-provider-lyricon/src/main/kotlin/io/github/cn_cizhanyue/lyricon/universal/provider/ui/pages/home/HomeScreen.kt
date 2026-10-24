package io.github.cn_cizhanyue.lyricon.universal.provider.ui.pages.home

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cn_cizhanyue.lyricon.universal.provider.R
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalAppIo
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalLyricHistory
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.AppDimens
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.LocalAppDesign
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.glass.GlassSurface
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    snapshot: Map<String, String>,
    artwork: Bitmap?,
    cachedSongs: Int,
) {
    val context = LocalContext.current
    val design = LocalAppDesign.current
    val title = snapshot["title"].orEmpty().takeUnless { it == "(missing)" }.orEmpty()
    val artist = snapshot["artist"].orEmpty().takeUnless { it == "(missing)" }.orEmpty()
    val source = snapshot["lyricSource"].orEmpty().substringAfterLast('/')
    val status = when (snapshot["lyricStatus"]) {
        "success" -> stringResource(R.string.lyric_status_success)
        "noLyric" -> stringResource(R.string.lyric_status_none)
        "failed" -> stringResource(R.string.lyric_status_failed)
        else -> stringResource(R.string.lyric_status_fetching)
    }
    var history by remember { mutableStateOf<List<UniversalLyricHistory.Entry>>(emptyList()) }
    LaunchedEffect(snapshot) {
        withContext(UniversalAppIo.dispatcher) {
            UniversalLyricHistory.record(context, snapshot)
            history = UniversalLyricHistory.load(context)
        }
    }
    val art = remember(artwork) { artwork?.asImageBitmap() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = AppDimens.pagePadding, end = AppDimens.pagePadding, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing),
    ) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(AppDimens.coverRadius))
                    .background(design.colors.wallpaperA),
            ) {
                if (art != null) {
                    Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(design.colors.primary, design.colors.wallpaperB))))
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    Text(
                        title.ifBlank { stringResource(R.string.track_placeholder) },
                        color = Color.White,
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(artist.ifBlank { stringResource(R.string.artist_placeholder) }, color = Color.White.copy(alpha = 0.82f), fontSize = 16.sp, maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            status,
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                        Text(source.ifBlank { "—" }, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassSurface(Modifier.weight(1f)) {
                    Text(stringResource(R.string.lsp_ok), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.lyricon_sync), style = MaterialTheme.typography.bodySmall, color = design.colors.onSurfaceVariant)
                }
                GlassSurface(Modifier.weight(1f)) {
                    Text(stringResource(R.string.cached_songs, cachedSongs), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.lyric_cache), style = MaterialTheme.typography.bodySmall, color = design.colors.onSurfaceVariant)
                }
            }
        }
        item {
            Text(stringResource(R.string.recent_cache), style = design.typography.label, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        }
        if (history.isEmpty()) {
            item {
                GlassSurface {
                    Text(stringResource(R.string.no_record), style = MaterialTheme.typography.bodyMedium, color = design.colors.onSurfaceVariant)
                }
            }
        } else {
            items(history.take(5), key = { it.title + it.artist }) { entry ->
                GlassSurface {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MusicNote, null, tint = design.colors.primary, modifier = Modifier.size(AppDimens.iconSmall))
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(entry.artist.ifBlank { stringResource(R.string.artist_placeholder) }, style = MaterialTheme.typography.bodySmall, color = design.colors.onSurfaceVariant, maxLines = 1)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = design.colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
