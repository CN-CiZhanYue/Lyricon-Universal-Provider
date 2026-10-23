package io.github.cn_cizhanyue.lyricon.universal.provider.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cn_cizhanyue.lyricon.universal.provider.BuildConfig
import io.github.cn_cizhanyue.lyricon.universal.provider.LyricsSourceConfigStore
import io.github.cn_cizhanyue.lyricon.universal.provider.R
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalAppIo
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalSettingsConstants
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalSnapshotStore
import kotlinx.coroutines.withContext
import org.json.JSONObject

private data class ProcessingSettings(
    val translation: Boolean,
    val wordTiming: Boolean,
    val clockLineFallback: Boolean,
    val debug: Boolean,
)

private data class AiSettings(
    val enabled: Boolean,
    val endpoint: String,
    val apiKey: String,
    val model: String,
    val target: String,
)

@Composable
fun SettingsScreen(
    cachedSongsCount: Int,
    onNavigateToPriority: () -> Unit,
    onClearCache: () -> Unit,
) {
    val context = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }
    var translationEnabled by remember { mutableStateOf(true) }
    var wordTimingEnabled by remember { mutableStateOf(true) }
    var clockLineFallbackEnabled by remember { mutableStateOf(true) }
    var debugEnabled by remember { mutableStateOf(false) }
    var networkEnabled by remember { mutableStateOf(true) }
    var aiEnabled by remember { mutableStateOf(false) }
    var aiEndpoint by remember { mutableStateOf("https://api.openai.com/v1") }
    var aiApiKey by remember { mutableStateOf("") }
    var aiModel by remember { mutableStateOf("gpt-4o-mini") }
    var aiTarget by remember { mutableStateOf("中文") }
    var settingsLoaded by remember { mutableStateOf(false) }
    var showOfflineWarning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loaded = withContext(UniversalAppIo.dispatcher) {
            val prefs = context.getSharedPreferences(UniversalSettingsConstants.PREFS_NAME, Context.MODE_PRIVATE)
            val processing = ProcessingSettings(
                prefs.getBoolean(UniversalSettingsConstants.KEY_TRANSLATION, true),
                prefs.getBoolean(UniversalSettingsConstants.KEY_WORD_TIMING, true),
                prefs.getBoolean(UniversalSettingsConstants.KEY_CLOCK_LINE_FALLBACK, true),
                prefs.getBoolean(UniversalSettingsConstants.KEY_DEBUG, false),
            )
            val ai = AiSettings(
                prefs.getBoolean(UniversalSettingsConstants.KEY_AI_ENABLED, false),
                prefs.getString(UniversalSettingsConstants.KEY_AI_ENDPOINT, "https://api.openai.com/v1").orEmpty(),
                prefs.getString(UniversalSettingsConstants.KEY_AI_API_KEY, "").orEmpty(),
                prefs.getString(UniversalSettingsConstants.KEY_AI_MODEL, "gpt-4o-mini").orEmpty(),
                prefs.getString(UniversalSettingsConstants.KEY_AI_TARGET, "中文").orEmpty(),
            )
            Triple(processing, ai, prefs.getBoolean(UniversalSettingsConstants.KEY_NETWORK, true))
        }
        val processing = loaded.first
        translationEnabled = processing.translation
        wordTimingEnabled = processing.wordTiming
        clockLineFallbackEnabled = processing.clockLineFallback
        debugEnabled = processing.debug
        val ai = loaded.second
        aiEnabled = ai.enabled
        aiEndpoint = ai.endpoint
        aiApiKey = ai.apiKey
        aiModel = ai.model
        aiTarget = ai.target
        networkEnabled = loaded.third
        settingsLoaded = true
    }

    fun updateConfig() {
        val appContext = context.applicationContext
        val settings = ProcessingSettings(
            translationEnabled,
            wordTimingEnabled,
            clockLineFallbackEnabled,
            debugEnabled,
        )
        UniversalAppIo.execute {
            appContext.getSharedPreferences(UniversalSettingsConstants.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(UniversalSettingsConstants.KEY_TRANSLATION, settings.translation)
                .putBoolean(UniversalSettingsConstants.KEY_WORD_TIMING, settings.wordTiming)
                .putBoolean(UniversalSettingsConstants.KEY_CLOCK_LINE_FALLBACK, settings.clockLineFallback)
                .putBoolean(UniversalSettingsConstants.KEY_DEBUG, settings.debug)
                .putBoolean(UniversalSettingsConstants.KEY_NETWORK, networkEnabled)
                .apply()
            appContext.sendBroadcast(Intent(UniversalSnapshotStore.ACTION_UPDATE_SOURCE_CONFIG).apply {
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                putExtra(UniversalSnapshotStore.EXTRA_SOURCE_PRIORITY, LyricsSourceConfigStore.encode(LyricsSourceConfigStore.read(appContext)))
                putExtra(UniversalSnapshotStore.EXTRA_WORD_TIMING_ENABLED, settings.wordTiming)
                putExtra(UniversalSnapshotStore.EXTRA_CLOCK_LINE_FALLBACK_ENABLED, settings.clockLineFallback)
                putExtra(UniversalSnapshotStore.EXTRA_TRANSLATION_ENABLED, settings.translation)
                putExtra(UniversalSnapshotStore.EXTRA_DEBUG_ENABLED, settings.debug)
                putExtra(UniversalSnapshotStore.EXTRA_NETWORK_ENABLED, networkEnabled)
            })
        }
    }

    fun saveAiConfig() {
        if (aiEnabled && (aiEndpoint.isBlank() || aiApiKey.isBlank() || aiModel.isBlank())) {
            Toast.makeText(context, context.getString(R.string.settings_ai_missing), Toast.LENGTH_SHORT).show()
            return
        }
        val appContext = context.applicationContext
        val json = JSONObject().apply {
            put("enabled", aiEnabled)
            put("endpoint", aiEndpoint.trim())
            put("apiKey", aiApiKey.trim())
            put("model", aiModel.trim())
            put("target", aiTarget.trim().ifBlank { "中文" })
        }.toString()
        UniversalAppIo.execute {
            appContext.getSharedPreferences(UniversalSettingsConstants.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(UniversalSettingsConstants.KEY_AI_ENABLED, aiEnabled)
                .putString(UniversalSettingsConstants.KEY_AI_ENDPOINT, aiEndpoint.trim())
                .putString(UniversalSettingsConstants.KEY_AI_API_KEY, aiApiKey.trim())
                .putString(UniversalSettingsConstants.KEY_AI_MODEL, aiModel.trim())
                .putString(UniversalSettingsConstants.KEY_AI_TARGET, aiTarget.trim())
                .apply()
            appContext.sendBroadcast(Intent(UniversalSnapshotStore.ACTION_UPDATE_AI_CONFIG).apply {
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                putExtra(UniversalSnapshotStore.EXTRA_AI_CONFIG, json)
            })
        }
        Toast.makeText(context, context.getString(R.string.settings_ai_saved), Toast.LENGTH_SHORT).show()
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

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
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.home_title),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item { AppearanceSection(context) }

            item {
                SettingsSection(stringResource(R.string.section_lyric_processing)) {
                    SettingsSwitchRow(
                        icon = Icons.Filled.Translate,
                        title = stringResource(R.string.inject_translation),
                        description = stringResource(R.string.inject_translation_desc),
                        checked = translationEnabled,
                        enabled = settingsLoaded,
                        onCheckedChange = {
                            translationEnabled = it
                            updateConfig()
                        },
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Filled.GraphicEq,
                        title = stringResource(R.string.prefer_word_timing),
                        description = stringResource(R.string.prefer_word_timing_desc),
                        checked = wordTimingEnabled,
                        enabled = settingsLoaded,
                        onCheckedChange = {
                            wordTimingEnabled = it
                            updateConfig()
                        },
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Filled.Timer,
                        title = stringResource(R.string.clock_line_fallback),
                        description = stringResource(R.string.clock_line_fallback_desc),
                        checked = clockLineFallbackEnabled,
                        enabled = settingsLoaded && wordTimingEnabled,
                        onCheckedChange = {
                            clockLineFallbackEnabled = it
                            updateConfig()
                        },
                    )
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_section_ai)) {
                    SettingsSwitchRow(
                        icon = Icons.Filled.Cloud,
                        title = stringResource(R.string.settings_ai_enable),
                        description = stringResource(R.string.settings_ai_enable_desc),
                        checked = aiEnabled,
                        enabled = settingsLoaded,
                        onCheckedChange = { aiEnabled = it },
                    )
                    Column(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = aiEndpoint,
                            onValueChange = { aiEndpoint = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.settings_ai_endpoint)) },
                            placeholder = { Text(stringResource(R.string.settings_ai_endpoint_hint)) },
                            singleLine = true,
                            enabled = settingsLoaded,
                            shape = RoundedCornerShape(14.dp),
                        )
                        OutlinedTextField(
                            value = aiApiKey,
                            onValueChange = { aiApiKey = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.settings_ai_key)) },
                            singleLine = true,
                            enabled = settingsLoaded,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(14.dp),
                        )
                        OutlinedTextField(
                            value = aiModel,
                            onValueChange = { aiModel = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.settings_ai_model)) },
                            singleLine = true,
                            enabled = settingsLoaded,
                            shape = RoundedCornerShape(14.dp),
                        )
                        OutlinedTextField(
                            value = aiTarget,
                            onValueChange = { aiTarget = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.settings_ai_target)) },
                            singleLine = true,
                            enabled = settingsLoaded,
                            shape = RoundedCornerShape(14.dp),
                        )
                        Text(
                            stringResource(R.string.settings_ai_note),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { saveAiConfig() },
                            enabled = settingsLoaded,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(stringResource(R.string.settings_ai_save), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item {
                SettingsSection(stringResource(R.string.section_network)) {
                    SettingsSwitchRow(
                        icon = Icons.Filled.Wifi,
                        title = stringResource(R.string.settings_network_title),
                        description = stringResource(R.string.settings_network_desc),
                        checked = networkEnabled,
                        enabled = settingsLoaded,
                        onCheckedChange = { checked ->
                            if (!checked && !officialProviderInstalled(context)) {
                                showOfflineWarning = true
                            } else {
                                networkEnabled = checked
                                updateConfig()
                            }
                        },
                    )
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_section_sources)) {
                    SettingsActionRow(
                        icon = Icons.Filled.Menu,
                        title = stringResource(R.string.section_source_priority),
                        description = stringResource(R.string.drag_to_reorder),
                        onClick = onNavigateToPriority,
                    )
                    SettingsDivider()
                    SettingsInfoRow(
                        icon = Icons.Filled.Storage,
                        title = stringResource(R.string.lyric_cache),
                        value = stringResource(R.string.cached_songs, cachedSongsCount),
                        action = {
                            TextButton(onClick = onClearCache) {
                                Text(stringResource(R.string.clear_cache, cachedSongsCount))
                            }
                        },
                    )
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_section_about)) {
                    SettingsInfoRow(
                        icon = Icons.Filled.Info,
                        title = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        value = "",
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = Icons.Filled.Info,
                        title = stringResource(R.string.settings_about_row),
                        description = stringResource(R.string.about_project),
                        onClick = { showAbout = true },
                    )
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_section_debug)) {
                    SettingsSwitchRow(
                        icon = Icons.Filled.BugReport,
                        title = stringResource(R.string.logcat),
                        description = stringResource(R.string.logcat_desc),
                        checked = debugEnabled,
                        enabled = settingsLoaded,
                        onCheckedChange = {
                            debugEnabled = it
                            updateConfig()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearanceSection(context: Context) {
    val settings = ThemeStore.settings
    val modeLabels = mapOf(
        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
        ThemeMode.LIGHT to stringResource(R.string.theme_light),
        ThemeMode.DARK to stringResource(R.string.theme_dark),
    )
    val themeLabels = mapOf(
        UiTheme.GLASS to stringResource(R.string.ui_glass),
        UiTheme.PAPER to stringResource(R.string.ui_paper),
        UiTheme.NEON to stringResource(R.string.ui_neon),
    )
    val themeDescriptions = mapOf(
        UiTheme.GLASS to stringResource(R.string.ui_glass_desc),
        UiTheme.PAPER to stringResource(R.string.ui_paper_desc),
        UiTheme.NEON to stringResource(R.string.ui_neon_desc),
    )
    val paletteLabels = mapOf(
        Palette.AURORA to stringResource(R.string.palette_aurora),
        Palette.EMBER to stringResource(R.string.palette_ember),
        Palette.TIDE to stringResource(R.string.palette_tide),
        Palette.ORCHID to stringResource(R.string.palette_orchid),
        Palette.GLACIER to stringResource(R.string.palette_glacier),
        Palette.INK to stringResource(R.string.palette_ink),
        Palette.SEPIA to stringResource(R.string.palette_sepia),
        Palette.SAGE to stringResource(R.string.palette_sage),
        Palette.COBALT to stringResource(R.string.palette_cobalt),
        Palette.RUST to stringResource(R.string.palette_rust),
        Palette.ACID to stringResource(R.string.palette_acid),
        Palette.CYBER to stringResource(R.string.palette_cyber),
        Palette.VOLT to stringResource(R.string.palette_volt),
        Palette.PLASMA to stringResource(R.string.palette_plasma),
        Palette.MATRIX to stringResource(R.string.palette_matrix),
    )
    SettingsSection(stringResource(R.string.theme_section)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.theme_ui), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UiTheme.values().forEach { theme ->
                    FilterChip(
                        selected = settings.uiTheme == theme,
                        onClick = {
                            ThemeStore.update(
                                context,
                                settings.copy(uiTheme = theme, palette = palettesFor(theme).first()),
                            )
                        },
                        label = { Text(themeLabels.getValue(theme)) },
                    )
                }
            }
            Text(
                themeDescriptions.getValue(settings.uiTheme),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.theme_palette),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                palettesFor(settings.uiTheme).forEach { palette ->
                    val selected = settings.palette == palette
                    val spec = specOf(palette)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(spec.primaryDark, spec.primaryLight)))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                )
                                .clickable { ThemeStore.update(context, settings.copy(palette = palette)) },
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            paletteLabels.getValue(palette),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.theme_mode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.values().forEach { mode ->
                    FilterChip(
                        selected = settings.mode == mode,
                        enabled = settings.uiTheme != UiTheme.NEON,
                        onClick = { ThemeStore.update(context, settings.copy(mode = mode)) },
                        label = { Text(modeLabels.getValue(mode)) },
                    )
                }
            }
            if (settings.uiTheme == UiTheme.NEON) {
                Text(
                    stringResource(R.string.theme_neon_dark_only),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.theme_custom_bg),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.theme_custom_bg_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(context, io.github.cn_cizhanyue.lyricon.universal.provider.CustomBackgroundActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                ) {
                    Text(stringResource(R.string.theme_custom_bg_pick))
                }
                if (settings.customBackground != null) {
                    TextButton(
                        onClick = {
                            CustomBackgroundStore.clear()
                            ThemeStore.update(context, ThemeStore.settings.copy(customBackground = null))
                        },
                    ) {
                        Text(stringResource(R.string.theme_custom_bg_clear))
                    }
                }
            }
        }
        if (settings.uiTheme == UiTheme.GLASS) {
            SettingsDivider()
            SettingsSwitchRow(
                icon = Icons.Filled.AutoAwesome,
                title = stringResource(R.string.theme_glass),
                description = stringResource(R.string.theme_glass_desc),
                checked = settings.glass,
                onCheckedChange = { ThemeStore.update(context, settings.copy(glass = it)) },
            )
        }
    }
}

private fun officialProviderInstalled(context: Context): Boolean =
    runCatching {
        context.packageManager.getPackageInfo(
            "io.github.andrealtb.coloroslyrics.provider.universal",
            0,
        )
    }.isSuccess
