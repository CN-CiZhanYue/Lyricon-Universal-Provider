package io.github.cn_cizhanyue.lyricon.universal.provider.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalSettingsConstants
import java.util.Locale

object LanguageStore {
    const val ZH = "zh"
    const val EN = "en"

    fun read(context: Context): String {
        val prefs = context.getSharedPreferences(UniversalSettingsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(UniversalSettingsConstants.KEY_LANGUAGE, ZH) ?: ZH
    }

    fun write(context: Context, language: String) {
        context.getSharedPreferences(UniversalSettingsConstants.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(UniversalSettingsConstants.KEY_LANGUAGE, language)
            .apply()
    }
}

@Composable
fun ProvideAppLocale(language: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val localized = remember(language, context) {
        val config = Configuration(context.resources.configuration)
        config.setLocale(if (language == LanguageStore.EN) Locale.ENGLISH else Locale.SIMPLIFIED_CHINESE)
        context.createConfigurationContext(config)
    }
    CompositionLocalProvider(LocalContext provides localized, content = content)
}
