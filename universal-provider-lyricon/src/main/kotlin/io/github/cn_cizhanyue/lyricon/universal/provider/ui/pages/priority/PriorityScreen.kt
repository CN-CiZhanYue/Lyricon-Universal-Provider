package io.github.cn_cizhanyue.lyricon.universal.provider.ui.pages.priority

import android.widget.Toast
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.cn_cizhanyue.lyricon.universal.provider.LyricsSourceConfigStore
import io.github.cn_cizhanyue.lyricon.universal.provider.R
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalAppIo
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.controller.SettingsController
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.controller.SourceConfigBroadcaster
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.AppDimens
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.LocalAppDesign
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.glass.GlassSurface
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun PriorityScreen(settingsController: SettingsController, onSaved: () -> Unit) {
    val context = LocalContext.current
    val design = LocalAppDesign.current
    val settings by settingsController.state.collectAsState()
    var sources by remember { mutableStateOf<List<String>>(emptyList()) }
    var enabled by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember { mutableFloatStateOf(0f) }
    val rowSpacingPx = with(LocalDensity.current) { 6.dp.toPx() }
    LaunchedEffect(Unit) {
        val configured = withContext(UniversalAppIo.dispatcher) { LyricsSourceConfigStore.read(context) }
        val all = listOf("QQ", "NETEASE", "APPLE_MUSIC", "SHARED_CACHE")
        sources = all.sortedBy { configured.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        enabled = all.associateWith { it in configured }
        loaded = true
    }
    Column(Modifier.fillMaxSize().padding(horizontal = AppDimens.pagePadding, vertical = 4.dp)) {
        Text(stringResource(R.string.drag_to_reorder), style = design.typography.caption, modifier = Modifier.padding(bottom = 10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sources.forEachIndexed { index, id ->
                key(id) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 70.dp)
                            .onSizeChanged { if (it.height > 0) rowHeightPx = it.height.toFloat() }
                            .zIndex(if (draggingId == id) 1f else 0f)
                            .offset { IntOffset(0, if (draggingId == id) dragOffset.roundToInt() else 0) }
                            .shadow(if (draggingId == id) 8.dp else 0.dp, RoundedCornerShape(AppDimens.cardRadius))
                            .clip(RoundedCornerShape(AppDimens.cardRadius))
                            .pointerInput(id, rowSpacingPx) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggingId = id; dragOffset = 0f },
                                    onDragCancel = { draggingId = null; dragOffset = 0f },
                                    onDragEnd = { draggingId = null; dragOffset = 0f },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        val height = if (rowHeightPx > 0f) rowHeightPx else 80.dp.toPx()
                                        dragOffset += amount.y
                                        val from = sources.indexOf(id)
                                        if (from < 0) return@detectDragGesturesAfterLongPress
                                        val shift = (dragOffset / (height + rowSpacingPx)).toInt()
                                        val to = (from + shift).coerceIn(0, sources.lastIndex)
                                        if (to != from) {
                                            val next = sources.toMutableList()
                                            next.add(to, next.removeAt(from))
                                            sources = next
                                            dragOffset -= (to - from) * (height + rowSpacingPx)
                                        }
                                    },
                                )
                            },
                    ) {
                        GlassSurface {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.MusicNote, null, tint = design.colors.primary)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(sourceLabel(id))
                                    Text(stringResource(R.string.priority_position, index + 1), style = design.typography.caption)
                                }
                                Switch(checked = enabled[id] == true, onCheckedChange = { enabled = enabled + (id to it) })
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Filled.DragHandle, contentDescription = stringResource(R.string.drag_handle), tint = design.colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val selected = sources.filter { enabled[it] == true }
                SourceConfigBroadcaster.savePriority(context, selected, settings)
                Toast.makeText(context, context.getString(R.string.toast_priority_saved), Toast.LENGTH_SHORT).show()
                onSaved()
            },
            enabled = loaded,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp),
        ) { Text(stringResource(R.string.save_order)) }
    }
}

@Composable
private fun sourceLabel(id: String): String = when (id) {
    "QQ" -> stringResource(R.string.source_qq)
    "NETEASE" -> stringResource(R.string.source_netease)
    "APPLE_MUSIC" -> stringResource(R.string.source_apple)
    else -> stringResource(R.string.source_shared_cache)
}
