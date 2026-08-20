package com.alexmodzofc.tool.settings.extratooling

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.alexmodzofc.tool.R
import com.alexmodzofc.tool.extratooling.ExtraToolingManager
import com.alexmodzofc.tool.ui.AlexToolDialog
import com.alexmodzofc.tool.extratooling.UserScriptStore
import com.alexmodzofc.tool.extratooling.UserScriptFetcher
import com.alexmodzofc.tool.ui.theme.AlexToolColors
import com.alexmodzofc.tool.settings.common.RowDivider
import com.alexmodzofc.tool.settings.common.SettingsRow
import com.alexmodzofc.tool.settings.common.SettingsScreenScaffold
import com.alexmodzofc.tool.settings.common.SettingsSection
import com.alexmodzofc.tool.ui.theme.LocalAlexToolColors

// ─────────────────────────────── Link Toolkit ────────────────────────────────

@Composable
fun LinkToolkitPane() {
    val colors = LocalAlexToolColors.current
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var latest by remember { mutableStateOf<ExtraToolingManager.LinkPair?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    fun doPaste() {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = manager?.primaryClip
        val text = clip?.getItemAt(0)?.coerceToText(context)?.toString()?.ifBlank { null }
        if (text != null) {
            inputText = text
            toast = context.getString(R.string.link_toolkit_pasted)
        } else {
            toast = context.getString(R.string.link_toolkit_clipboard_empty)
        }
    }

    fun doGenerate() {
        if (inputText.isBlank()) {
            toast = context.getString(R.string.link_toolkit_empty_message)
            return
        }
        val pairs = ExtraToolingManager.extractAllLinkPairs(inputText)
        if (pairs.isEmpty()) {
            toast = context.getString(R.string.link_toolkit_no_pairs)
            return
        }
        latest = pairs.last()
        toast = context.getString(R.string.link_toolkit_pairs_found, pairs.size)
    }

    fun doClear() {
        inputText = ""
        latest = null
    }

    fun openInBrowser(url: String) {
        try {
            // Open inside AlexTool's own WebView (never an external browser).
            val intent = android.content.Intent(context, com.alexmodzofc.tool.browser.MainActivity::class.java)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(com.alexmodzofc.tool.settings.SettingsActivity.EXTRA_OPEN_URL, url)
            context.startActivity(intent)
            if (context is android.app.Activity) context.finish()
            toast = context.getString(R.string.link_toolkit_opened)
        } catch (e: Exception) {
            toast = context.getString(R.string.link_toolkit_invalid_link)
        }
    }

    val mono = FontFamily.Monospace

    SettingsScreenScaffold {
        // ── Paste Bot Message ──
        Text(
            text = stringResource(R.string.link_toolkit_title),
            color = colors.primary,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Default,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            style = androidx.compose.ui.text.TextStyle(letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp))
        )
        SettingsSection(colors.cardBackground) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                placeholder = { Text(stringResource(R.string.link_toolkit_paste_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                minLines = 6,
                maxLines = 14,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = if (colors.isLight) Color.Black else Color(0xFFF0F0F0),
                    unfocusedTextColor = if (colors.isLight) Color.Black else Color(0xFFF0F0F0),
                    focusedContainerColor = if (colors.isLight) Color(0xFFECECEC) else Color(0xFF2E2E2E),
                    unfocusedContainerColor = if (colors.isLight) Color(0xFFECECEC) else Color(0xFF2E2E2E),
                    focusedLabelColor = colors.secondaryText,
                    unfocusedLabelColor = colors.secondaryText,
                    focusedPlaceholderColor = colors.secondaryText,
                    unfocusedPlaceholderColor = colors.secondaryText,
                    cursorColor = colors.primary
                )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { doPaste() },
                    modifier = Modifier.weight(2.6f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 12.dp, top = 0.dp, bottom = 0.dp
                    ),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.link_toolkit_paste), color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Button(
                    onClick = { doGenerate() },
                    modifier = Modifier.weight(4.6f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 12.dp, top = 0.dp, bottom = 0.dp
                    ),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("GENERATE", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { doClear() },
                    modifier = Modifier.weight(2f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 12.dp, top = 0.dp, bottom = 0.dp
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface)
                ) {
                    Text(stringResource(R.string.link_toolkit_clear), maxLines = 1)
                }
            }
        }

        // ── Generated Alextrick Link card ──
        if (latest != null) {
            val link = latest!!
            // Mirror the reference: generated link lives on the ORIGINAL link's domain.
            val domain = runCatching { java.net.URL(link.original).host }.getOrNull() ?: "alextool.links"
            val generated = "https://$domain/links?alextrick=" + android.net.Uri.encode(link.bypassed.ifEmpty { link.original })
            val freshTime = java.text.SimpleDateFormat("dd MMM yyyy  hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            Spacer(modifier = Modifier.height(6.dp))
            SettingsSection(colors.cardBackground) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.link_toolkit_generated_link),
                        color = colors.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SelectionContainer {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = if (colors.isLight) Color(0xFFE8E8E8) else Color(0xFF262626)
                        ) {
                            Text(
                                text = generated,
                                color = if (colors.isLight) Color.Black else Color(0xFFF0F0F0),
                                fontFamily = mono,
                                fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                copyToClipboard(context, generated)
                                toast = context.getString(R.string.link_toolkit_copied)
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.link_toolkit_copy), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { openInBrowser(generated) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                        ) {
                            Icon(Icons.Filled.Public, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.link_toolkit_open), color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    // ── Original Link ──
                    Text(
                        stringResource(R.string.link_toolkit_original_link).uppercase(),
                        color = colors.secondaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = if (colors.isLight) Color(0xFFE8E8E8) else Color(0xFF262626)
                        ) {
                            Text(
                                text = link.original,
                                color = if (colors.isLight) Color.Black else Color(0xFFD8D8FF),
                                fontFamily = mono,
                                fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    // ── Bypassed Link ──
                    Text(
                        stringResource(R.string.link_toolkit_bypassed_link).uppercase(),
                        color = colors.secondaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = if (colors.isLight) Color(0xFFE8E8E8) else Color(0xFF262626)
                        ) {
                            Text(
                                text = link.bypassed.ifEmpty { context.getString(R.string.link_toolkit_invalid_link) },
                                color = if (colors.isLight) Color.Black else Color(0xFFD8D8FF),
                                fontFamily = mono,
                                fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    RowDivider(colors.divider)
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("🆕 ", fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp))
                        Text(
                            stringResource(R.string.link_toolkit_fresh),
                            color = colors.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("🕒 ", fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp))
                        Text(
                            "${stringResource(R.string.link_toolkit_time_label)}  :  $freshTime",
                            color = colors.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

    }

    toast?.let { t ->
        Toast(t)
        toast = null
    }
}

// ─────────────────────────────── Domain Blocker ──────────────────────────────

@Composable
fun DomainBlockerPane() {
    val colors = LocalAlexToolColors.current
    val context = LocalContext.current
    var domains by remember { mutableStateOf(ExtraToolingManager.getBlockedDomains(context)) }
    var disabledDomains by remember { mutableStateOf(ExtraToolingManager.getDisabledDomains(context)) }
    var addInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }

    fun handleAddDomain(input: String) {
        val result = ExtraToolingManager.addDomain(context, input)
        when (result) {
            null -> {}
            "parse_fail" -> toast = context.getString(R.string.domain_blocker_status_parse_fail, input)
            "already" -> toast = context.getString(R.string.domain_blocker_status_already, input)
            else -> {
                toast = context.getString(R.string.domain_blocker_status_blocked, result)
            }
        }
        domains = ExtraToolingManager.getBlockedDomains(context)
    }

    val filtered = domains.filter { it.contains(searchQuery, ignoreCase = true) }

    SettingsScreenScaffold {
        SettingsSection(colors.cardBackground) {
            TextField(
                value = addInput,
                onValueChange = { addInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                placeholder = { Text(stringResource(R.string.domain_blocker_add_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = if (colors.isLight) Color.Black else Color(0xFFF0F0F0),
                    unfocusedTextColor = if (colors.isLight) Color.Black else Color(0xFFF0F0F0),
                    focusedContainerColor = if (colors.isLight) Color(0xFFECECEC) else Color(0xFF2E2E2E),
                    unfocusedContainerColor = if (colors.isLight) Color(0xFFECECEC) else Color(0xFF2E2E2E),
                    focusedPlaceholderColor = colors.secondaryText,
                    unfocusedPlaceholderColor = colors.secondaryText,
                    cursorColor = colors.primary
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        handleAddDomain(addInput)
                        addInput = ""
                    }
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        handleAddDomain(addInput)
                        addInput = ""
                    },
                    modifier = Modifier.weight(2f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text(stringResource(R.string.domain_blocker_add), color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                FilledTonalButton(
                    onClick = {
                        addInput = ""
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(contentColor = colors.onSurface)
                ) {
                    Text(stringResource(R.string.link_toolkit_clear), maxLines = 1)
                }
            }
            RowDivider(colors.divider)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                placeholder = { Text(stringResource(R.string.domain_blocker_search_hint)) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = colors.secondaryText)
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        androidx.compose.material3.IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = colors.secondaryText)
                        }
                    }
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = if (colors.isLight) Color.Black else Color(0xFFF0F0F0),
                    unfocusedTextColor = if (colors.isLight) Color.Black else Color(0xFFF0F0F0),
                    focusedContainerColor = if (colors.isLight) Color(0xFFECECEC) else Color(0xFF2E2E2E),
                    unfocusedContainerColor = if (colors.isLight) Color(0xFFECECEC) else Color(0xFF2E2E2E),
                    focusedPlaceholderColor = colors.secondaryText,
                    unfocusedPlaceholderColor = colors.secondaryText,
                    cursorColor = colors.primary,
                    focusedLeadingIconColor = colors.secondaryText,
                    unfocusedLeadingIconColor = colors.secondaryText,
                    focusedTrailingIconColor = colors.secondaryText,
                    unfocusedTrailingIconColor = colors.secondaryText
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        if (searchQuery.isNotBlank()) {
            androidx.compose.material3.Text(
                text = "${filtered.size} of ${domains.size} rules",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = colors.secondaryText,
                fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)
            )
        }

        if (filtered.isEmpty() && searchQuery.isNotBlank()) {
            SettingsSection(colors.cardBackground) {
                SettingsRow(
                    icon = androidx.compose.material.icons.Icons.Filled.Search,
                    title = stringResource(R.string.quiver_guard_manual_filter_no_results),
                    summary = "",
                    colors = colors,
                    onClick = { }
                )
            }
        }

        if (filtered.isNotEmpty()) {
            SettingsSection(colors.cardBackground) {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    items(filtered) { domain ->
                        val enabled = domain !in disabledDomains
                        RowDivider(colors.divider)
                        SettingsRow(
                            icon = if (enabled) androidx.compose.material.icons.Icons.Filled.Visibility else androidx.compose.material.icons.Icons.Filled.VisibilityOff,
                            title = domain,
                            summary = if (enabled) stringResource(R.string.domain_blocker_status_on) else stringResource(R.string.domain_blocker_status_off),
                            colors = colors,
                            onClick = { },
                            trailing = {
                                Row(modifier = Modifier.padding(end = 4.dp)) {
                                    Switch(
                                        checked = enabled,
                                        onCheckedChange = { on ->
                                            val next = disabledDomains.toMutableSet()
                                            if (on) next.remove(domain) else next.add(domain)
                                            disabledDomains = next
                                            ExtraToolingManager.saveDisabledDomains(context, next)
                                        },
                                        colors = androidx.compose.material3.SwitchDefaults.colors(
                                            checkedTrackColor = colors.primary,
                                            uncheckedTrackColor = colors.divider,
                                            checkedThumbColor = colors.onPrimary
                                        )
                                    )
                                    androidx.compose.material3.IconButton(
                                        onClick = {
                                            ExtraToolingManager.removeDomain(context, domain)
                                            domains = ExtraToolingManager.getBlockedDomains(context)
                                            disabledDomains = ExtraToolingManager.getDisabledDomains(context)
                                            toast = context.getString(R.string.domain_blocker_status_removed, domain)
                                        }
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = stringResource(R.string.user_scripts_delete),
                                            tint = colors.colorError
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    toast?.let { t ->
        Toast(t)
        toast = null
    }
}

// ─────────────────────────────── User Scripts ────────────────────────────────

@Composable
fun UserScriptsPane() {
    val colors = LocalAlexToolColors.current
    val context = LocalContext.current
    var scripts by remember { mutableStateOf(UserScriptStore.loadUserScripts(context)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<UserScriptStore.UserScript?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }

    fun refresh() { scripts = UserScriptStore.loadUserScripts(context) }

    SettingsScreenScaffold {
        SettingsSection(colors.cardBackground) {
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.ContentPaste,
                title = stringResource(R.string.user_scripts_import_from_clipboard),
                summary = stringResource(R.string.user_scripts_add),
                colors = colors,
                onClick = { showImportDialog = true }
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.CloudDownload,
                title = stringResource(R.string.user_scripts_install_from_url),
                summary = stringResource(R.string.user_scripts_install_url_hint),
                colors = colors,
                onClick = { showUrlDialog = true }
            )
            RowDivider(colors.divider)
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Filled.Edit,
                title = stringResource(R.string.user_scripts_add),
                summary = stringResource(R.string.user_scripts_new_script),
                colors = colors,
                onClick = {
                    editTarget = null
                    showAddDialog = true
                }
            )
        }

        if (scripts.isEmpty()) {
            SettingsSection(colors.cardBackground) {
                SettingsRow(
                    icon = androidx.compose.material.icons.Icons.Filled.VisibilityOff,
                    title = stringResource(R.string.user_scripts_empty),
                    summary = stringResource(R.string.user_scripts_quick_ref),
                    colors = colors,
                    onClick = { }
                )
            }
        } else {
            SettingsSection(colors.cardBackground) {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    items(scripts) { script ->
                        SettingsRow(
                            icon = androidx.compose.material.icons.Icons.Filled.Visibility,
                            title = script.name,
                            summary = "${script.matches.size} match(es) · ${script.runAt}",
                            colors = colors,
                            onClick = { },
                            trailing = {
                                Row(modifier = Modifier.padding(end = 4.dp)) {
                                    Switch(
                                        checked = script.enabled,
                                        onCheckedChange = { on ->
                                            val all = UserScriptStore.loadUserScripts(context)
                                            val idx = all.indexOfFirst { it.id == script.id }
                                            if (idx >= 0) {
                                                all[idx].enabled = on
                                                UserScriptStore.saveUserScripts(context, all)
                                                refresh()
                                            }
                                        },
                                        colors = androidx.compose.material3.SwitchDefaults.colors(
                                            checkedTrackColor = colors.primary,
                                            uncheckedTrackColor = colors.divider,
                                            checkedThumbColor = colors.onPrimary
                                        )
                                    )
                                    androidx.compose.material3.IconButton(
                                        onClick = {
                                            val updated = UserScriptFetcher.checkUpdates(context)
                                            if (script.id in updated) {
                                                val newScript = UserScriptFetcher.installFromUrl(
                                                    context, script.downloadUrl.ifEmpty { script.updateUrl }
                                                )
                                                toast = if (newScript != null) {
                                                    context.getString(R.string.user_scripts_status_updated, newScript.name)
                                                } else {
                                                    context.getString(R.string.user_scripts_status_update_failed)
                                                }
                                                refresh()
                                            } else {
                                                toast = context.getString(R.string.user_scripts_status_up_to_date)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Filled.Refresh,
                                            contentDescription = stringResource(R.string.user_scripts_update),
                                            tint = colors.iconTint
                                        )
                                    }
                                    androidx.compose.material3.IconButton(
                                        onClick = {
                                            val content = script.toUserJsText()
                                            copyToClipboard(context, content)
                                            toast = context.getString(R.string.user_scripts_status_exported, script.name)
                                        }
                                    ) {
                                        Icon(
                                            Icons.Filled.Share,
                                            contentDescription = stringResource(R.string.user_scripts_export),
                                            tint = colors.iconTint
                                        )
                                    }
                                    androidx.compose.material3.IconButton(
                                        onClick = {
                                            editTarget = script
                                            showAddDialog = true
                                        }
                                    ) {
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = stringResource(R.string.user_scripts_copy),
                                            tint = colors.iconTint
                                        )
                                    }
                                    androidx.compose.material3.IconButton(
                                        onClick = {
                                            val all = UserScriptStore.loadUserScripts(context)
                                            val keep = all.filter { it.id != script.id }
                                            UserScriptStore.saveUserScripts(context, keep)
                                            toast = context.getString(R.string.user_scripts_status_deleted, script.name)
                                            refresh()
                                        }
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = stringResource(R.string.user_scripts_delete),
                                            tint = colors.colorError
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip
        val text = clip?.getItemAt(0)?.text?.toString()?.trim()
        AlexToolDialog(
            title = stringResource(R.string.user_scripts_import_from_clipboard),
            hideStatusBar = false,
            onDismiss = { showImportDialog = false }
        ) {
            if (text.isNullOrBlank() || !text.contains("UserScript")) {
                Text(
                    stringResource(R.string.user_scripts_status_clipboard_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryText,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                val parsed = UserScriptStore.parse(text)
                val duplicate = UserScriptStore.loadUserScripts(context).firstOrNull { it.name.equals(parsed.name, ignoreCase = true) }
                if (duplicate != null) {
                    Text(
                        context.getString(R.string.user_scripts_import_replace_hint, parsed.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.secondaryText,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showImportDialog = false }) {
                        Text(stringResource(R.string.user_scripts_cancel), color = colors.primary)
                    }
                    Button(
                        onClick = {
                            val all = UserScriptStore.loadUserScripts(context)
                            val idx = all.indexOfFirst { it.name.equals(parsed.name, ignoreCase = true) }
                            if (idx >= 0) {
                                parsed.id = all[idx].id
                                parsed.enabled = all[idx].enabled
                                all[idx] = parsed
                                toast = context.getString(R.string.user_scripts_status_imported, parsed.name)
                            } else {
                                all.add(parsed)
                                toast = context.getString(R.string.user_scripts_status_imported, parsed.name)
                            }
                            UserScriptStore.saveUserScripts(context, all)
                            showImportDialog = false
                            refresh()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.buttonBackground)
                    ) {
                        Text(
                            if (duplicate != null) stringResource(R.string.user_scripts_replace)
                            else stringResource(R.string.user_scripts_install),
                            color = colors.buttonTextColor
                        )
                    }
                }
            }
        }
    }

    if (showUrlDialog) {
        ScriptUrlDialog(
            colors = colors,
            context = context,
            onInstall = { url ->
                toast = context.getString(R.string.user_scripts_status_installing)
                Thread {
                    val installed = UserScriptFetcher.installFromUrl(context, url)
                    toast = if (installed != null) {
                        context.getString(R.string.user_scripts_status_installed, installed.name)
                    } else {
                        context.getString(R.string.user_scripts_status_url_failed)
                    }
                    refresh()
                }.start()
                showUrlDialog = false
            },
            onDismiss = { showUrlDialog = false }
        )
    }

    if (showAddDialog) {
        ScriptEditDialog(
            existing = editTarget,
            colors = colors,
            context = context,
            onSave = { raw, isEdit ->
                val parsed = UserScriptStore.parse(raw)
                val all = UserScriptStore.loadUserScripts(context)
                if (isEdit && editTarget != null) {
                    val idx = all.indexOfFirst { it.id == editTarget!!.id }
                    if (idx >= 0) {
                        parsed.id = all[idx].id
                        parsed.enabled = all[idx].enabled
                        for (url in all[idx].requireUrls) {
                            val cached = all[idx].requireCache[url]
                            if (cached != null) parsed.requireCache[url] = cached
                        }
                        all[idx] = parsed
                    }
                    toast = context.getString(R.string.user_scripts_status_updated, parsed.name)
                } else {
                    all.add(parsed)
                    toast = context.getString(R.string.user_scripts_status_installed, parsed.name)
                }
                UserScriptStore.saveUserScripts(context, all)
                showAddDialog = false
                editTarget = null
                refresh()
            },
            onDismiss = {
                showAddDialog = false
                editTarget = null
            }
        )
    }

    toast?.let { t ->
        Toast(t)
        toast = null
    }
}

@Composable
private fun ScriptEditDialog(
    existing: UserScriptStore.UserScript?,
    colors: AlexToolColors,
    context: Context,
    onSave: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var raw by remember { mutableStateOf(existing?.toUserJsText() ?: DEFAULT_SCRIPT_TEMPLATE) }
    AlexToolDialog(
        title = stringResource(if (existing != null) R.string.user_scripts_edit_script else R.string.user_scripts_new_script),
        hideStatusBar = false,
        onDismiss = onDismiss,
        footer = {
            Row(Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.user_scripts_cancel), color = colors.primary, fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = {
                    if (raw.trim().isEmpty()) {
                        android.widget.Toast.makeText(context, context.getString(R.string.user_scripts_status_empty), android.widget.Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                        onSave(raw, existing != null)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.buttonBackground)
                ) {
                    Text(stringResource(if (existing != null) R.string.user_scripts_save else R.string.user_scripts_install), color = colors.buttonTextColor)
                }
            }
        }
    ) {
        TextField(
            value = raw,
            onValueChange = { raw = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = 420.dp)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            placeholder = { Text(stringResource(R.string.user_scripts_script_body_hint)) },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedTextColor = colors.onSurface,
                unfocusedTextColor = colors.onSurface,
                focusedContainerColor = colors.surfaceVariant,
                unfocusedContainerColor = colors.surfaceVariant,
                focusedLabelColor = colors.secondaryText,
                unfocusedLabelColor = colors.secondaryText,
                cursorColor = colors.primary
            )
        )
        Spacer(modifier = Modifier.padding(top = 8.dp))
        Text(
            stringResource(R.string.user_scripts_quick_ref),
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryText,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        )
    }
}

@Composable
private fun ScriptUrlDialog(
    colors: AlexToolColors,
    context: Context,
    onInstall: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    AlexToolDialog(
        title = stringResource(R.string.user_scripts_install_from_url),
        hideStatusBar = false,
        onDismiss = onDismiss,
        footer = {
            Row(Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.user_scripts_cancel), color = colors.primary, fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = {
                        val target = url.trim()
                        if (target.isEmpty() || !target.startsWith("http")) {
                            android.widget.Toast.makeText(context, context.getString(R.string.user_scripts_status_empty), android.widget.Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onInstall(target)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.buttonBackground)
                ) {
                    Text(stringResource(R.string.user_scripts_install), color = colors.buttonTextColor)
                }
            }
        }
    ) {
        TextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            placeholder = { Text("https://example.com/script.user.js") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedTextColor = colors.onSurface,
                unfocusedTextColor = colors.onSurface,
                focusedContainerColor = colors.surfaceVariant,
                unfocusedContainerColor = colors.surfaceVariant,
                focusedLabelColor = colors.secondaryText,
                unfocusedLabelColor = colors.secondaryText,
                cursorColor = colors.primary
            )
        )
        Text(
            stringResource(R.string.user_scripts_install_url_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryText,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
        )
    }
}

private const val DEFAULT_SCRIPT_TEMPLATE = """// ==UserScript==
// @name        My Script
// @namespace   alextool.toolkit
// @version     1.0
// @description Describe your script here
// @author      You
// @match       *://example.com/*
// @grant       GM_setValue
// @grant       GM_getValue
// @run-at      document-end
// ==/UserScript==

(function() {
    'use strict';

    // Your code here
    console.log('Script running on ' + location.href);

})();"""

// ───────────────────────────────── Toast ─────────────────────────────────────

@Composable
private fun Toast(message: String) {
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(message) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("AlexTool", text))
}
