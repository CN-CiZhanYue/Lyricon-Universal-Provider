package io.github.cn_cizhanyue.lyricon.universal.provider.ui.controller

import android.content.Context
import android.content.Intent
import io.github.cn_cizhanyue.lyricon.universal.provider.LyricsSourceConfigStore
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalAppIo
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalSettingsConstants
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalSnapshotStore
import org.json.JSONObject

internal object SourceConfigBroadcaster {

    fun sendProcessing(context: Context, state: SettingsUiState) {
        UniversalAppIo.execute {
            context.getSharedPreferences(UniversalSettingsConstants.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(UniversalSettingsConstants.KEY_TRANSLATION, state.translation)
                .putBoolean(UniversalSettingsConstants.KEY_WORD_TIMING, state.wordTiming)
                .putBoolean(UniversalSettingsConstants.KEY_CLOCK_LINE_FALLBACK, state.clockFallback)
                .putBoolean(UniversalSettingsConstants.KEY_DEBUG, state.debug)
                .putBoolean(UniversalSettingsConstants.KEY_NETWORK, state.network)
                .apply()
            context.sendBroadcast(Intent(UniversalSnapshotStore.ACTION_UPDATE_SOURCE_CONFIG).apply {
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                putExtra(UniversalSnapshotStore.EXTRA_SOURCE_PRIORITY, LyricsSourceConfigStore.encode(LyricsSourceConfigStore.read(context)))
                putExtra(UniversalSnapshotStore.EXTRA_WORD_TIMING_ENABLED, state.wordTiming)
                putExtra(UniversalSnapshotStore.EXTRA_CLOCK_LINE_FALLBACK_ENABLED, state.clockFallback && state.wordTiming)
                putExtra(UniversalSnapshotStore.EXTRA_TRANSLATION_ENABLED, state.translation)
                putExtra(UniversalSnapshotStore.EXTRA_DEBUG_ENABLED, state.debug)
                putExtra(UniversalSnapshotStore.EXTRA_NETWORK_ENABLED, state.network)
            })
        }
    }

    fun sendAi(context: Context, state: SettingsUiState) {
        val json = JSONObject().apply {
            put("enabled", state.aiEnabled)
            put("endpoint", state.aiEndpoint.trim())
            put("apiKey", state.aiKey.trim())
            put("model", state.aiModel.trim())
            put("target", state.aiTarget.trim())
        }.toString()
        UniversalAppIo.execute {
            context.getSharedPreferences(UniversalSettingsConstants.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(UniversalSettingsConstants.KEY_AI_ENABLED, state.aiEnabled)
                .putString(UniversalSettingsConstants.KEY_AI_ENDPOINT, state.aiEndpoint.trim())
                .putString(UniversalSettingsConstants.KEY_AI_API_KEY, state.aiKey.trim())
                .putString(UniversalSettingsConstants.KEY_AI_MODEL, state.aiModel.trim())
                .putString(UniversalSettingsConstants.KEY_AI_TARGET, state.aiTarget.trim())
                .apply()
            context.sendBroadcast(Intent(UniversalSnapshotStore.ACTION_UPDATE_AI_CONFIG).apply {
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                putExtra(UniversalSnapshotStore.EXTRA_AI_CONFIG, json)
            })
        }
    }

    fun savePriority(context: Context, priority: List<String>, state: SettingsUiState) {
        UniversalAppIo.execute {
            LyricsSourceConfigStore.write(context, priority)
            context.sendBroadcast(Intent(UniversalSnapshotStore.ACTION_UPDATE_SOURCE_CONFIG).apply {
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                putExtra(UniversalSnapshotStore.EXTRA_SOURCE_PRIORITY, LyricsSourceConfigStore.encode(priority))
                putExtra(UniversalSnapshotStore.EXTRA_WORD_TIMING_ENABLED, state.wordTiming)
                putExtra(UniversalSnapshotStore.EXTRA_TRANSLATION_ENABLED, state.translation)
                putExtra(UniversalSnapshotStore.EXTRA_DEBUG_ENABLED, state.debug)
                putExtra(UniversalSnapshotStore.EXTRA_CLOCK_LINE_FALLBACK_ENABLED, state.clockFallback && state.wordTiming)
                putExtra(UniversalSnapshotStore.EXTRA_NETWORK_ENABLED, state.network)
            })
        }
    }
}
