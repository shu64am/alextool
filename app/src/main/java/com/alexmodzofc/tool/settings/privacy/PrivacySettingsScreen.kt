package com.alexmodzofc.tool.settings.privacy
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock

import androidx.compose.foundation.layout.padding
import com.alexmodzofc.tool.ui.AlexToolSwitch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alexmodzofc.tool.R
import com.alexmodzofc.tool.settings.common.RowDivider
import com.alexmodzofc.tool.settings.common.SettingsRow
import com.alexmodzofc.tool.settings.common.SettingsScreenScaffold
import com.alexmodzofc.tool.settings.common.SettingsSection
import com.alexmodzofc.tool.setup.SectionLabel
import com.alexmodzofc.tool.ui.theme.LocalAlexToolColors

@Composable
fun PrivacySettingsScreen(
    state: PrivacySettingsUiState,
    onBlockThirdPartyCookiesClick: () -> Unit,
    onCustomUserAgentClick: () -> Unit,
    onHttpsOnlyClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    val colors = LocalAlexToolColors.current

    SettingsScreenScaffold {
        // These two section headers are already stored fully uppercase in strings.xml
        // (the original XML never applied textAllCaps to them), so they're used as-is.
        SectionLabel(stringResource(R.string.privacy_section_privacy), colors.primary, Modifier.padding(start = 4.dp, bottom = 8.dp))
        SettingsSection(colors.cardBackground) {
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Cookie,
                title = stringResource(R.string.block_third_party_cookies),
                summary = stringResource(R.string.block_third_party_cookies_summary),
                colors = colors,
                onClick = onBlockThirdPartyCookiesClick,
                trailing = {
                    AlexToolSwitch(checked = state.blockThirdPartyCookies)
                }
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Language,
                title = stringResource(R.string.custom_user_agent),
                summary = stringResource(R.string.custom_user_agent_summary),
                colors = colors,
                onClick = onCustomUserAgentClick,
                trailing = {
                    AlexToolSwitch(checked = state.customUserAgent)
                }
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Lock,
                title = stringResource(R.string.https_only),
                summary = stringResource(R.string.https_only_summary),
                colors = colors,
                onClick = onHttpsOnlyClick,
                trailing = {
                    AlexToolSwitch(checked = state.httpsOnly)
                }
            )
        }

        SectionLabel(stringResource(R.string.privacy_section_history), colors.primary, Modifier.padding(start = 4.dp, bottom = 8.dp))
        SettingsSection(colors.cardBackground) {
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.History,
                title = stringResource(R.string.history_title),
                summary = stringResource(R.string.history_summary),
                colors = colors,
                onClick = onHistoryClick
            )
        }
    }
}
