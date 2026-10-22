package io.github.cn_cizhanyue.lyricon.universal.provider.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.cn_cizhanyue.lyricon.universal.provider.R

private const val OFFICIAL_URL = "https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Providers"


@Composable
fun AboutDialog(onDismiss: () -> Unit, onOpenNetworkSettings: (() -> Unit)? = null) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            Column {
                Text(stringResource(R.string.about_message))
                if (onOpenNetworkSettings != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.about_network_hint),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(stringResource(R.string.about_network_body))
                    TextButton(onClick = {
                        onOpenNetworkSettings()
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.about_open_network))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(OFFICIAL_URL))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                onDismiss()
            }) {
                Text(stringResource(R.string.about_open_official))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.about_dismiss))
            }
        },
    )
}
