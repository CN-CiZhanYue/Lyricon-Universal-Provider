package io.github.cn_cizhanyue.lyricon.universal.provider.ui.pages.search

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.cn_cizhanyue.lyricon.universal.provider.R
import io.github.cn_cizhanyue.lyricon.universal.provider.api.SearchCandidate
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.controller.CurrentTrack
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.controller.SearchController
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.AppDimens
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.LocalAppDesign
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.glass.GlassSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    controller: SearchController,
    current: CurrentTrack,
    onBound: () -> Unit,
    onNoLyric: () -> Unit,
) {
    val context = LocalContext.current
    val design = LocalAppDesign.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<SearchCandidate>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var selectedSource by remember { mutableStateOf<String?>(null) }
    fun search() {
        if (query.isBlank()) return
        searching = true
        status = context.getString(R.string.searching_sources)
        scope.launch {
            val result = withContext(Dispatchers.IO) { controller.search(query) }
            candidates = result
            status = context.getString(R.string.search_results, result.size)
            searching = false
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = AppDimens.pagePadding, end = AppDimens.pagePadding, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing),
    ) {
        item {
            GlassSurface {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text(stringResource(R.string.search_songs_hint)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { search() }, enabled = !searching, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Text(stringResource(R.string.action_search))
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { controller.markNoLyric(current) }
                                Toast.makeText(context, context.getString(R.string.toast_no_lyric), Toast.LENGTH_SHORT).show()
                                onNoLyric()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text(stringResource(R.string.confirm_no_lyric)) }
                }
                if (searching) {
                    Spacer(Modifier.height(6.dp))
                    LoadingIndicator(Modifier.size(20.dp), color = design.colors.primary)
                } else if (status.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(status, style = design.typography.caption)
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val filters = listOf(
                    stringResource(R.string.filter_all) to null,
                    stringResource(R.string.source_qq) to "QQ",
                    stringResource(R.string.source_netease) to "网易云",
                    stringResource(R.string.source_apple) to "Apple",
                )
                filters.forEach { (label, value) ->
                    FilterChip(
                        selected = selectedSource == value,
                        onClick = { selectedSource = value },
                        label = { Text(label) },
                    )
                }
            }
        }
        val shown = if (selectedSource == null) candidates else candidates.filter { it.source.contains(selectedSource!!, true) }
        items(shown, key = { it.source + it.id }) { candidate ->
            GlassSurface(Modifier.clickable {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { controller.bind(candidate, current) }
                    if (ok) {
                        Toast.makeText(context, context.getString(R.string.toast_lyric_bound), Toast.LENGTH_SHORT).show()
                        onBound()
                    } else {
                        Toast.makeText(context, context.getString(R.string.toast_lyric_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }) {
                Text(candidate.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${candidate.artist} · ${candidate.album}", style = design.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(candidate.source, style = design.typography.caption, color = design.colors.primary)
            }
        }
    }
}
