package io.github.skofqq.dnscryptmanager.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import io.github.skofqq.dnscryptmanager.data.ModuleContract
import io.github.skofqq.dnscryptmanager.data.ModuleSettings
import io.github.skofqq.dnscryptmanager.ui.components.BoxyDialog
import io.github.skofqq.dnscryptmanager.ui.components.Choice
import io.github.skofqq.dnscryptmanager.ui.components.ChoiceDialog
import io.github.skofqq.dnscryptmanager.ui.components.LocalBarPadding
import io.github.skofqq.dnscryptmanager.ui.components.NavRow
import io.github.skofqq.dnscryptmanager.ui.components.NoticeCard
import io.github.skofqq.dnscryptmanager.ui.components.PageInset
import io.github.skofqq.dnscryptmanager.ui.components.PendingBar
import io.github.skofqq.dnscryptmanager.ui.components.SectionCard
import io.github.skofqq.dnscryptmanager.ui.components.SwitchRow
import io.github.skofqq.dnscryptmanager.ui.components.TextInset
import io.github.skofqq.dnscryptmanager.ui.theme.Boxy
import io.github.skofqq.dnscryptmanager.ui.theme.ThemeMode
import io.github.skofqq.dnscryptmanager.ui.theme.Tints
import io.github.skofqq.dnscryptmanager.ui.update.ModuleUpdateUiState
import io.github.skofqq.dnscryptmanager.ui.update.ModuleVersionRow
import kotlin.math.roundToInt

/** Which of the value dialogs is open; one at a time. */
private enum class SettingDialog { IPV6, RESTART_DELAY, RUN_GID, RUN_GID_CUSTOM, THEME, POLL, RESET }

/**
 * Settings in Boxy's layout: titled cards with icon rows. Choices open a dialog instead
 * of sitting in the row as segmented buttons; servers and the config editor are sub-pages.
 */
