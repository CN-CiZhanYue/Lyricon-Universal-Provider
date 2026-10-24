package io.github.cn_cizhanyue.lyricon.universal.provider.ui.controller

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.ThemeSettings
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.ThemeStore
import kotlinx.coroutines.flow.StateFlow

class ThemeController(private val context: Context) {
    val state: StateFlow<ThemeSettings> = ThemeStore.state

    fun update(value: ThemeSettings) = ThemeStore.update(context, value)
}

@Composable
fun rememberThemeController(): Pair<ThemeController, ThemeSettings> {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val controller = remember { ThemeController(context) }
    val state by controller.state.collectAsState()
    LaunchedEffect(controller) { ThemeStore.load(context) }
    return controller to state
}
