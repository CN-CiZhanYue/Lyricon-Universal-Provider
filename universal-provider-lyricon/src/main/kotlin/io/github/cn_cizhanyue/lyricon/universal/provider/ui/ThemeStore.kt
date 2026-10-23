package io.github.cn_cizhanyue.lyricon.universal.provider.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalSettingsConstants
import java.io.File
import java.io.FileOutputStream

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class UiTheme { GLASS, PAPER, NEON }

enum class Palette { AURORA, EMBER, TIDE, ORCHID, GLACIER, INK, SEPIA, SAGE, COBALT, RUST, ACID, CYBER, VOLT, PLASMA, MATRIX }

fun Palette.uiTheme(): UiTheme = when (this) {
    Palette.AURORA, Palette.EMBER, Palette.TIDE, Palette.ORCHID, Palette.GLACIER -> UiTheme.GLASS
    Palette.INK, Palette.SEPIA, Palette.SAGE, Palette.COBALT, Palette.RUST -> UiTheme.PAPER
    Palette.ACID, Palette.CYBER, Palette.VOLT, Palette.PLASMA, Palette.MATRIX -> UiTheme.NEON
}

fun palettesFor(theme: UiTheme): List<Palette> = Palette.values().filter { it.uiTheme() == theme }

data class ThemeSettings(
    val uiTheme: UiTheme = UiTheme.GLASS,
    val palette: Palette = Palette.AURORA,
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val glass: Boolean = true,
    val customBackground: String? = null,
)

object ThemeStore {
    var settings by mutableStateOf(ThemeSettings())
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(UniversalSettingsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val legacyAccent = prefs.getString(UniversalSettingsConstants.KEY_THEME_ACCENT, null)
        val theme = runCatching {
            UiTheme.valueOf(prefs.getString(UniversalSettingsConstants.KEY_THEME_UI, UiTheme.GLASS.name).orEmpty())
        }.getOrDefault(UiTheme.GLASS)
        val palette = runCatching {
            Palette.valueOf(
                prefs.getString(UniversalSettingsConstants.KEY_THEME_PALETTE, null)
                    ?: when (legacyAccent) {
                        "AURORA" -> Palette.AURORA.name
                        "EMBER" -> Palette.EMBER.name
                        "TIDE" -> Palette.TIDE.name
                        else -> Palette.AURORA.name
                    }
            )
        }.getOrDefault(Palette.AURORA).let { if (it.uiTheme() == theme) it else palettesFor(theme).first() }
        val mode = runCatching {
            ThemeMode.valueOf(prefs.getString(UniversalSettingsConstants.KEY_THEME_MODE, ThemeMode.SYSTEM.name).orEmpty())
        }.getOrDefault(ThemeMode.SYSTEM)
        val custom = prefs.getString(UniversalSettingsConstants.KEY_CUSTOM_BACKGROUND, null)
            ?.takeIf { File(it).isFile }
        settings = ThemeSettings(
            uiTheme = theme,
            palette = palette,
            mode = mode,
            glass = prefs.getBoolean(UniversalSettingsConstants.KEY_GLASS, true),
            customBackground = custom,
        )
    }

    fun update(context: Context, value: ThemeSettings) {
        val normalized = if (value.palette.uiTheme() == value.uiTheme) {
            value
        } else {
            value.copy(palette = palettesFor(value.uiTheme).first())
        }
        settings = normalized
        context.getSharedPreferences(UniversalSettingsConstants.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(UniversalSettingsConstants.KEY_THEME_UI, normalized.uiTheme.name)
            .putString(UniversalSettingsConstants.KEY_THEME_PALETTE, normalized.palette.name)
            .putString(UniversalSettingsConstants.KEY_THEME_MODE, normalized.mode.name)
            .putBoolean(UniversalSettingsConstants.KEY_GLASS, normalized.glass)
            .putString(UniversalSettingsConstants.KEY_CUSTOM_BACKGROUND, normalized.customBackground)
            .apply()
    }
}

object CustomBackgroundStore {
    fun import(context: Context, uri: android.net.Uri, previousPath: String?): String? = runCatching {
        val target = File(context.filesDir, "custom_background_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        val bitmap = decodeSampled(target) ?: run {
            target.delete()
            return null
        }
        FileOutputStream(target).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
        }
        bitmap.recycle()
        if (previousPath != null && previousPath != target.absolutePath) {
            runCatching { File(previousPath).delete() }
        }
        target.absolutePath
    }.getOrNull()

    fun clear() {
        ThemeStore.settings.customBackground?.let { runCatching { File(it).delete() } }
    }

    private fun decodeSampled(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }
}
