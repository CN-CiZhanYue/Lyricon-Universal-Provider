package io.github.cn_cizhanyue.lyricon.universal.provider.ui

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun HomeScreen(
    snapshotMap: Map<String, String>,
    artworkBitmap: Bitmap?,
    onRefreshSnapshot: () -> Unit,
    onConfirmNoLyric: () -> Unit,
    onNavigateToSearch: (String) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPlayers: () -> Unit
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
    val uiTheme = LocalUiTheme.current

    val serverHistory = UniversalLyricHistory.snapshotKeys.joinToString("\n") { snapshotMap[it].orEmpty() }
    LaunchedEffect(currentTitle, currentArtist, lyricSource, lyricStatus, serverHistory) {
        historyList = withContext(UniversalAppIo.dispatcher) {
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
            when (uiTheme) {
                UiTheme.GLASS -> BrandHeader(
                    onRefresh = onRefreshSnapshot,
                    onSettings = onNavigateToSettings,
                )
                UiTheme.PAPER -> PaperHeader()
                UiTheme.NEON -> NeonHeader()
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (uiTheme) {
                    UiTheme.GLASS -> {
                        item {
                            NowPlayingCard(
                                title = currentTitle,
                                artist = currentArtist,
                                album = currentAlbum,
                                playerLabel = playerLabel,
                                lyricStatus = lyricStatus,
                                lyricSource = lyricSource,
                                cover = coverImage,
                                clockFallback = snapshotMap["lyricClockFallback"] == "true",
                                onClick = {
                                    if (currentPkg.isNotBlank()) {
                                        context.packageManager.getLaunchIntentForPackage(currentPkg)?.let {
                                            it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(it)
                                        }
                                    }
                                }
                            )
                        }
                        item {
                            QuickActions(
                                onPlayers = onNavigateToPlayers,
                                onSettings = onNavigateToSettings,
                                onRefresh = onRefreshSnapshot,
                            )
                        }
                        item { StatusCard() }
                    }
                    UiTheme.PAPER -> {
                        item {
                            PaperHero(
                                title = currentTitle,
                                artist = currentArtist,
                                album = currentAlbum,
                                playerLabel = playerLabel,
                                lyricStatus = lyricStatus,
                                lyricSource = lyricSource,
                                cover = coverImage,
                                clockFallback = snapshotMap["lyricClockFallback"] == "true",
                                onClick = {
                                    if (currentPkg.isNotBlank()) {
                                        context.packageManager.getLaunchIntentForPackage(currentPkg)?.let {
                                            it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(it)
                                        }
                                    }
                                }
                            )
                        }
                        item {
                            PaperQuick(
                                onPlayers = onNavigateToPlayers,
                                onSettings = onNavigateToSettings,
                                onRefresh = onRefreshSnapshot,
                            )
                        }
                        item { PaperStatus() }
                    }
                    UiTheme.NEON -> {
                        item {
                            NeonHero(
                                title = currentTitle,
                                artist = currentArtist,
                                album = currentAlbum,
                                packageName = currentPkg,
                                cover = coverImage,
                            )
                        }
                        item {
                            NeonStats(
                                playerLabel = playerLabel,
                                lyricStatus = lyricStatus,
                                lyricSource = lyricSource,
                                clockFallback = snapshotMap["lyricClockFallback"] == "true",
                            )
                        }
                        item {
                            NeonQuick(
                                onPlayers = onNavigateToPlayers,
                                onSettings = onNavigateToSettings,
                                onRefresh = onRefreshSnapshot,
                            )
                        }
                    }
                }

                item {
                    val style = LocalAppStyle.current
                    Text(
                        if (uiTheme == UiTheme.NEON) "// " + stringResource(R.string.recent_cache) else stringResource(R.string.recent_cache),
                        fontSize = 20.sp,
                        fontFamily = style.titleFont,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items((0 until 5).toList(), key = { it }, contentType = { "history-row" }) { index ->
                    val entry = historyList.getOrNull(index)
                    HistoryRow(
                        entry = entry,
                        index = index,
                        onClick = {
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
                        }
                    )
                }
            }
        }

        var fabExpanded by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 16.dp, bottom = 16.dp),
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

@Composable
private fun PaperHeader() {
    val style = LocalAppStyle.current
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp)
    ) {
        Text(
            stringResource(R.string.home_title),
            fontSize = 32.sp,
            fontFamily = style.titleFont,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(R.string.brand_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = style.bodyFont,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.padding(top = 12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun NeonHeader() {
    val style = LocalAppStyle.current
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
    ) {
        Row {
            Text("LYRICON://", fontFamily = style.labelFont, fontSize = 11.sp, color = LocalAccentPalette.current.primary)
            Text("UNIVERSAL PROVIDER", fontFamily = style.labelFont, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            stringResource(R.string.home_title),
            fontSize = 22.sp,
            fontFamily = style.titleFont,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        HorizontalDivider(color = LocalAccentPalette.current.primary.copy(alpha = 0.5f))
    }
}

@Composable
private fun PaperHero(
    title: String,
    artist: String,
    album: String,
    playerLabel: String,
    lyricStatus: String,
    lyricSource: String,
    cover: androidx.compose.ui.graphics.ImageBitmap?,
    clockFallback: Boolean,
    onClick: () -> Unit,
) {
    val style = LocalAppStyle.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(
            stringResource(R.string.now_playing),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            title.ifBlank { stringResource(R.string.waiting_playback) },
            fontSize = 34.sp,
            fontFamily = style.titleFont,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOf(artist, album).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = style.bodyFont,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(96.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (cover != null) {
                    Image(cover, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.player_label, playerLabel), style = MaterialTheme.typography.bodyMedium, fontFamily = style.bodyFont)
                Text(stringResource(R.string.lyric_label, lyricStatus, lyricSource), style = MaterialTheme.typography.bodySmall, fontFamily = style.bodyFont, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (clockFallback) {
                    Text(stringResource(R.string.lyric_clock_fallback), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun NeonHero(
    title: String,
    artist: String,
    album: String,
    packageName: String,
    cover: androidx.compose.ui.graphics.ImageBitmap?,
) {
    val style = LocalAppStyle.current
    val palette = LocalAccentPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(92.dp)
                .border(1.dp, palette.primary.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                Image(cover, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = palette.primary)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title.ifBlank { stringResource(R.string.waiting_playback) },
                fontSize = 22.sp,
                fontFamily = style.titleFont,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(artist, album).filter { it.isNotBlank() }.joinToString(" / "),
                fontFamily = style.labelFont,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "PKG ${packageName.ifBlank { "—" }}",
                fontFamily = style.labelFont,
                fontSize = 10.sp,
                color = palette.primary.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NeonStats(playerLabel: String, lyricStatus: String, lyricSource: String, clockFallback: Boolean) {
    val style = LocalAppStyle.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NeonStat("PLAYER", playerLabel, Modifier.weight(1f))
            NeonStat("SOURCE", lyricSource, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NeonStat("STATUS", lyricStatus, Modifier.weight(1f))
            NeonStat("CLOCK", if (clockFallback) "LINE" else "WORD", Modifier.weight(1f))
        }
    }
}

@Composable
private fun NeonStat(label: String, value: String, modifier: Modifier = Modifier) {
    val style = LocalAppStyle.current
    AppPanel(modifier = modifier, shape = RoundedCornerShape(2.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, fontFamily = style.labelFont, fontSize = 9.sp, color = LocalAccentPalette.current.primary)
            Text(
                value.ifBlank { "—" },
                fontFamily = style.labelFont,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PaperQuick(onPlayers: () -> Unit, onSettings: () -> Unit, onRefresh: () -> Unit) {
    val style = LocalAppStyle.current
    Column {
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = onPlayers, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.quick_players), fontFamily = style.bodyFont)
            }
            TextButton(onClick = onSettings, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.nav_settings), fontFamily = style.bodyFont)
            }
            TextButton(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_refresh), fontFamily = style.bodyFont)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun NeonQuick(onPlayers: () -> Unit, onSettings: () -> Unit, onRefresh: () -> Unit) {
    val style = LocalAppStyle.current
    val palette = LocalAccentPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            stringResource(R.string.quick_players) to onPlayers,
            stringResource(R.string.nav_settings) to onSettings,
            stringResource(R.string.action_refresh) to onRefresh,
        ).forEach { (label, action) ->
            OutlinedButton(
                onClick = action,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(2.dp),
                border = BorderStroke(1.dp, palette.primary.copy(alpha = 0.55f)),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text(label, fontFamily = style.labelFont, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PaperStatus() {
    val style = LocalAppStyle.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.lsp_ok), style = MaterialTheme.typography.bodyMedium, fontFamily = style.bodyFont, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.lsp_connected), style = MaterialTheme.typography.bodySmall, fontFamily = style.bodyFont, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.bluetooth_warning_short), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun BrandHeader(onRefresh: () -> Unit, onSettings: () -> Unit) {
    val palette = LocalAccentPalette.current
    val style = LocalAppStyle.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppPanel(modifier = Modifier.size(44.dp), shape = RoundedCornerShape(16.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = palette.primary, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.home_title),
                fontSize = 20.sp,
                fontFamily = style.titleFont,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.brand_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh), tint = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun NowPlayingCard(
    title: String,
    artist: String,
    album: String,
    playerLabel: String,
    lyricStatus: String,
    lyricSource: String,
    cover: androidx.compose.ui.graphics.ImageBitmap?,
    clockFallback: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalAccentPalette.current
    val style = LocalAppStyle.current
    AppPanel(
        modifier = Modifier
            .fillMaxWidth()
            .height(214.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WaveBars(active = title.isNotBlank())
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.now_playing),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    lyricStatus,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.primary,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
            ) {
                Box(
                    modifier = Modifier
                        .size(126.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.White.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (cover != null) {
                        Image(
                            bitmap = cover,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        title.ifBlank { stringResource(R.string.waiting_playback) },
                        fontSize = 26.sp,
                        fontFamily = style.titleFont,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOf(album, artist).filter { it.isNotBlank() }.joinToString(" · ")
                            .ifBlank { stringResource(R.string.album_artist_placeholder) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.player_label, playerLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.lyric_label, lyricStatus, lyricSource),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (clockFallback) {
                        Text(
                            stringResource(R.string.lyric_clock_fallback),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActions(onPlayers: () -> Unit, onSettings: () -> Unit, onRefresh: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickAction(Icons.Filled.MusicNote, stringResource(R.string.quick_players), onPlayers, Modifier.weight(1f))
        QuickAction(Icons.Filled.Settings, stringResource(R.string.nav_settings), onSettings, Modifier.weight(1f))
        QuickAction(Icons.Filled.Refresh, stringResource(R.string.action_refresh), onRefresh, Modifier.weight(1f))
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalAccentPalette.current
    AppPanel(modifier = modifier.height(54.dp)) {
        Row(
            Modifier
                .fillMaxSize()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = palette.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

@Composable
private fun StatusCard() {
    val palette = LocalAccentPalette.current
    AppPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = palette.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.lsp_ok),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.lsp_connected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.bluetooth_warning_short),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: UniversalLyricHistory.Entry?, index: Int, onClick: () -> Unit) {
    val style = LocalAppStyle.current
    val uiTheme = LocalUiTheme.current
    val shape = when (index) {
        0 -> RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        4 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
        else -> RoundedCornerShape(8.dp)
    }
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(if (uiTheme == UiTheme.GLASS) shape else RoundedCornerShape(0.dp))
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
                fontFamily = style.bodyFont,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            entry?.let {
                Text(
                    text = formatSourceName(LocalContext.current, it.source) + " · " + historyStatusLabel(it.status),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    )
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
