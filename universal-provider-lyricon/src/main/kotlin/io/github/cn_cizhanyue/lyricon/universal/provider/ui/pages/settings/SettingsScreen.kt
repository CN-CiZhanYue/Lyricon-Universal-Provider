package io.github.cn_cizhanyue.lyricon.universal.provider.ui.pages.settings
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.cn_cizhanyue.lyricon.universal.provider.BuildConfig
import io.github.cn_cizhanyue.lyricon.universal.provider.R
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.CustomBackgroundStore
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.Palette
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.ThemeMode
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.UiTheme
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.controller.SettingsController
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.controller.SettingsToggle
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.controller.ThemeController
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.AppDimens
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.LocalAppDesign
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.components.AboutDialog
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.glass.GlassSurface
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.palettesFor
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.specOf
@Composable
fun SettingsScreen(
    settingsController: SettingsController,
    themeController: ThemeController,
    cached: Int,
    onClearCache: () -> Unit,
    onPriority: () -> Unit,
    onLanguage: (String) -> Unit,
) {
    val context = LocalContext.current
    val design = LocalAppDesign.current
    val settings by settingsController.state.collectAsState()
    val theme by themeController.state.collectAsState()
    var showAbout by remember { mutableStateOf(false) }
    val themeLabels = mapOf(
        UiTheme.GLASS to stringResource(R.string.ui_glass),
        UiTheme.PAPER to stringResource(R.string.ui_paper),
        UiTheme.NEON to stringResource(R.string.ui_neon),
        UiTheme.CLASSIC to stringResource(R.string.ui_classic),
    )
    val modeLabels = mapOf(
        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
        ThemeMode.LIGHT to stringResource(R.string.theme_light),
        ThemeMode.DARK to stringResource(R.string.theme_dark),
    )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = AppDimens.pagePadding, end = AppDimens.pagePadding, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SettingsGroup(stringResource(R.string.theme_section)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UiTheme.values().filter { it != UiTheme.MIUIX }.forEach { item ->
                        Choice(themeLabels.getValue(item), theme.uiTheme == item, Modifier.weight(1f)) {
                            themeController.update(theme.copy(uiTheme = item, palette = palettesFor(item).first()))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    val palettes = palettesFor(theme.uiTheme)
                    palettes.forEach { palette ->
                        val spec = specOf(palette)
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(spec.primaryDark, spec.primaryLight)))
                                .border(if (theme.palette == palette) 2.dp else 1.dp, if (theme.palette == palette) design.colors.primary else design.colors.outline, CircleShape)
                                .clickable { themeController.update(theme.copy(palette = palette)) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.values().forEach { mode ->
                        Choice(modeLabels.getValue(mode), theme.mode == mode, Modifier.weight(1f)) {
                            themeController.update(theme.copy(mode = mode))
                        }
                    }
                }
                ToggleRow(stringResource(R.string.glass_material), theme.effect == io.github.cn_cizhanyue.lyricon.universal.provider.ui.GlassEffect.TRANSLUCENT) {
                    themeController.update(theme.copy(effect = if (it) io.github.cn_cizhanyue.lyricon.universal.provider.ui.GlassEffect.TRANSLUCENT else io.github.cn_cizhanyue.lyricon.universal.provider.ui.GlassEffect.OFF))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Filled.Image, null, tint = design.colors.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.theme_custom_bg_desc), style = design.typography.caption, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(context, io.github.cn_cizhanyue.lyricon.universal.provider.CustomBackgroundActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) { Text(stringResource(R.string.theme_custom_bg_pick)) }
                    if (theme.customBackground != null) {
                        TextButton(onClick = {
                            CustomBackgroundStore.clear()
                            themeController.update(theme.copy(customBackground = null))
                        }) { Text(stringResource(R.string.theme_custom_bg_clear)) }
                    }
                }
            }
        }
        item {
            SettingsGroup(stringResource(R.string.section_lyric_processing)) {
                ToggleRow(stringResource(R.string.settings_network_title), settings.network) { settingsController.setToggle(SettingsToggle.NETWORK, it) }
                HorizontalDivider()
                ToggleRow(stringResource(R.string.inject_translation), settings.translation) { settingsController.setToggle(SettingsToggle.TRANSLATION, it) }
                HorizontalDivider()
                ToggleRow(stringResource(R.string.prefer_word_timing), settings.wordTiming) { settingsController.setToggle(SettingsToggle.WORD_TIMING, it) }
                AnimatedVisibility(
                    visible = settings.wordTiming,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column {
                        HorizontalDivider()
                        ToggleRow(stringResource(R.string.clock_line_fallback), settings.clockFallback) {
                            settingsController.setToggle(SettingsToggle.CLOCK_FALLBACK, it)
                        }
                    }
                }
                HorizontalDivider()
                ToggleRow(stringResource(R.string.settings_section_debug), settings.debug) { settingsController.setToggle(SettingsToggle.DEBUG, it) }
            }
        }
        item {
            SettingsGroup(stringResource(R.string.settings_section_ai)) {
                ToggleRow(stringResource(R.string.settings_ai_enable), settings.aiEnabled) {
                    settingsController.updateAi { state -> state.copy(aiEnabled = it) }
                }
                AnimatedVisibility(
                    visible = settings.aiEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column {
                        TextFieldRow(stringResource(R.string.settings_ai_endpoint), settings.aiEndpoint) {
                    settingsController.updateAi { state -> state.copy(aiEndpoint = it) }
                }
                TextFieldRow(stringResource(R.string.settings_ai_key), settings.aiKey, password = true) {
                    settingsController.updateAi { state -> state.copy(aiKey = it) }
                }
                TextFieldRow(stringResource(R.string.settings_ai_model), settings.aiModel) {
                    settingsController.updateAi { state -> state.copy(aiModel = it) }
                }
                        TextFieldRow(stringResource(R.string.settings_ai_target), settings.aiTarget) {
                            settingsController.updateAi { state -> state.copy(aiTarget = it) }
                        }
                    }
                }
                Button(
                    onClick = {
                        if (!settingsController.saveAi()) {
                            Toast.makeText(context, context.getString(R.string.settings_ai_missing), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) { Text(stringResource(R.string.settings_ai_save)) }
            }
        }
        item {
            SettingsGroup(stringResource(R.string.section_local_data)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.lyric_cache), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.cached_songs, cached), style = design.typography.caption)
                    }
                    TextButton(onClick = onClearCache) { Text(stringResource(R.string.clear_cache, cached)) }
                }
                HorizontalDivider()
                LinkRow(stringResource(R.string.priority_title), onPriority)
            }
        }
        item {
            SettingsGroup(stringResource(R.string.action_language)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onLanguage("zh") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                        Text(stringResource(R.string.language_zh))
                    }
                    Button(onClick = { onLanguage("en") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                        Text(stringResource(R.string.language_en))
                    }
                }
            }
        }
        item {
            GlassSurface(Modifier.clickable { showAbout = true }) {
                Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), style = design.typography.caption)
            }
        }
    }
    AboutDialog(show = showAbout, onDismiss = { showAbout = false })
}
