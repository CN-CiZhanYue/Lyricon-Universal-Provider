package io.github.cn_cizhanyue.lyricon.universal.provider.ui

import android.content.Context
import android.widget.Toast
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cn_cizhanyue.lyricon.universal.provider.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalAppIo
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalLyricHistory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    snapshotMap: Map<String, String>,
    artworkBitmap: Bitmap?,
    onRefreshSnapshot: () -> Unit,
    onConfirmNoLyric: () -> Unit,
    onNavigateToSearch: (String) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val aboutPrefs = remember { context.getSharedPreferences("lyricon_fork", Context.MODE_PRIVATE) }
    var showAbout by remember { mutableStateOf(!aboutPrefs.getBoolean("about_seen", false)) }
    LaunchedEffect(Unit) {
        if (showAbout) {
            aboutPrefs.edit().putBoolean("about_seen", true).apply()
        }
    }
    val scope = rememberCoroutineScope()
    val currentTitle = snapshotMap["title"].orEmpty().takeUnless { it == "(missing)" }.orEmpty()
    val currentArtist = snapshotMap["artist"].orEmpty().takeUnless { it == "(missing)" }.orEmpty()
    val currentAlbum = snapshotMap["album"].orEmpty().takeUnless { it == "(missing)" }.orEmpty()
    val currentPkg = snapshotMap["package"].orEmpty().takeUnless { it == "(none)" }.orEmpty()
    val lyricSource = formatSourceName(context, snapshotMap["lyricSource"].orEmpty().substringAfterLast('/'))
    val playerLabel by produceState(currentPkg.ifBlank { "—" }, currentPkg, context) {
        value = if (currentPkg.isBlank()) "—" else withContext(Dispatchers.IO) {
            InstalledAppCatalog.peek().firstOrNull { it.packageName == currentPkg }?.label
                ?: runCatching {
                    val info = context.packageManager.getApplicationInfo(currentPkg, 0)
                    context.packageManager.getApplicationLabel(info).toString()
                }.getOrDefault(currentPkg)
        }
    }
    val lyricStatus = when (snapshotMap["lyricStatus"]) {
        "success" -> stringResource(R.string.lyric_status_success)
        "noLyric" -> stringResource(R.string.lyric_status_none)
        "failed" -> stringResource(R.string.lyric_status_failed)
        else -> stringResource(R.string.lyric_status_fetching)
    }

    var historyList by remember { mutableStateOf<List<UniversalLyricHistory.Entry>>(emptyList()) }
    val coverImage = remember(artworkBitmap) { artworkBitmap?.asImageBitmap() }

    // system_server's rows also change when a skipped track's fetch finishes.
    val serverHistory = UniversalLyricHistory.snapshotKeys.joinToString("\n") { snapshotMap[it].orEmpty() }
    LaunchedEffect(currentTitle, currentArtist, lyricSource, lyricStatus, serverHistory) {
        historyList = withContext(UniversalAppIo.dispatcher) {
            // Idempotent with SnapshotReceiver; recording here keeps the list in step with this snapshot.
            UniversalLyricHistory.record(context, snapshotMap)
            UniversalLyricHistory.load(context)
        }
    }

    if (showAbout) {
        AboutDialog(
            onDismiss = { showAbout = false },
            onOpenNetworkSettings = onNavigateToSettings,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.home_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Home, contentDescription = null)
                }
            },
            actions = {
                IconButton(onClick = onRefreshSnapshot) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                }
            },
            windowInsets = WindowInsets.statusBars,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
    ) {
        item {
        // 当前播放卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .clickable {
                    if (currentPkg.isNotBlank()) {
                        context.packageManager.getLaunchIntentForPackage(currentPkg)?.let { context.startActivity(it) }
                    }
                },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    if (coverImage != null) {
                        Image(
                            bitmap = coverImage,
                            contentDescription = "封面",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = currentTitle.ifBlank { stringResource(R.string.waiting_playback) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = listOf(currentAlbum, currentArtist).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { stringResource(R.string.album_artist_placeholder) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.player_label, playerLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.lyric_label, lyricStatus, lyricSource),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (snapshotMap["lyricClockFallback"] == "true") {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.lyric_clock_fallback),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // LSP 状态卡片
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(
                        onClick = {},
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.lsp_ok),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.lsp_connected),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.bluetooth_warning_short),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            stringResource(R.string.recent_cache),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        }

        // 5 条列表
        items((0 until 5).toList(), key = { it }, contentType = { "history-row" }) { index ->
                val entry = historyList.getOrNull(index)
                val isFirst = index == 0
                val isLast = index == 4
                val shape = when {
                    isFirst -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                    isLast -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
                    else -> RoundedCornerShape(8.dp)
                }

                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(shape)
                        .clickable {
                            entry?.let {
                                scope.launch {
                                    val artist = it.artist.ifBlank {
                                        withContext(UniversalAppIo.dispatcher) { lookupArtist(context, it.title) }
                                    }
                                    val query = listOf(it.title, artist)
                                        .filter { value -> value.isNotBlank() && value != "—" }
                                        .joinToString(" ")
                                    onNavigateToSearch(query)
                                }
                            }
                        },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    leadingContent = {
                        val icon = when (entry?.status) {
                            "failed", "获取失败", "Fetch failed" -> Icons.Filled.Cancel
                            "noLyric", "纯音乐 / 已确认无词" -> Icons.Filled.MusicOff
                            "pending", "fetching", "正在获取", "Fetching" -> Icons.Filled.Sync
                            else -> Icons.Filled.CheckCircle
                        }
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    headlineContent = {
                        Text(
                            text = entry?.title ?: stringResource(R.string.no_record),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        entry?.let {
                            Text(
                                text = formatSourceName(context, it.source) + " · " + historyStatusLabel(it.status),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    trailingContent = {
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
    }

    // FAB 语言切换菜单
    var fabExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 16.dp, bottom = 12.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (fabExpanded) {
                FloatingActionButton(
                    onClick = {
                        fabExpanded = false
                        onLanguageSelected(LanguageStore.EN)
                        Toast.makeText(context, context.getString(R.string.language_switched_en), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text("English", modifier = Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold)
                }
                FloatingActionButton(
                    onClick = {
                        fabExpanded = false
                        onLanguageSelected(LanguageStore.ZH)
                        Toast.makeText(context, context.getString(R.string.language_switched_zh), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text("中文", modifier = Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold)
                }
            }
            FloatingActionButton(
                onClick = { fabExpanded = !fabExpanded },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    if (fabExpanded) Icons.Filled.Close else Icons.Filled.Translate,
                    contentDescription = stringResource(R.string.action_language)
                )
            }
        }
    }
    }
}

private fun lookupArtist(context: Context, title: String): String {
    val prefix = title.trim().lowercase() + "|"
    val prefs = context.getSharedPreferences("universal_manual_bindings", Context.MODE_PRIVATE)
    return prefs.all.keys.firstOrNull { it.startsWith(prefix) }?.substringAfter('|').orEmpty()
}




@Composable
private fun historyStatusLabel(status: String): String {
    return when (status) {
        "success", "已获取", "Fetched" -> stringResource(R.string.lyric_status_success)
        "noLyric", "纯音乐 / 已确认无词", "Instrumental / confirmed no lyrics" -> stringResource(R.string.lyric_status_none)
        "failed", "获取失败", "Fetch failed" -> stringResource(R.string.lyric_status_failed)
        "pending", "fetching", "正在获取", "Fetching" -> stringResource(R.string.lyric_status_fetching)
        else -> status.ifBlank { stringResource(R.string.lyric_status_fetching) }
    }
}

// Older history rows stored the label localized at record time; map those back as well.
private fun formatSourceName(context: Context, raw: String): String {
    val clean = raw.trim().lowercase()
    return when {
        clean.contains("manual") || clean.contains("手动") -> context.getString(R.string.source_manual)
        clean.contains("qq") -> context.getString(R.string.source_qq)
        clean.contains("netease") || clean.contains("网易") -> context.getString(R.string.source_netease)
        clean.contains("apple") -> context.getString(R.string.source_apple)
        clean == "none" || clean == "—" -> "—"
        else -> raw.ifBlank { "—" }
    }
}
