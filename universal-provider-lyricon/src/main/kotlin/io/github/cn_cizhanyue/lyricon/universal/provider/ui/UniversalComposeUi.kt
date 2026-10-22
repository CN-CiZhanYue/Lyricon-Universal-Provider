package io.github.cn_cizhanyue.lyricon.universal.provider.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.cn_cizhanyue.lyricon.universal.provider.R
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalAppIo
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

enum class Screen { HOME, PLAYERS, SEARCH, SETTINGS, PRIORITY }

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun UniversalMainApp(
    initialScreen: Screen = Screen.HOME,
    snapshotMap: Map<String, String>,
    artworkBitmap: Bitmap?,
    cachedSongsCount: Int,
    onRefreshSnapshot: () -> Unit,
    onClearCache: () -> Unit,
    onConfirmNoLyric: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(initialScreen) }
    var searchInitialQuery by remember { mutableStateOf("") }

    val searchState = remember { SearchUiState() }
    val playersState = remember { PlayersUiState() }

    val appContext = LocalContext.current.applicationContext
    var language by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(appContext) {
        // The first frame must not wait behind binding fsync/Binder work on the write queue.
        language = withContext(Dispatchers.IO) { LanguageStore.read(appContext) }
    }
    val loadedLanguage = language ?: return

    ProvideAppLocale(loadedLanguage) {
        BackHandler(enabled = currentScreen == Screen.PRIORITY) {
            currentScreen = Screen.SETTINGS
        }
        UniversalM3Theme {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                contentWindowInsets = WindowInsets(0.dp),
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth(),
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        NavigationBarItem(
                            selected = currentScreen == Screen.HOME,
                            onClick = { currentScreen = Screen.HOME },
                            icon = { Icon(Icons.Filled.Home, contentDescription = stringResource(R.string.nav_home)) },
                            label = { Text(stringResource(R.string.nav_home)) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                        NavigationBarItem(
                            selected = currentScreen == Screen.PLAYERS,
                            onClick = { currentScreen = Screen.PLAYERS },
                            icon = { Icon(Icons.Filled.PlayCircle, contentDescription = stringResource(R.string.nav_players)) },
                            label = { Text(stringResource(R.string.nav_players)) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                        NavigationBarItem(
                            selected = currentScreen == Screen.SEARCH,
                            onClick = { currentScreen = Screen.SEARCH },
                            icon = { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.nav_search)) },
                            label = { Text(stringResource(R.string.nav_search)) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                        NavigationBarItem(
                            selected = currentScreen == Screen.SETTINGS || currentScreen == Screen.PRIORITY,
                            onClick = { currentScreen = Screen.SETTINGS },
                            icon = { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings)) },
                            label = { Text(stringResource(R.string.nav_settings)) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                    AnimatedContent(
                        targetState = currentScreen,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            val toPriority = targetState == Screen.PRIORITY
                            val fromPriority = initialState == Screen.PRIORITY
                            val duration = if (toPriority || fromPriority) 280 else 200
                            val dir = when {
                                toPriority -> 1
                                fromPriority -> -1
                                targetState.ordinal >= initialState.ordinal -> 1
                                else -> -1
                            }
                            val slideFactor = if (toPriority || fromPriority) 1 else 8
                            (
                                slideInHorizontally(animationSpec = tween(duration)) { full -> dir * full / slideFactor } +
                                    fadeIn(animationSpec = tween(duration))
                                ) togetherWith (
                                slideOutHorizontally(animationSpec = tween(duration)) { full -> -dir * full / slideFactor } +
                                    fadeOut(animationSpec = tween((duration * 0.7f).toInt()))
                                )
                        },
                        label = "main-screen"
                    ) { screen ->
                        when (screen) {
                            Screen.HOME -> HomeScreen(
                                snapshotMap = snapshotMap,
                                artworkBitmap = artworkBitmap,
                                onRefreshSnapshot = onRefreshSnapshot,
                                onConfirmNoLyric = onConfirmNoLyric,
                                onNavigateToSearch = { query ->
                                    searchInitialQuery = query
                                    currentScreen = Screen.SEARCH
                                },
                                onLanguageSelected = { code ->
                                    language = code
                                    UniversalAppIo.execute { LanguageStore.write(appContext, code) }
                                },
                                onNavigateToSettings = { currentScreen = Screen.SETTINGS }
                            )
                            Screen.PLAYERS -> PlayersScreen(state = playersState)
                            Screen.SEARCH -> SearchScreen(
                                state = searchState,
                                initialQuery = searchInitialQuery,
                                currentPkg = snapshotMap["package"].orEmpty().takeUnless { it == "(none)" }.orEmpty(),
                                currentTitle = snapshotMap["title"].orEmpty().takeUnless { it == "(missing)" }.orEmpty(),
                                currentArtist = snapshotMap["artist"].orEmpty().takeUnless { it == "(missing)" }.orEmpty(),
                                currentGen = snapshotMap["generation"]?.toLongOrNull() ?: 1L,
                                onLyricBound = { currentScreen = Screen.HOME }
                            )
                            Screen.SETTINGS -> SettingsScreen(
                                cachedSongsCount = cachedSongsCount,
                                onNavigateToPriority = { currentScreen = Screen.PRIORITY },
                                onClearCache = onClearCache
                            )
                            Screen.PRIORITY -> PriorityScreen(
                                onBack = { currentScreen = Screen.SETTINGS }
                            )
                        }
                    }
                }
            }
        }
    }
}
