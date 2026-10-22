package io.github.skofqq.dnscryptmanager.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.skofqq.dnscryptmanager.R
import io.github.skofqq.dnscryptmanager.data.AppSettings
import io.github.skofqq.dnscryptmanager.data.ModuleSettings
import io.github.skofqq.dnscryptmanager.ui.components.SectionHeader
import io.github.skofqq.dnscryptmanager.ui.components.SettingLabel
import io.github.skofqq.dnscryptmanager.ui.theme.ThemeMode
import io.github.skofqq.dnscryptmanager.ui.update.ModuleUpdateSheet
import io.github.skofqq.dnscryptmanager.ui.update.ModuleUpdateUiState
import io.github.skofqq.dnscryptmanager.ui.update.ModuleVersionRow
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    uiState: ModuleSettingsUiState,
    appSettings: AppSettings,
    advancedSupported: Boolean,
    runGidSupported: Boolean,
    mirrorSupported: Boolean,
    resetSupported: Boolean,
    autostartSupported: Boolean,
    onReset: () -> Unit,
    onEdit: (String, String) -> Unit,
    onDiscard: () -> Unit,
    onApply: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onPollInterval: (Int) -> Unit,
    updateState: ModuleUpdateUiState,
    onOpenUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (uiState.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Box
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (uiState.error != null) {
                item {
                    Card(
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.error_prefix, uiState.error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_module_header)) }

            item {
                Card {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        if (autostartSupported) {
                            SwitchRow(
                                title = stringResource(R.string.setting_autostart),
                                description = stringResource(R.string.setting_autostart_desc),
                                checked = uiState.boolean(ModuleSettings.AUTOSTART, true),
                                onCheckedChange = {
                                    onEdit(ModuleSettings.AUTOSTART, it.toString())
                                },
                            )
                            HorizontalDivider()
                        }
                        SwitchRow(
                            title = stringResource(R.string.setting_redirect_ipv4),
                            description = stringResource(R.string.setting_redirect_ipv4_desc),
                            checked = uiState.boolean(ModuleSettings.REDIRECT_IPV4, true),
                            onCheckedChange = {
                                onEdit(ModuleSettings.REDIRECT_IPV4, it.toString())
                            },
                        )
                        HorizontalDivider()
                        Ipv6Row(
                            selected = uiState.text(ModuleSettings.REDIRECT_IPV6, "auto"),
                            onSelect = { onEdit(ModuleSettings.REDIRECT_IPV6, it) },
                        )
                        HorizontalDivider()
                        SwitchRow(
                            title = stringResource(R.string.setting_tether),
                            description = stringResource(R.string.setting_tether_desc),
                            checked = uiState.boolean(ModuleSettings.TETHER_REDIRECT, false),
                            onCheckedChange = {
                                onEdit(ModuleSettings.TETHER_REDIRECT, it.toString())
                            },
                        )
                        HorizontalDivider()
                        SwitchRow(
                            title = stringResource(R.string.setting_private_dns),
                            description = stringResource(R.string.setting_private_dns_desc),
                            checked = uiState.boolean(ModuleSettings.DISABLE_PRIVATE_DNS, false),
                            onCheckedChange = {
                                onEdit(ModuleSettings.DISABLE_PRIVATE_DNS, it.toString())
                            },
                        )
                        if (mirrorSupported) {
                            HorizontalDivider()
                            SwitchRow(
                                title = stringResource(R.string.setting_mirror),
                                description = stringResource(R.string.setting_mirror_desc),
                                checked = uiState.boolean(ModuleSettings.MIRROR_CONFIG, false),
                                onCheckedChange = {
                                    onEdit(ModuleSettings.MIRROR_CONFIG, it.toString())
                                },
                            )
                        }
                        HorizontalDivider()
                        RestartDelayRow(
                            seconds = uiState.number(
                                ModuleSettings.RESTART_DELAY_MAX,
                                ModuleSettings.RESTART_DELAY_DEFAULT,
                            ),
                            onChange = {
                                onEdit(ModuleSettings.RESTART_DELAY_MAX, it.toString())
                            },
                        )
                    }
                }
            }

            if (resetSupported) {
                item {
                    var confirmReset by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = { confirmReset = true },
                        enabled = !uiState.applying,
                        modifier = Modifier.padding(start = 4.dp),
                    ) { Text(stringResource(R.string.settings_reset)) }

                    if (confirmReset) {
                        AlertDialog(
                            onDismissRequest = { confirmReset = false },
                            title = { Text(stringResource(R.string.settings_reset_title)) },
                            text = { Text(stringResource(R.string.settings_reset_message)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    confirmReset = false
                                    onReset()
                                }) { Text(stringResource(R.string.settings_reset)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmReset = false }) {
                                    Text(stringResource(R.string.action_cancel))
                                }
                            },
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.settings_restart_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }

            if (advancedSupported && runGidSupported) {
                item { SectionHeader(stringResource(R.string.settings_advanced_header)) }
                item {
                    Card {
                        RunGidRow(
                            value = uiState.text(ModuleSettings.RUN_GID, ""),
                            onEdit = { onEdit(ModuleSettings.RUN_GID, it) },
                        )
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_app_header)) }

            item {
                Card {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        ThemeRow(selected = appSettings.themeMode, onSelect = onThemeMode)
                        HorizontalDivider()
                        SwitchRow(
                            title = stringResource(R.string.setting_dynamic_color),
                            description = stringResource(R.string.setting_dynamic_color_desc),
                            checked = appSettings.dynamicColor,
                            onCheckedChange = onDynamicColor,
                        )
                        HorizontalDivider()
                        PollIntervalRow(
                            selected = appSettings.pollIntervalSeconds,
                            onSelect = onPollInterval,
                        )
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_about_header)) }

            item {
                Card {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        ModuleVersionRow(state = updateState, onClick = onOpenUpdate)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.pendingKeys.isNotEmpty(),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ApplyBar(
                pending = uiState.pendingKeys.size,
                applying = uiState.applying,
                onApply = onApply,
                onDiscard = onDiscard,
            )
        }
    }
}

