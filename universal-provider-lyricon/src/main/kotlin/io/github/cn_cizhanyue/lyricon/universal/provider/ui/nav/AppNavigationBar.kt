package io.github.cn_cizhanyue.lyricon.universal.provider.ui.nav

import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
    val container = if (design.glassEnabled) {
        design.colors.surface.copy(alpha = if (design.dark) 0.62f else 0.68f)
    } else {
        design.colors.surface
    }
    NavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = if (design.dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.06f),
                    start = Offset.Zero,
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            },
        containerColor = container,
        tonalElevation = 0.dp,
    ) {
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
