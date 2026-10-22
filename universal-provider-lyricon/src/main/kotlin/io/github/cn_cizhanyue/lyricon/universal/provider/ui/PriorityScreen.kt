package io.github.cn_cizhanyue.lyricon.universal.provider.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import io.github.cn_cizhanyue.lyricon.universal.provider.LyricsSourceConfigStore
import io.github.cn_cizhanyue.lyricon.universal.provider.R
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalSettingsConstants
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalSnapshotStore
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalAppIo
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriorityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val labels = remember {
        mapOf(
            "QQ" to "QQ 音乐",
            "NETEASE" to "网易云音乐",
            "APPLE_MUSIC" to "Apple Music",
            "SHARED_CACHE" to "和官方版共用（缓存）",
        )
    }
    var sourceList by remember { mutableStateOf<List<String>>(emptyList()) }
    val enabledMap = remember { mutableStateMapOf<String, Boolean>() }
    var settingsLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val configured = withContext(UniversalAppIo.dispatcher) { LyricsSourceConfigStore.read(context) }
        val all = mutableListOf("QQ", "NETEASE", "APPLE_MUSIC", "SHARED_CACHE")
        all.sortBy { configured.indexOf(it).let { index -> if (index < 0) Int.MAX_VALUE else index } }
        all.forEach { enabledMap[it] = it in configured }
        sourceList = all
        settingsLoaded = true
    }

    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember { mutableFloatStateOf(0f) }
    val rowSpacingPx = with(LocalDensity.current) { 6.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        TopAppBar(
            title = {
                Text(stringResource(R.string.priority_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            windowInsets = WindowInsets.statusBars,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Text(
            stringResource(R.string.drag_to_reorder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sourceList.forEachIndexed { index, id ->
                key(id) {
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .onSizeChanged { if (it.height > 0) rowHeightPx = it.height.toFloat() }
                            .zIndex(if (draggingId == id) 1f else 0f)
                            .offset { IntOffset(0, if (draggingId == id) dragOffset.roundToInt() else 0) }
                            .shadow(if (draggingId == id) 6.dp else 0.dp, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .pointerInput(id, rowSpacingPx) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingId = id
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggingId = null
                                        dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        draggingId = null
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        val height = if (rowHeightPx > 0f) rowHeightPx else 80.dp.toPx()
                                        dragOffset += amount.y
                                        val currentList = sourceList
                                        val from = currentList.indexOf(id)
                                        if (from < 0) return@detectDragGesturesAfterLongPress
                                        val shift = (dragOffset / (height + rowSpacingPx)).toInt()
                                        val to = (from + shift).coerceIn(0, currentList.lastIndex)
                                        if (to != from) {
                                            val newList = currentList.toMutableList()
                                            val moved = newList.removeAt(from)
                                            newList.add(to, moved)
                                            sourceList = newList
                                            dragOffset -= (to - from) * (height + rowSpacingPx)
                                        }
                                    }
                                )
                            },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        leadingContent = {
                            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        headlineContent = {
                            Text(labels[id] ?: id, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = {
                            Text(
                                stringResource(
                                    when (id) {
                                        "APPLE_MUSIC" -> R.string.priority_line
                                        "SHARED_CACHE" -> R.string.priority_shared
                                        else -> R.string.priority_word
                                    },
                                    index + 1,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = enabledMap[id] == true,
                                    onCheckedChange = { enabledMap[id] = it }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.Filled.DragHandle,
                                    contentDescription = stringResource(R.string.drag_handle),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            enabled = settingsLoaded,
            onClick = {
                val selected = sourceList.filter { enabledMap[it] == true }
                val appContext = context.applicationContext
                UniversalAppIo.execute {
                    LyricsSourceConfigStore.write(appContext, selected)
                    val prefs = appContext.getSharedPreferences(UniversalSettingsConstants.PREFS_NAME, Context.MODE_PRIVATE)
                    appContext.sendBroadcast(Intent(UniversalSnapshotStore.ACTION_UPDATE_SOURCE_CONFIG).apply {
                        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                        putExtra(UniversalSnapshotStore.EXTRA_SOURCE_PRIORITY, LyricsSourceConfigStore.encode(selected))
                        putExtra(UniversalSnapshotStore.EXTRA_WORD_TIMING_ENABLED, prefs.getBoolean(UniversalSettingsConstants.KEY_WORD_TIMING, false))
                        putExtra(UniversalSnapshotStore.EXTRA_TRANSLATION_ENABLED, prefs.getBoolean(UniversalSettingsConstants.KEY_TRANSLATION, true))
                        putExtra(UniversalSnapshotStore.EXTRA_RAW_LYRIC_ENABLED, prefs.getBoolean(UniversalSettingsConstants.KEY_RAW_LYRIC, true))
                        putExtra(UniversalSnapshotStore.EXTRA_DEBUG_ENABLED, prefs.getBoolean(UniversalSettingsConstants.KEY_DEBUG, false))
                        putExtra(
                            UniversalSnapshotStore.EXTRA_CLOCK_LINE_FALLBACK_ENABLED,
                            prefs.getBoolean(UniversalSettingsConstants.KEY_CLOCK_LINE_FALLBACK, true)
                        )
                    })
                }
                Toast.makeText(context, context.getString(R.string.toast_priority_saved), Toast.LENGTH_SHORT).show()
                onBack()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(stringResource(R.string.save_order), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
