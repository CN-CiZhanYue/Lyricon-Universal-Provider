package io.github.cn_cizhanyue.lyricon.universal.provider.ui.pages.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.AppDimens
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.design.LocalAppDesign
import io.github.cn_cizhanyue.lyricon.universal.provider.ui.glass.GlassSurface

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val design = LocalAppDesign.current
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = design.typography.label, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        GlassSurface { content() }
    }
}
@Composable
fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = AppDimens.minTouch), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked, onCheckedChange)
    }
}
@Composable
fun TextFieldRow(label: String, value: String, password: Boolean = false, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
    )
    Spacer(Modifier.height(8.dp))
}
@Composable
fun LinkRow(title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AppDimens.minTouch).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, null, tint = LocalAppDesign.current.colors.onSurfaceVariant)
    }
}
@Composable
fun Choice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val design = LocalAppDesign.current
    Box(
        modifier = modifier
            .heightIn(min = AppDimens.minTouch)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) design.colors.primary.copy(alpha = 0.16f) else design.colors.surface.copy(alpha = 0.4f))
            .border(if (selected) 2.dp else 1.dp, if (selected) design.colors.primary else design.colors.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) design.colors.primary else design.colors.onSurface)
    }
}
