package io.github.cn_cizhanyue.lyricon.universal.provider.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cn_cizhanyue.lyricon.universal.provider.NoLyricPolicy
import io.github.cn_cizhanyue.lyricon.universal.provider.R
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalSnapshotStore
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalAppNetwork
import io.github.cn_cizhanyue.lyricon.universal.provider.api.LyricsSourceAggregator
import io.github.cn_cizhanyue.lyricon.universal.provider.api.SearchCandidate
import io.github.cn_cizhanyue.lyricon.universal.provider.cache.UniversalLyricCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

class SearchUiState {
    var query by mutableStateOf("")
    var selectedFilter by mutableStateOf<String?>(null)
    var candidates by mutableStateOf<List<SearchCandidate>>(emptyList())
    var statusText by mutableStateOf("")
    var isSearching by mutableStateOf(false)
    var seeded by mutableStateOf(false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    initialQuery: String,
    currentPkg: String,
    currentTitle: String,
    currentArtist: String,
    currentGen: Long,
    onLyricBound: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun doSearch(kw: String) {
        if (kw.isBlank()) return
        state.isSearching = true
        state.statusText = context.getString(R.string.searching_sources)
        scope.launch(Dispatchers.IO) {
            UniversalAppNetwork.initialize(context)
            val aggregator = LyricsSourceAggregator.shared
            val qq = aggregator.qqSource.searchCandidates(kw)
            val netease = aggregator.netEaseSource.searchCandidates(kw)
            val apple = aggregator.appleMusicSource.searchCandidates(kw)
            val combined = qq + netease + apple
            withContext(Dispatchers.Main) {
                state.candidates = combined
                state.statusText = context.getString(R.string.search_results, combined.size)
                state.isSearching = false
            }
        }
    }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) state.query = initialQuery
    }

    LaunchedEffect(Unit) {
        if (!state.seeded) {
            state.seeded = true
            if (state.query.isBlank()) {
                state.query = initialQuery.ifBlank {
                    listOf(currentTitle, currentArtist).filter { it.isNotBlank() }.joinToString(" ")
                }
            }
        }
    }

    val displayedCandidates = remember(state.candidates, state.selectedFilter) {
        if (state.selectedFilter == null) state.candidates
        else state.candidates.filter { it.source == state.selectedFilter }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        TopAppBar(
            title = {
                Text(
                    stringResource(
                        R.string.search_now_playing,
                        currentTitle.ifBlank { stringResource(R.string.track_placeholder) },
                        currentArtist.ifBlank { stringResource(R.string.artist_placeholder) }
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null)
                }
            },
            windowInsets = WindowInsets.statusBars,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { state.query = it },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(28.dp),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { state.query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                },
                placeholder = { Text(stringResource(R.string.search_songs_hint)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = { doSearch(state.query) },
                modifier = Modifier.height(56.dp)
            ) {
                Text(stringResource(R.string.action_search), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 来源 Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                stringResource(R.string.filter_all) to null,
                stringResource(R.string.source_qq) to "QQ音乐",
                stringResource(R.string.source_netease) to "网易云音乐",
                stringResource(R.string.source_apple) to "Apple Music"
            ).forEach { (label, src) ->
                val isSelected = state.selectedFilter == src
                FilterChip(
                    selected = isSelected,
                    onClick = { state.selectedFilter = src },
                    label = { Text(label) },
                    leadingIcon = {
                        if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(state.statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = {
                val json = NoLyricPolicy.payloadJson(currentTitle, currentArtist, NoLyricPolicy.REASON_USER)
                UniversalLyricCache.putManualBinding(context, currentTitle, currentArtist, json)
                val intent = Intent(UniversalSnapshotStore.ACTION_INJECT_REAL_LYRIC).apply {
                    putExtra(UniversalSnapshotStore.EXTRA_OWNER_PACKAGE, currentPkg)
                    putExtra(UniversalSnapshotStore.EXTRA_GENERATION, currentGen)
                    putExtra(UniversalSnapshotStore.EXTRA_LYRIC_INFO, json)
                    putExtra("extra_is_manual", true)
                    putExtra("extra_track_key", "${currentTitle.trim()}|${currentArtist.trim()}".lowercase())
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    addFlags(0x01000000)
                }
                context.sendBroadcast(intent)
                Toast.makeText(context, context.getString(R.string.toast_no_lyric), Toast.LENGTH_SHORT).show()
                onLyricBound()
            }) {
                Text(stringResource(R.string.confirm_no_lyric))
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(displayedCandidates, key = { it.source + it.id }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clickable {
                            scope.launch(Dispatchers.IO) {
                                UniversalAppNetwork.initialize(context)
                                val aggregator = LyricsSourceAggregator.shared
                                val source = when (item.source) {
                                    "QQ音乐" -> aggregator.qqSource
                                    "Apple Music" -> aggregator.appleMusicSource
                                    else -> aggregator.netEaseSource
                                }
                                val result = source.fetchLyricByCandidate(item)
                                if (result != null && result.lyric.isNotBlank()) {
                                    val json = JSONObject().apply {
                                        put("songName", currentTitle.ifBlank { result.title })
                                        put("artist", currentArtist.ifBlank { result.artist })
                                        put("album", item.album)
                                        put("songId", "universal-$currentGen")
                                        put("lyricType", 0)
                                        put("id", "")
                                        put("noLyric", false)
                                        put("lyric", result.lyric)
                                        put("rawLyric", result.rawLyric ?: result.lyric)
                                        if (!result.translationLyric.isNullOrBlank()) {
                                            put("transLyric", result.translationLyric)
                                            put("translationLyric", result.translationLyric)
                                            put("translationLanguageTag", Locale.getDefault().toLanguageTag())
                                        }
                                        put("provider", "io.github.cn_cizhanyue.lyricon.universal.provider")
                                        put("source", result.source + "/manual")
                                        put("sessionGeneration", currentGen)
                                        put("remark", "[CLLUniversal]")
                                    }.toString()

                                    UniversalLyricCache.putManualBinding(context, currentTitle, currentArtist, json)
                                    val intent = Intent(UniversalSnapshotStore.ACTION_INJECT_REAL_LYRIC).apply {
                                        putExtra(UniversalSnapshotStore.EXTRA_OWNER_PACKAGE, currentPkg)
                                        putExtra(UniversalSnapshotStore.EXTRA_GENERATION, currentGen)
                                        putExtra(UniversalSnapshotStore.EXTRA_LYRIC_INFO, json)
                                        putExtra("extra_is_manual", true)
                                        putExtra("extra_track_key", "${currentTitle.trim()}|${currentArtist.trim()}".lowercase())
                                        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                                        addFlags(0x01000000)
                                    }
                                    context.sendBroadcast(intent)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, context.getString(R.string.toast_lyric_bound), Toast.LENGTH_SHORT).show()
                                        onLyricBound()
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, context.getString(R.string.toast_lyric_failed), Toast.LENGTH_SHORT).show()
                                    }
                                }
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
                            .padding(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(130.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                        ) {
                            CoverArt(url = item.coverUrl)
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(item.source, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${item.artist} · ${item.album}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    formatDuration(item.durationMs),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverArt(url: String) {
    val context = LocalContext.current
    var bitmap by remember(url) { mutableStateOf(CoverArtCache.peek(url)) }
    LaunchedEffect(url) {
        if (bitmap == null && url.isNotBlank()) {
            bitmap = CoverArtCache.load(context, url)
        }
    }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
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

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "--:--"
    val totalSec = durationMs / 1000L
    return "%d:%02d".format(totalSec / 60L, totalSec % 60L)
}
