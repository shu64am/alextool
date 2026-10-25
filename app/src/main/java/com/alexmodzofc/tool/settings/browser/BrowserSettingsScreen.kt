package com.alexmodzofc.tool.settings.browser
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Search

import androidx.compose.foundation.layout.padding
import com.alexmodzofc.tool.ui.AlexToolSwitch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alexmodzofc.tool.R
import com.alexmodzofc.tool.settings.common.SettingsRow
import com.alexmodzofc.tool.settings.common.SettingsScreenScaffold
import com.alexmodzofc.tool.settings.common.SettingsSection
import com.alexmodzofc.tool.setup.SectionLabel
import com.alexmodzofc.tool.ui.theme.LocalAlexToolColors

@Composable
fun BrowserSettingsScreen(
    state: BrowserSettingsUiState,
    onSearchEngineConfirmed: (String) -> Unit,
    onJavascriptRowClicked: () -> Unit
) {
    val colors = LocalAlexToolColors.current

    SettingsScreenScaffold(
        overlay = {
            if (state.searchEngineDialogOpen) {
                SearchEngineDialog(
                    current = state.searchEngine,
                    hideStatusBar = state.hideStatusBar,
                    onConfirm = onSearchEngineConfirmed,
                    onDismiss = { state.searchEngineDialogOpen = false }
                )
            }
        }
    ) {
        SectionLabel(stringResource(R.string.pref_category_search).uppercase(), colors.primary, Modifier.padding(start = 4.dp, bottom = 8.dp))
        SettingsSection(colors.cardBackground) {
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Search,
                title = stringResource(R.string.search_engine),
                summary = stringResource(engineSummaryRes(state.searchEngine)),
                colors = colors,
                onClick = { state.searchEngineDialogOpen = true }
            )
        }

        SectionLabel(stringResource(R.string.browser_settings).uppercase(), colors.primary, Modifier.padding(start = 4.dp, bottom = 8.dp))
        SettingsSection(colors.cardBackground) {
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.DesktopWindows,
                title = stringResource(R.string.javascript_enabled),
                summary = stringResource(R.string.javascript_enabled_summary),
                colors = colors,
                onClick = onJavascriptRowClicked,
                trailing = {
                    AlexToolSwitch(checked = state.javascriptEnabled)
                }
            )
        }
    }
}

private fun engineSummaryRes(engine: String): Int = when (engine) {
    "brave" -> R.string.engine_brave
    "google" -> R.string.engine_google
    else -> R.string.engine_duckduckgo
}
