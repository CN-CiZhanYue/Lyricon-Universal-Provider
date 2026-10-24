package io.github.cn_cizhanyue.lyricon.universal.provider.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.cn_cizhanyue.lyricon.universal.provider.R
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalAppIo
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.controller.CurrentTrack
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.controller.PlayerBindingController
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.controller.SearchController
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.controller.rememberSettingsController
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.controller.rememberThemeController
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.BackgroundImageCache
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.LocalAppDesign
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.rememberAppDesign
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.components.AboutDialog
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.components.FirstRunStore
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.glass.GlassScaffold
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.nav.AppNavigationBar
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.nav.AppTopBar
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.pages.home.HomeScreen
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.pages.players.PlayersScreen
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.pages.priority.PriorityScreen
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.pages.search.SearchScreen
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.pages.settings.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun UniversalMainApp(
    initialScreen: Screen = Screen.HOME,
    snapshotMap: Map<String, String>,
    artworkBitmap: Bitmap?,
    cachedSongsCount: Int,
    onRefreshSnapshot: () -> Unit,
    onClearCache: () -> Unit,
    onConfirmNoLyric: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val (themeController, theme) = rememberThemeController()
    val (settingsController, _) = rememberSettingsController()
    val design = rememberAppDesign(theme)
    val searchController = remember { SearchController(context) }
    val playerController = remember { PlayerBindingController(context) }
    var screen by remember { mutableStateOf(initialScreen) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    val custom by produceState<ImageBitmap?>(null, theme.customBackground) {
        value = BackgroundImageCache.load(context, theme.customBackground)
    }
    var language by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(context) { language = withContext(Dispatchers.IO) { LanguageStore.read(context) } }
    val loadedLanguage = language ?: return
    val current = CurrentTrack(
        title = snapshotMap["title"].orEmpty().takeUnless { it == "(missing)" }.orEmpty(),
        artist = snapshotMap["artist"].orEmpty().takeUnless { it == "(missing)" }.orEmpty(),
        packageName = snapshotMap["package"].orEmpty().takeUnless { it == "(none)" }.orEmpty(),
        generation = snapshotMap["generation"]?.toLongOrNull() ?: 1L,
    )
    var showFirstAbout by remember { mutableStateOf(FirstRunStore.shouldShow(context)) }
    val goBack: () -> Unit = {
        screen = when (screen) {
            Screen.PRIORITY -> Screen.SETTINGS
            Screen.HOME -> Screen.HOME
            else -> Screen.HOME
        }
    }
    BackHandler(enabled = screen != Screen.HOME) { goBack() }
    PredictiveBackHandler(enabled = screen != Screen.HOME) { progress ->
        progress.collect { backProgress = it.progress }
        backProgress = 0f
        goBack()
    }

    ProvideAppLocale(loadedLanguage) {
        val onLanguage: (String) -> Unit = { code ->
            language = code
            UniversalAppIo.execute { LanguageStore.write(context, code) }
        }
        UniversalM3Theme(settings = theme) {
            GlassScaffold(design = design, customBackground = custom) {
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        AppTopBar(
                            title = titleFor(screen),
                            subtitle = if (screen == Screen.HOME) stringResource(R.string.brand_subtitle) else null,
                            onBack = if (screen == Screen.PRIORITY) ({ goBack() }) else null,
                            actions = {
                                if (screen == Screen.HOME) {
                                    IconButton(onClick = onRefreshSnapshot) {
                                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                                    }
                                }
                            },
                        )
                    },
                    bottomBar = {
                        if (screen != Screen.PRIORITY) AppNavigationBar(screen) { target -> screen = target }
                    },
                ) { padding ->
                    AnimatedContent(
                        targetState = screen,
                        modifier = Modifier.fillMaxSize().padding(padding).graphicsLayer {
                            translationX = backProgress * size.width * 0.14f
                            alpha = 1f - backProgress * 0.18f
                        },
                        transitionSpec = {
                            val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                            (slideInHorizontally { it / 5 * direction } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it / 5 * direction } + fadeOut())
                        },
                        label = "screen",
                    ) { target ->
                        Page(
                            screen = target,
                            modifier = Modifier.fillMaxSize(),
                            snapshot = snapshotMap,
                            artwork = artworkBitmap,
                            cached = cachedSongsCount,
                            current = current,
                            themeController = themeController,
                            settingsController = settingsController,
                            searchController = searchController,
                            playerController = playerController,
                            onClearCache = onClearCache,
                            onConfirmNoLyric = onConfirmNoLyric,
                            onLanguage = onLanguage,
                            onScreen = { screen = it },
                        )
                    }
                }
            }
        }
        CompositionLocalProvider(LocalAppDesign provides design) {
            AboutDialog(show = showFirstAbout, onDismiss = {
                showFirstAbout = false
                FirstRunStore.markShown(context)
            })
        }
    }
}

@Composable
private fun Page(
    screen: Screen,
    modifier: Modifier,
    snapshot: Map<String, String>,
    artwork: Bitmap?,
    cached: Int,
    current: CurrentTrack,
    themeController: io.github.cn_cizhanyue.lyricon.universal.provider.ui.controller.ThemeController,
    settingsController: io.github.cn_cizhanyue.lyricon.universal.provider.ui.controller.SettingsController,
    searchController: SearchController,
    playerController: PlayerBindingController,
    onClearCache: () -> Unit,
    onConfirmNoLyric: () -> Unit,
    onLanguage: (String) -> Unit,
    onScreen: (Screen) -> Unit,
) {
    Box(modifier) {
        when (screen) {
            Screen.HOME -> HomeScreen(snapshot, artwork, cached)
            Screen.PLAYERS -> PlayersScreen(playerController)
            Screen.SEARCH -> SearchScreen(searchController, current, onBound = { onScreen(Screen.HOME) }, onNoLyric = onConfirmNoLyric)
            Screen.SETTINGS -> SettingsScreen(
                settingsController = settingsController,
                themeController = themeController,
                cached = cached,
                onClearCache = onClearCache,
                onPriority = { onScreen(Screen.PRIORITY) },
                onLanguage = onLanguage,
            )
            Screen.PRIORITY -> PriorityScreen(settingsController, onSaved = { onScreen(Screen.SETTINGS) })
        }
    }
}

@Composable
private fun titleFor(screen: Screen): String = when (screen) {
    Screen.HOME -> stringResource(R.string.home_title)
    Screen.PLAYERS -> stringResource(R.string.players_title)
    Screen.SEARCH -> stringResource(R.string.nav_search)
    Screen.SETTINGS -> stringResource(R.string.nav_settings)
    Screen.PRIORITY -> stringResource(R.string.priority_title)
}