@Composable
fun SettingsScreen(
    uiState: ModuleSettingsUiState,
    appSettings: AppSettings,
    serversSupported: Boolean,
    serversSummary: String,
    advancedSupported: Boolean,
    runGidSupported: Boolean,
    mirrorSupported: Boolean,
    resetSupported: Boolean,
    autostartSupported: Boolean,
    onOpenServers: () -> Unit,
    onOpenConfig: () -> Unit,
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
    var dialog by remember { mutableStateOf<SettingDialog?>(null) }
    val barPadding = LocalBarPadding.current

    Box(modifier = modifier.fillMaxSize()) {
        if (uiState.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Box
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = PageInset,
                end = PageInset,
                top = 4.dp,
                bottom = barPadding + if (uiState.pendingKeys.isNotEmpty()) 110.dp else 0.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (uiState.error != null) {
                item {
                    NoticeCard(
                        title = stringResource(R.string.settings_error_title),
                        body = stringResource(R.string.error_prefix, uiState.error),
                        tint = Tints.red,
                        icon = painterResource(R.drawable.ic_warning),
                    )
                }
            }

            item {
                SectionCard(
                    title = stringResource(R.string.settings_proxy_header),
                    subtitle = stringResource(R.string.settings_proxy_sub),
                ) {
                    NavRow(
                        icon = painterResource(R.drawable.ic_dns),
                        title = stringResource(R.string.nav_servers),
                        subtitle = if (serversSupported) {
                            serversSummary
                        } else {
                            stringResource(R.string.settings_needs_module, ModuleContract.MIN_SERVERS_VERSION)
                        },
                        enabled = serversSupported,
                        onClick = onOpenServers,
                    )
                    NavRow(
                        icon = painterResource(R.drawable.ic_article),
                        title = stringResource(R.string.settings_config_row),
                        subtitle = ModuleContract.CONFIG_NAME,
                        showDivider = false,
                        onClick = onOpenConfig,
                    )
                }
            }

            item {
                SectionCard(
                    title = stringResource(R.string.settings_module_header),
                    subtitle = stringResource(R.string.settings_module_sub),
                ) {
                    if (autostartSupported) {
                        SwitchRow(
                            icon = painterResource(R.drawable.ic_power),
                            title = stringResource(R.string.setting_autostart),
                            subtitle = stringResource(R.string.setting_autostart_short),
                            checked = uiState.boolean(ModuleSettings.AUTOSTART, true),
                            onCheckedChange = { onEdit(ModuleSettings.AUTOSTART, it.toString()) },
                        )
                    }
                    SwitchRow(
                        icon = painterResource(R.drawable.ic_swap),
                        title = stringResource(R.string.setting_redirect_ipv4),
                        subtitle = stringResource(R.string.setting_redirect_ipv4_short),
                        checked = uiState.boolean(ModuleSettings.REDIRECT_IPV4, true),
                        onCheckedChange = { onEdit(ModuleSettings.REDIRECT_IPV4, it.toString()) },
                    )
                    NavRow(
                        icon = painterResource(R.drawable.ic_globe),
                        title = stringResource(R.string.setting_redirect_ipv6),
                        subtitle = stringResource(ipv6ModeLabel(uiState.text(ModuleSettings.REDIRECT_IPV6, "auto"))),
                        onClick = { dialog = SettingDialog.IPV6 },
                    )
                    SwitchRow(
                        icon = painterResource(R.drawable.ic_hotspot),
                        title = stringResource(R.string.setting_tether),
                        subtitle = stringResource(R.string.setting_tether_short),
                        checked = uiState.boolean(ModuleSettings.TETHER_REDIRECT, false),
                        onCheckedChange = { onEdit(ModuleSettings.TETHER_REDIRECT, it.toString()) },
                    )
                    SwitchRow(
                        icon = painterResource(R.drawable.ic_lock),
                        title = stringResource(R.string.setting_private_dns),
                        subtitle = stringResource(R.string.setting_private_dns_short),
                        checked = uiState.boolean(ModuleSettings.DISABLE_PRIVATE_DNS, false),
                        onCheckedChange = { onEdit(ModuleSettings.DISABLE_PRIVATE_DNS, it.toString()) },
                    )
                    if (mirrorSupported) {
                        SwitchRow(
                            icon = painterResource(R.drawable.ic_backup),
                            title = stringResource(R.string.setting_mirror),
                            subtitle = stringResource(R.string.setting_mirror_desc),
                            checked = uiState.boolean(ModuleSettings.MIRROR_CONFIG, false),
                            onCheckedChange = { onEdit(ModuleSettings.MIRROR_CONFIG, it.toString()) },
                        )
                    }
                    NavRow(
                        icon = painterResource(R.drawable.ic_timer),
                        title = stringResource(R.string.setting_restart_delay),
                        subtitle = stringResource(
                            R.string.value_seconds,
                            uiState.number(ModuleSettings.RESTART_DELAY_MAX, ModuleSettings.RESTART_DELAY_DEFAULT),
                        ),
                        showDivider = resetSupported,
                        onClick = { dialog = SettingDialog.RESTART_DELAY },
                    )
                    if (resetSupported) {
                        NavRow(
                            icon = painterResource(R.drawable.ic_restore),
                            title = stringResource(R.string.settings_reset),
                            subtitle = stringResource(R.string.settings_restart_note),
                            iconTint = Tints.red.fg,
                            enabled = !uiState.applying,
                            showDivider = false,
                            onClick = { dialog = SettingDialog.RESET },
                        )
                    }
                }
            }

            if (advancedSupported && runGidSupported) {
                item {
                    SectionCard(
                        title = stringResource(R.string.settings_advanced_header),
                        subtitle = stringResource(R.string.settings_advanced_sub),
                    ) {
                        NavRow(
                            icon = painterResource(R.drawable.ic_group),
                            title = stringResource(R.string.setting_run_gid),
                            subtitle = runGidValue(uiState.text(ModuleSettings.RUN_GID, "")),
                            showDivider = false,
                            onClick = { dialog = SettingDialog.RUN_GID },
                        )
                    }
                }
            }

            item {
                SectionCard(
                    title = stringResource(R.string.settings_app_header),
                    subtitle = stringResource(R.string.settings_app_sub),
                ) {
                    NavRow(
                        icon = painterResource(R.drawable.ic_palette),
                        title = stringResource(R.string.setting_theme),
                        subtitle = stringResource(themeLabel(appSettings.themeMode)),
                        onClick = { dialog = SettingDialog.THEME },
                    )
                    SwitchRow(
                        icon = painterResource(R.drawable.ic_wallpaper),
                        title = stringResource(R.string.setting_dynamic_color),
                        subtitle = stringResource(R.string.setting_dynamic_color_desc),
                        checked = appSettings.dynamicColor,
                        onCheckedChange = onDynamicColor,
                    )
                    NavRow(
                        icon = painterResource(R.drawable.ic_schedule),
                        title = stringResource(R.string.setting_poll),
                        subtitle = stringResource(R.string.value_seconds, appSettings.pollIntervalSeconds),
                        showDivider = false,
                        onClick = { dialog = SettingDialog.POLL },
                    )
                }
            }

            item {
                SectionCard(
                    title = stringResource(R.string.settings_about_header),
                    subtitle = stringResource(R.string.settings_about_sub),
                ) {
                    ModuleVersionRow(state = updateState, onClick = onOpenUpdate)
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.pendingKeys.isNotEmpty(),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = barPadding - 16.dp),
        ) {
            Column {
                if (uiState.applying) {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 32.dp))
                }
                PendingBar(
                    label = pluralStringResource(R.plurals.settings_pending, uiState.pendingKeys.size, uiState.pendingKeys.size) +
                        " · " + stringResource(R.string.settings_restart_note),
                    applying = uiState.applying,
                    applyText = stringResource(R.string.settings_apply),
                    onDiscard = onDiscard,
                    onApply = onApply,
                )
            }
        }
    }

    when (dialog) {
        SettingDialog.IPV6 -> ChoiceDialog(
            title = stringResource(R.string.setting_redirect_ipv6),
            choices = listOf(
                Choice("auto", stringResource(R.string.ipv6_auto), stringResource(R.string.setting_redirect_ipv6_desc)),
                Choice("redirect", stringResource(R.string.ipv6_redirect), stringResource(R.string.ipv6_redirect_desc)),
                Choice("off", stringResource(R.string.ipv6_off), stringResource(R.string.ipv6_off_desc)),
            ),
            selected = uiState.text(ModuleSettings.REDIRECT_IPV6, "auto"),
            onSelect = { onEdit(ModuleSettings.REDIRECT_IPV6, it) },
            onDismiss = { dialog = null },
        )

        SettingDialog.RESTART_DELAY -> RestartDelayDialog(
            seconds = uiState.number(ModuleSettings.RESTART_DELAY_MAX, ModuleSettings.RESTART_DELAY_DEFAULT),
            onConfirm = { onEdit(ModuleSettings.RESTART_DELAY_MAX, it.toString()) },
            onDismiss = { dialog = null },
        )

        SettingDialog.RUN_GID -> {
            val current = uiState.text(ModuleSettings.RUN_GID, "")
            ChoiceDialog(
                title = stringResource(R.string.setting_run_gid),
                choices = listOf(
                    Choice("", stringResource(R.string.run_gid_default), stringResource(R.string.run_gid_default_desc)),
                    Choice(ModuleSettings.RUN_GID_NET_ADMIN, ModuleSettings.RUN_GID_NET_ADMIN, stringResource(R.string.setting_run_gid_desc)),
                    Choice(CUSTOM, stringResource(R.string.run_gid_custom), stringResource(R.string.run_gid_custom_label)),
                ),
                selected = if (current.isEmpty() || current == ModuleSettings.RUN_GID_NET_ADMIN) current else CUSTOM,
                onSelect = { if (it == CUSTOM) dialog = SettingDialog.RUN_GID_CUSTOM else onEdit(ModuleSettings.RUN_GID, it) },
                onDismiss = { if (dialog == SettingDialog.RUN_GID) dialog = null },
            )
        }

        SettingDialog.RUN_GID_CUSTOM -> RunGidCustomDialog(
            initial = uiState.text(ModuleSettings.RUN_GID, "").takeIf { it != ModuleSettings.RUN_GID_NET_ADMIN }.orEmpty(),
            onConfirm = { onEdit(ModuleSettings.RUN_GID, it) },
            onDismiss = { dialog = null },
        )

        SettingDialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.setting_theme),
            choices = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK).map { Choice(it, stringResource(themeLabel(it))) },
            selected = appSettings.themeMode,
            onSelect = onThemeMode,
            onDismiss = { dialog = null },
        )

        SettingDialog.POLL -> ChoiceDialog(
            title = stringResource(R.string.setting_poll),
            choices = AppSettings.POLL_CHOICES.map { Choice(it, stringResource(R.string.value_seconds, it)) },
            selected = appSettings.pollIntervalSeconds,
            onSelect = onPollInterval,
            onDismiss = { dialog = null },
        )

        SettingDialog.RESET -> BoxyDialog(
            title = stringResource(R.string.settings_reset_title),
            text = stringResource(R.string.settings_reset_message),
            onDismiss = { dialog = null },
            dismissText = stringResource(R.string.action_cancel),
            confirmText = stringResource(R.string.settings_reset),
            confirmTint = Tints.red,
            onConfirm = {
                dialog = null
                onReset()
            },
        )

        null -> Unit
    }
}

