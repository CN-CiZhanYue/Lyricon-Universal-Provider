package io.github.cn_cizhanyue.lyricon.universal.provider.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.cn_cizhanyue.lyricon.universal.provider.BuildConfig
import io.github.cn_cizhanyue.lyricon.universal.provider.R
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.AppDimens
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.LocalAppDesign

/**
 * About is intentionally not a GlassSurface: it is a solid, readable sheet on
 * every theme. The first-run and Settings entry points share this dialog.
 */
@Composable
fun AboutDialog(show: Boolean, onDismiss: () -> Unit) {
    if (!show) return
    val context = LocalContext.current
    val design = LocalAppDesign.current
    val colors = design.colors
    Dialog(onDismissRequest = onDismiss) {
        AnimatedVisibility(
            visible = show,
            enter = fadeIn() + scaleIn(initialScale = 0.88f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                shape = RoundedCornerShape(AppDimens.cardRadius),
                colors = CardDefaults.cardColors(
                    containerColor = colors.surface,
                    contentColor = colors.onSurface,
                ),
                border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.55f)),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(30.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleLarge)
                            Text(
                                stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                                style = design.typography.caption,
                            )
                        }
                    }
                    Text(stringResource(R.string.about_message), style = design.typography.body)
                    HorizontalDivider(color = colors.outline.copy(alpha = 0.5f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { openUrl(context, "https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Providers") },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.about_open_official), maxLines = 1) }
                        TextButton(
                            onClick = { openUrl(context, "https://github.com/CN-CiZhanYue/Lyricon-Universal-Provider") },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.about_open_project), maxLines = 1) }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.about_dismiss))
                    }
                }
            }
        }
    }
}


private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, context.getString(R.string.about_link_failed), Toast.LENGTH_SHORT).show()
    }
}
