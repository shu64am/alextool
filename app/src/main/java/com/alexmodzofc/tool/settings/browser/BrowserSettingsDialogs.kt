package com.alexmodzofc.tool.settings.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.alexmodzofc.tool.ui.AlexToolRadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexmodzofc.tool.R
import com.alexmodzofc.tool.ui.AlexToolDialog
import com.alexmodzofc.tool.settings.common.SettingsPickerOptionBottomSpacing
import com.alexmodzofc.tool.settings.common.SettingsPickerOptionContentPadding
import com.alexmodzofc.tool.setup.DefaultChip
import com.alexmodzofc.tool.setup.SelectableCard
import com.alexmodzofc.tool.ui.theme.LocalAlexToolColors

@Composable
fun SearchEngineDialog(
    current: String,
    hideStatusBar: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalAlexToolColors.current
    var selected by remember(current) { mutableStateOf(current) }

    AlexToolDialog(
        title = stringResource(R.string.choose_search_engine),
        hideStatusBar = hideStatusBar,
        onDismiss = onDismiss,
        footer = {
            Row(Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel), color = colors.primary, fontWeight = FontWeight.Medium)
                }
                TextButton(onClick = { onConfirm(selected) }) {
                    Text(stringResource(android.R.string.ok), color = colors.primary, fontWeight = FontWeight.Medium)
                }
            }
        }
    ) {
        data class EngineOption(val key: String, val titleRes: Int, val descRes: Int, val showDefault: Boolean)
        listOf(
            EngineOption("duckduckgo", R.string.engine_duckduckgo, R.string.engine_duckduckgo_desc, false),
            EngineOption("brave", R.string.engine_brave, R.string.engine_brave_desc, false),
            EngineOption("google", R.string.engine_google, R.string.engine_google_desc, true)
        ).forEach { option ->
            val sel = selected == option.key
            SelectableCard(
                selected = sel, onClick = { selected = option.key },
                cardBackground = colors.surfaceVariant, primary = colors.primary,
                contentPadding = SettingsPickerOptionContentPadding, bottomSpacing = SettingsPickerOptionBottomSpacing
            ) {
                AlexToolRadioButton(selected = sel)
                Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                    Text(stringResource(option.titleRes), color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(option.descRes), color = colors.secondaryText, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                }
                if (option.showDefault) DefaultChip(stringResource(R.string.default_label), colors.primary)
            }
        }
    }
}