/** Marker for the "custom value" choice; never written to module.conf. */
private const val CUSTOM = "\u0000custom"

@Composable
private fun RestartDelayDialog(seconds: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableIntStateOf(seconds) }
    BoxyDialog(
        title = stringResource(R.string.setting_restart_delay),
        text = stringResource(R.string.setting_restart_delay_desc),
        onDismiss = onDismiss,
        dismissText = stringResource(R.string.action_cancel),
        confirmText = stringResource(R.string.action_ok),
        onConfirm = {
            onConfirm(value)
            onDismiss()
        },
    ) {
        Text(
            stringResource(R.string.value_seconds, value),
            style = MaterialTheme.typography.headlineMedium,
            color = Boxy.colors.text,
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { value = it.roundToInt() },
            valueRange = ModuleSettings.RESTART_DELAY_MIN_SECONDS.toFloat()..ModuleSettings.RESTART_DELAY_MAX_SECONDS.toFloat(),
        )
    }
}

@Composable
private fun RunGidCustomDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val normalized = ModuleSettings.SPECS[ModuleSettings.RUN_GID]?.normalize(text)
    BoxyDialog(
        title = stringResource(R.string.run_gid_custom),
        onDismiss = onDismiss,
        dismissText = stringResource(R.string.action_cancel),
        confirmText = stringResource(R.string.action_ok),
        onConfirm = {
            if (normalized != null) {
                onConfirm(normalized)
                onDismiss()
            }
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            isError = text.isNotEmpty() && normalized == null,
            label = { Text(stringResource(R.string.run_gid_custom_label)) },
            supportingText = {
                if (text.isNotEmpty() && normalized == null) Text(stringResource(R.string.run_gid_invalid))
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun runGidValue(value: String): String = when {
    value.isEmpty() -> stringResource(R.string.run_gid_default)
    else -> value
}

private fun themeLabel(mode: ThemeMode) = when (mode) {
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.SYSTEM -> R.string.theme_system
}

private fun ipv6ModeLabel(mode: String) = when (mode) {
    "redirect" -> R.string.ipv6_redirect
    "off" -> R.string.ipv6_off
    else -> R.string.ipv6_auto
}
