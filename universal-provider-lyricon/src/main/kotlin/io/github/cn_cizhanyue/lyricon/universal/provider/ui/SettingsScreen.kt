package io.github.cn_cizhanyue.lyricon.universal.provider.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cn_cizhanyue.lyricon.universal.provider.LyricsSourceConfigStore
import io.github.cn_cizhanyue.lyricon.universal.provider.R
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalSettingsConstants
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalSnapshotStore
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalAppIo
import kotlinx.coroutines.withContext

private data class ProcessingSettings(
    val translation: Boolean,
    val wordTiming: Boolean,
    val clockLineFallback: Boolean,
    val rawLyric: Boolean,
    val debug: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    cachedSongsCount: Int,
    onNavigateToPriority: () -> Unit,
    onClearCache: () -> Unit
) {
    val context = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }
    var translationEnabled by remember { mutableStateOf(true) }
    var wordTimingEnabled by remember { mutableStateOf(false) }
    var clockLineFallbackEnabled by remember { mutableStateOf(true) }
    var rawLyricEnabled by remember { mutableStateOf(true) }
    var debugEnabled by remember { mutableStateOf(false) }
    var settingsLoaded by remember { mutableStateOf(false) }
    var networkEnabled by remember { mutableStateOf(true) }
    var showOfflineWarning by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val loaded = withContext(UniversalAppIo.dispatcher) {
            val prefs = context.getSharedPreferences(UniversalSettingsConstants.PREFS_NAME, Context.MODE_PRIVATE)
            ProcessingSettings(
                prefs.getBoolean(UniversalSettingsConstants.KEY_TRANSLATION, true),
                prefs.getBoolean(UniversalSettingsConstants.KEY_WORD_TIMING, true),
                prefs.getBoolean(UniversalSettingsConstants.KEY_CLOCK_LINE_FALLBACK, true),
                prefs.getBoolean(UniversalSettingsConstants.KEY_RAW_LYRIC, true),
                prefs.getBoolean(UniversalSettingsConstants.KEY_DEBUG, false)
            ) to prefs.getBoolean(UniversalSettingsConstants.KEY_NETWORK, true)
        }
        val settings = loaded.first
        networkEnabled = loaded.second
        translationEnabled = settings.translation
        wordTimingEnabled = settings.wordTiming
        clockLineFallbackEnabled = settings.clockLineFallback
        rawLyricEnabled = settings.rawLyric
        debugEnabled = settings.debug
        settingsLoaded = true
    }

    fun updateConfig() {
        val settings = ProcessingSettings(
            translationEnabled,
            wordTimingEnabled,
            clockLineFallbackEnabled,
            rawLyricEnabled,
            debugEnabled
        )
        val appContext = context.applicationContext
        UniversalAppIo.execute {
            appContext.getSharedPreferences(UniversalSettingsConstants.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(UniversalSettingsConstants.KEY_TRANSLATION, settings.translation)
                .putBoolean(UniversalSettingsConstants.KEY_WORD_TIMING, settings.wordTiming)
                .putBoolean(UniversalSettingsConstants.KEY_CLOCK_LINE_FALLBACK, settings.clockLineFallback)
                .putBoolean(UniversalSettingsConstants.KEY_RAW_LYRIC, settings.rawLyric)
                .putBoolean(UniversalSettingsConstants.KEY_DEBUG, settings.debug)
                .putBoolean(UniversalSettingsConstants.KEY_NETWORK, networkEnabled)
                .apply()
            appContext.sendBroadcast(Intent(UniversalSnapshotStore.ACTION_UPDATE_SOURCE_CONFIG).apply {
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                putExtra(UniversalSnapshotStore.EXTRA_SOURCE_PRIORITY, LyricsSourceConfigStore.encode(LyricsSourceConfigStore.read(appContext)))
                putExtra(UniversalSnapshotStore.EXTRA_WORD_TIMING_ENABLED, settings.wordTiming)
                putExtra(UniversalSnapshotStore.EXTRA_CLOCK_LINE_FALLBACK_ENABLED, settings.clockLineFallback)
                putExtra(UniversalSnapshotStore.EXTRA_TRANSLATION_ENABLED, settings.translation)
                putExtra(UniversalSnapshotStore.EXTRA_RAW_LYRIC_ENABLED, settings.rawLyric)
                putExtra(UniversalSnapshotStore.EXTRA_DEBUG_ENABLED, settings.debug)
                putExtra(UniversalSnapshotStore.EXTRA_NETWORK_ENABLED, networkEnabled)
            })
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        TopAppBar(
            title = {
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            },
            navigationIcon = {
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Settings, contentDescription = null)
                }
            },
            actions = {
                IconButton(onClick = { showAbout = true }) {
                    Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.about_project))
                }
            },
            windowInsets = WindowInsets.statusBars,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        if (showAbout) {
            AboutDialog(onDismiss = { showAbout = false })
        }

        Text(stringResource(R.string.section_source_priority), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))

        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onNavigateToPriority),
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            leadingContent = { Icon(Icons.Filled.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            headlineContent = { Text(stringResource(R.string.section_source_priority), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) },
            trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) }
        )

        Text(stringResource(R.string.section_network), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))

        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clip(RoundedCornerShape(20.dp)),
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            leadingContent = { Icon(Icons.Filled.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            headlineContent = { Text(stringResource(R.string.settings_network_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) },
            supportingContent = { Text(stringResource(R.string.settings_network_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingContent = {
                Switch(
                    checked = networkEnabled,
                    onCheckedChange = { checked ->
                        if (!checked && !officialProviderInstalled(context)) {
                            showOfflineWarning = true
                        } else {
                            networkEnabled = checked
                            updateConfig()
                        }
                    }
                )
            }
        )

        if (showOfflineWarning) {
            AlertDialog(
                onDismissRequest = { showOfflineWarning = false },
                title = { Text(stringResource(R.string.network_warning_title)) },
                text = { Text(stringResource(R.string.network_warning_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showOfflineWarning = false
                        networkEnabled = false
                        updateConfig()
                    }) { Text(stringResource(R.string.network_warning_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showOfflineWarning = false }) {
                        Text(stringResource(R.string.network_warning_cancel))
                    }
                }
            )
        }

        Text(stringResource(R.string.section_lyric_processing), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp)),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                leadingContent = { Icon(Icons.Filled.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                headlineContent = { Text(stringResource(R.string.inject_translation), style = MaterialTheme.typography.bodyLarge) },
                supportingContent = { Text(stringResource(R.string.inject_translation_desc), style = MaterialTheme.typography.bodySmall) },
                trailingContent = {
                    Switch(
                        checked = translationEnabled,
                        enabled = settingsLoaded,
                        onCheckedChange = {
                            translationEnabled = it
                            updateConfig()
                        }
                    )
                }
            )

            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                leadingContent = { Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                headlineContent = { Text(stringResource(R.string.prefer_word_timing), style = MaterialTheme.typography.bodyLarge) },
                supportingContent = { Text(stringResource(R.string.prefer_word_timing_desc), style = MaterialTheme.typography.bodySmall) },
                trailingContent = {
                    Switch(
                        checked = wordTimingEnabled,
                        enabled = settingsLoaded,
                        onCheckedChange = {
                            wordTimingEnabled = it
                            updateConfig()
                        }
                    )
                }
            )

            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                leadingContent = { Icon(Icons.Filled.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                headlineContent = { Text(stringResource(R.string.clock_line_fallback), style = MaterialTheme.typography.bodyLarge) },
                supportingContent = { Text(stringResource(R.string.clock_line_fallback_desc), style = MaterialTheme.typography.bodySmall) },
                trailingContent = {
                    Switch(
                        checked = clockLineFallbackEnabled,
                        // Only word timing can fall back; line timing is already published otherwise.
                        enabled = settingsLoaded && wordTimingEnabled,
                        onCheckedChange = {
                            clockLineFallbackEnabled = it
                            updateConfig()
                        }
                    )
                }
            )

            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 28.dp, bottomEnd = 28.dp)),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                leadingContent = { Icon(Icons.Filled.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                headlineContent = { Text(stringResource(R.string.allow_raw_lyric), style = MaterialTheme.typography.bodyLarge) },
                supportingContent = { Text(stringResource(R.string.allow_raw_lyric_desc), style = MaterialTheme.typography.bodySmall) },
                trailingContent = {
                    Switch(
                        checked = rawLyricEnabled,
                        enabled = settingsLoaded,
                        onCheckedChange = {
                            rawLyricEnabled = it
                            updateConfig()
                        }
                    )
                }
            )
        }

        Text(stringResource(R.string.section_local_data), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))

        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clip(RoundedCornerShape(20.dp)),
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            leadingContent = { Icon(Icons.Filled.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            headlineContent = { Text(stringResource(R.string.lyric_cache), style = MaterialTheme.typography.bodyLarge) },
            supportingContent = { Text(stringResource(R.string.cached_songs, cachedSongsCount), style = MaterialTheme.typography.bodySmall) },
            trailingContent = {
                IconButton(onClick = {
                    onClearCache()
                    Toast.makeText(context, context.getString(R.string.toast_cache_cleared), Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Filled.Delete, contentDescription = "清理缓存")
                }
            }
        )

        Text(stringResource(R.string.section_logs), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))

        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clip(RoundedCornerShape(20.dp)),
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            leadingContent = { Icon(Icons.Filled.Construction, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            headlineContent = { Text(stringResource(R.string.logcat), style = MaterialTheme.typography.bodyLarge) },
            supportingContent = { Text(stringResource(R.string.logcat_desc), style = MaterialTheme.typography.bodySmall) },
            trailingContent = {
                Switch(
                    checked = debugEnabled,
                    enabled = settingsLoaded,
                    onCheckedChange = {
                        debugEnabled = it
                        updateConfig()
                    }
                )
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}


private fun officialProviderInstalled(context: Context): Boolean =
    runCatching {
        context.packageManager.getPackageInfo(
            "io.github.cn_cizhanyue.lyricon.universal.provider",
            0,
        )
    }.isSuccess
