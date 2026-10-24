package io.github.cn_cizhanyue.lyricon.universal.provider.ui.pages.players

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.cn_cizhanyue.lyricon.universal.provider.R
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.AppIconCache
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.AppItemUi
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.controller.PlayerBindingController
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.AppDimens
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.LocalAppDesign
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.glass.GlassSurface

@Composable
fun PlayersScreen(controller: PlayerBindingController) {
    val context = LocalContext.current
    val design = LocalAppDesign.current
    var apps by remember { mutableStateOf<List<AppItemUi>>(emptyList()) }
    var bound by remember { mutableStateOf<Set<String>>(emptySet()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var showSystem by remember { mutableStateOf(false) }
    var primaryUserOnly by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val data = controller.load()
        apps = data.apps
        bound = data.bound
        loading = false
    }
    val filtered = apps.filter { app ->
        (!primaryUserOnly || app.isPrimaryUser) &&
            (showSystem || !app.isSystem) &&
            (query.isBlank() || app.label.contains(query, true) || app.packageName.contains(query, true))
    }.let { list ->
        val (boundApps, other) = list.partition { it.packageName in bound }
        boundApps + other
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = AppDimens.pagePadding, end = AppDimens.pagePadding, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        FilterMenuItem(stringResource(R.string.filter_system_apps), showSystem) { showSystem = !showSystem }
                        FilterMenuItem(stringResource(R.string.filter_primary_user), primaryUserOnly) { primaryUserOnly = !primaryUserOnly }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.search_apps_hint)) },
                singleLine = true,
            )
        }
        if (loading) {
            item { Text(stringResource(R.string.loading_apps), modifier = Modifier.padding(16.dp)) }
        } else {
            items(filtered, key = { it.packageName + "|" + it.userLabel }) { app ->
                GlassSurface {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var icon by remember(app.packageName) { mutableStateOf<Bitmap?>(null) }
                        val iconPx = with(LocalDensity.current) { 36.dp.roundToPx() }
                        LaunchedEffect(app.packageName) {
                            icon = AppIconCache.load(context.packageManager, app.packageName, iconPx)
                        }
                        if (icon != null) {
                            Image(
                                bitmap = icon!!.asImageBitmap(),
                                contentDescription = app.label,
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)),
                            )
                        } else {
                            Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = design.colors.primary, modifier = Modifier.size(AppDimens.iconMedium))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (app.userLabel.isBlank()) app.packageName else app.userLabel + " · " + app.packageName,
                                style = design.typography.caption,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Switch(checked = app.packageName in bound, onCheckedChange = {
                            bound = controller.toggle(app.packageName, bound, it)
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterMenuItem(title: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(title) },
        onClick = onClick,
        trailingIcon = { if (selected) Icon(Icons.Filled.Check, contentDescription = null) },
    )
}
