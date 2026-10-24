package io.github.cn_cizhanyue.lyricon.universal.provider.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.cn_cizhanyue.lyricon.universal.provider.R
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.Screen
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.LocalAppDesign

@Composable
fun AppNavigationBar(current: Screen, onSelect: (Screen) -> Unit) {
    val design = LocalAppDesign.current
    val items = listOf(
        Screen.HOME to Icons.Filled.Home,
        Screen.PLAYERS to Icons.Filled.PlayCircle,
        Screen.SEARCH to Icons.Filled.Search,
        Screen.SETTINGS to Icons.Filled.Settings,
    )
    NavigationBar(containerColor = design.colors.surface.copy(alpha = 0.96f)) {
        items.forEach { (target, icon) ->
            val label = when (target) {
                Screen.HOME -> stringResource(R.string.nav_home)
                Screen.PLAYERS -> stringResource(R.string.nav_players)
                Screen.SEARCH -> stringResource(R.string.nav_search)
                else -> stringResource(R.string.nav_settings)
            }
            NavigationBarItem(
                selected = current == target,
                onClick = { onSelect(target) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
            )
        }
    }
}
