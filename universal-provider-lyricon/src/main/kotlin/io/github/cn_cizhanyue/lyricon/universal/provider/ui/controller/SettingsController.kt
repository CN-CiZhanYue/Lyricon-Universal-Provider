package io.github.cn_cizhanyue.lyricon.universal.provider.ui.controller

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalAppIo
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalSettingsConstants
import io.github.cn_cizhanyue.lyricon.universal.provider.cache.UniversalLyricCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val timeFloor: Long = 0L,
    val network: Boolean = true,
    val translation: Boolean = true,
    val wordTiming: Boolean = true,
    val clockFallback: Boolean = true,
    val debug: Boolean = false,
    val aiEnabled: Boolean = false,
    val aiEndpoint: String = "",
    val aiKey: String = "",
    val aiModel: String = "",
    val aiTarget: String = "",
    val loaded: Boolean = false,
) {
    val aiValid: Boolean get() = !aiEnabled || (aiEndpoint.isNotBlank() && aiKey.isNotBlank() && aiModel.isNotBlank())
}

enum class SettingsToggle { NETWORK, TRANSLATION, WORD_TIMING, CLOCK_FALLBACK, DEBUG }

class SettingsController(private val context: Context) {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    suspend fun load() = withContext(UniversalAppIo.dispatcher) {
        val prefs = context.getSharedPreferences(UniversalSettingsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        _state.value = SettingsUiState(
            network = prefs.getBoolean(UniversalSettingsConstants.KEY_NETWORK, true),
            translation = prefs.getBoolean(UniversalSettingsConstants.KEY_TRANSLATION, true),
            wordTiming = prefs.getBoolean(UniversalSettingsConstants.KEY_WORD_TIMING, true),
            clockFallback = prefs.getBoolean(UniversalSettingsConstants.KEY_CLOCK_LINE_FALLBACK, true),
            debug = prefs.getBoolean(UniversalSettingsConstants.KEY_DEBUG, false),
            aiEnabled = prefs.getBoolean(UniversalSettingsConstants.KEY_AI_ENABLED, false),
            aiEndpoint = prefs.getString(UniversalSettingsConstants.KEY_AI_ENDPOINT, "https://api.openai.com/v1").orEmpty(),
            aiKey = prefs.getString(UniversalSettingsConstants.KEY_AI_API_KEY, "").orEmpty(),
            aiModel = prefs.getString(UniversalSettingsConstants.KEY_AI_MODEL, "gpt-4o-mini").orEmpty(),
            aiTarget = prefs.getString(UniversalSettingsConstants.KEY_AI_TARGET, context.getString(io.github.cn_cizhanyue.lyricon.universal.provider.R.string.settings_ai_target_default)).orEmpty(),
            loaded = true,
        )
    }

    fun setToggle(key: SettingsToggle, value: Boolean) {
        _state.update {
            when (key) {
                SettingsToggle.NETWORK -> it.copy(network = value)
                SettingsToggle.TRANSLATION -> it.copy(translation = value)
                SettingsToggle.WORD_TIMING -> it.copy(wordTiming = value)
                SettingsToggle.CLOCK_FALLBACK -> it.copy(clockFallback = value)
                SettingsToggle.DEBUG -> it.copy(debug = value)
            }
        }
        SourceConfigBroadcaster.sendProcessing(context, _state.value)
    }

    fun updateAi(block: (SettingsUiState) -> SettingsUiState) {
        _state.update(block)
    }

    fun saveAi(): Boolean {
        if (!_state.value.aiValid) return false
        SourceConfigBroadcaster.sendAi(context, _state.value)
        return true
    }

    suspend fun clearCache(): Int = withContext(UniversalAppIo.dispatcher) {
        UniversalLyricCache.clear().songs
    }
}

@Composable
fun rememberSettingsController(): Pair<SettingsController, SettingsUiState> {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val controller = remember { SettingsController(context) }
    val state by controller.state.collectAsState()
    LaunchedEffect(controller) { controller.load() }
    return controller to state
}
