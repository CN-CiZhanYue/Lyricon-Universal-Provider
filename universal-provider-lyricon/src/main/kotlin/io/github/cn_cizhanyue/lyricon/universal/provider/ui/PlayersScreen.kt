package io.github.cn_cizhanyue.lyricon.universal.provider.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cn_cizhanyue.lyricon.universal.provider.PlayerBindingPolicy
import io.github.cn_cizhanyue.lyricon.universal.provider.R
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalBindingStore
import io.github.cn_cizhanyue.lyricon.universal.provider.UniversalAppIo
import kotlinx.coroutines.withContext

class PlayersUiState {
    var searchQuery by mutableStateOf("")
    var showSystemApps by mutableStateOf(false)
    var primaryUserOnly by mutableStateOf(true)
    var showMenu by mutableStateOf(false)
    var boundPackages by mutableStateOf(emptySet<String>())
    var allApps by mutableStateOf<List<AppItemUi>>(emptyList())
    var isLoading by mutableStateOf(true)
    var hydrated by mutableStateOf(false)
    val listState = LazyListState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayersScreen(state: PlayersUiState) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (!state.hydrated) {
            state.boundPackages = withContext(UniversalAppIo.dispatcher) {
                UniversalBindingStore.getBoundPackages(context).toSet()
            }
            val cached = InstalledAppCatalog.peek()
            if (cached.isNotEmpty()) {
                state.allApps = cached
                state.isLoading = false
            }
            state.hydrated = true
        }
        val loaded = InstalledAppCatalog.load(context)
        if (state.allApps !== loaded) {
            state.allApps = loaded
        }
        state.isLoading = false
    }

    val filteredApps = remember(state.allApps, state.searchQuery, state.showSystemApps) {
        val q = state.searchQuery.trim().lowercase()
        state.allApps.filter { item ->
            val blocked = PlayerBindingPolicy.isBlocked(item.packageName)
            if (!state.showSystemApps && item.isSystem && !blocked) return@filter false
            if (q.isEmpty()) true
            else item.label.lowercase().contains(q) || item.packageName.lowercase().contains(q)
        }
    }

    // Keep bound players at the top without changing the binding set.
    // The catalog is already alphabetical; a stable partition avoids sorting on each toggle.
    val orderedApps = remember(filteredApps, state.boundPackages) {
        val (bound, other) = filteredApps.partition { it.packageName in state.boundPackages }
        bound + other
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        TopAppBar(
            title = {
                Text(stringResource(R.string.players_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            },
            navigationIcon = {
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.PlayCircle, contentDescription = null)
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { state.showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(
                        expanded = state.showMenu,
                        onDismissRequest = { state.showMenu = false },
                        shape = RoundedCornerShape(28.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 0.dp,
                        shadowElevation = 3.dp
                    ) {
                        FilterMenuRow(
                            icon = Icons.Filled.Person,
                            title = stringResource(R.string.filter_primary_user),
                            subtitle = stringResource(R.string.filter_primary_user_desc),
                            selected = state.primaryUserOnly,
                            onClick = { state.primaryUserOnly = !state.primaryUserOnly }
                        )
                        FilterMenuRow(
                            icon = Icons.Filled.Settings,
                            title = stringResource(R.string.filter_system_apps),
                            subtitle = stringResource(R.string.filter_system_apps_desc),
                            selected = state.showSystemApps,
                            onClick = { state.showSystemApps = !state.showSystemApps }
                        )
                    }
                }
            },
            windowInsets = WindowInsets.statusBars,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Text(
            stringResource(R.string.app_list),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { state.searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { state.searchQuery = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                    }
                }
            },
            placeholder = { Text(stringResource(R.string.search_apps_hint)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )

        Text(
            text = if (state.isLoading) stringResource(R.string.loading_apps) else stringResource(R.string.apps_summary, state.boundPackages.size, filteredApps.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state.listState,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(
                items = orderedApps,
                key = { it.packageName },
                contentType = { "app-row" }
            ) { app ->
                val blockedReason = PlayerBindingPolicy.blockReason(app.packageName)
                val isBlocked = blockedReason != null
                val isBound = !isBlocked && state.boundPackages.contains(app.packageName)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .semantics(mergeDescendants = true) {},
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppLauncherIcon(packageName = app.packageName)

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isBlocked) "$blockedReason · ${app.packageName}" else app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isBlocked) {
                            Icon(
                                Icons.Filled.Block,
                                contentDescription = stringResource(R.string.disabled),
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        } else {
                            Switch(
                                checked = isBound,
                                onCheckedChange = { checked ->
                                    val newSet = state.boundPackages.toMutableSet()
                                    if (checked) newSet.add(app.packageName) else newSet.remove(app.packageName)
                                    state.boundPackages = newSet
                                    UniversalBindingStore.saveBoundPackages(context, newSet)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppLauncherIcon(packageName: String) {
    val pm = LocalContext.current.packageManager
    val targetSizePx = with(LocalDensity.current) { 48.dp.roundToPx() }
    val initiallyCached = remember(packageName, targetSizePx) {
        AppIconCache.peek(packageName, targetSizePx)
    }
    var bitmap by remember(packageName, targetSizePx) { mutableStateOf(initiallyCached) }
    if (initiallyCached == null) {
        LaunchedEffect(packageName, targetSizePx) {
            bitmap = AppIconCache.load(pm, packageName, targetSizePx)
        }
    }

    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
        )
    } else {
        Icon(
            Icons.Filled.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FilterMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .widthIn(min = 280.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