@Composable
private fun ApplyBar(
    pending: Int,
    applying: Boolean,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pluralStringResource(R.plurals.settings_pending, pending, pending),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDiscard, enabled = !applying) {
                Text(stringResource(R.string.action_cancel))
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onApply, enabled = !applying) {
                Text(stringResource(R.string.settings_apply))
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingLabel(
            title = title,
            description = description,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun Ipv6Row(selected: String, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        SettingLabel(
            title = stringResource(R.string.setting_redirect_ipv6),
            description = stringResource(R.string.setting_redirect_ipv6_desc),
        )
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ModuleSettings.IPV6_MODES.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = selected == mode,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ModuleSettings.IPV6_MODES.size,
                    ),
                ) {
                    Text(stringResource(ipv6ModeLabel(mode)))
                }
            }
        }
    }
}

@Composable
private fun RestartDelayRow(seconds: Int, onChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingLabel(
                title = stringResource(R.string.setting_restart_delay),
                description = stringResource(R.string.setting_restart_delay_desc),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.value_seconds, seconds),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Slider(
            value = seconds.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = ModuleSettings.RESTART_DELAY_MIN_SECONDS.toFloat()..
                ModuleSettings.RESTART_DELAY_MAX_SECONDS.toFloat(),
        )
    }
}

private enum class RunGidMode { DEFAULT, NET_ADMIN, CUSTOM }

/**
 * `run_gid` only matters next to box_for_magisk, so it lives in the advanced section
 * and offers the two answers that make sense plus a free-form escape hatch.
 */
@Composable
private fun RunGidRow(value: String, onEdit: (String) -> Unit) {
    var customChosen by remember {
        mutableStateOf(value.isNotEmpty() && value != ModuleSettings.RUN_GID_NET_ADMIN)
    }
    var customText by remember {
        mutableStateOf(if (value != ModuleSettings.RUN_GID_NET_ADMIN) value else "")
    }
    var expanded by remember { mutableStateOf(false) }

    val mode = when {
        customChosen -> RunGidMode.CUSTOM
        value.isEmpty() -> RunGidMode.DEFAULT
        value == ModuleSettings.RUN_GID_NET_ADMIN -> RunGidMode.NET_ADMIN
        else -> RunGidMode.CUSTOM
    }
    val customValid = ModuleSettings.SPECS[ModuleSettings.RUN_GID]
        ?.normalize(customText) != null

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        SettingLabel(
            title = stringResource(R.string.setting_run_gid),
            description = stringResource(R.string.setting_run_gid_desc),
        )
        Spacer(Modifier.height(12.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = runGidLabel(mode),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(stringResource(R.string.setting_run_gid)) },
                trailingIcon = {
                    Icon(
                        painter = painterResource(
                            if (expanded) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down
                        ),
                        contentDescription = null,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                RunGidMode.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(runGidLabel(option)) },
                        onClick = {
                            expanded = false
                            when (option) {
                                RunGidMode.DEFAULT -> {
                                    customChosen = false
                                    onEdit("")
                                }

                                RunGidMode.NET_ADMIN -> {
                                    customChosen = false
                                    onEdit(ModuleSettings.RUN_GID_NET_ADMIN)
                                }

                                RunGidMode.CUSTOM -> {
                                    customChosen = true
                                    if (customText.isNotEmpty() && customValid) onEdit(customText)
                                }
                            }
                        },
                    )
                }
            }
        }

        if (mode == RunGidMode.CUSTOM) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = customText,
                onValueChange = { entered ->
                    customText = entered
                    val normalized = ModuleSettings.SPECS[ModuleSettings.RUN_GID]
                        ?.normalize(entered)
                    if (normalized != null) onEdit(normalized)
                },
                singleLine = true,
                isError = customText.isNotEmpty() && !customValid,
                label = { Text(stringResource(R.string.run_gid_custom_label)) },
                supportingText = {
                    if (customText.isNotEmpty() && !customValid) {
                        Text(stringResource(R.string.run_gid_invalid))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun runGidLabel(mode: RunGidMode): String = when (mode) {
    RunGidMode.DEFAULT -> stringResource(R.string.run_gid_default)
    RunGidMode.NET_ADMIN -> ModuleSettings.RUN_GID_NET_ADMIN
    RunGidMode.CUSTOM -> stringResource(R.string.run_gid_custom)
}

@Composable
private fun ThemeRow(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val modes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        SettingLabel(title = stringResource(R.string.setting_theme), description = null)
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = selected == mode,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                ) {
                    Text(
                        stringResource(
                            when (mode) {
                                ThemeMode.LIGHT -> R.string.theme_light
                                ThemeMode.DARK -> R.string.theme_dark
                                ThemeMode.SYSTEM -> R.string.theme_system
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PollIntervalRow(selected: Int, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        SettingLabel(
            title = stringResource(R.string.setting_poll),
            description = stringResource(R.string.setting_poll_desc),
        )
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            AppSettings.POLL_CHOICES.forEachIndexed { index, value ->
                SegmentedButton(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = AppSettings.POLL_CHOICES.size,
                    ),
                ) {
                    Text(stringResource(R.string.value_seconds, value))
                }
            }
        }
    }
}

private fun ipv6ModeLabel(mode: String) = when (mode) {
    "redirect" -> R.string.ipv6_redirect
    "off" -> R.string.ipv6_off
    else -> R.string.ipv6_auto
}
