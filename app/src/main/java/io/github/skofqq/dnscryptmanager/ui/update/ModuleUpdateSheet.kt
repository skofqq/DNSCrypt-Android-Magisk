package io.github.skofqq.dnscryptmanager.ui.update

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.skofqq.dnscryptmanager.R
import io.github.skofqq.dnscryptmanager.data.ModuleContract
import io.github.skofqq.dnscryptmanager.data.UpdateError
import io.github.skofqq.dnscryptmanager.ui.components.ActionPill
import io.github.skofqq.dnscryptmanager.ui.components.Badge
import io.github.skofqq.dnscryptmanager.ui.components.IconTile
import io.github.skofqq.dnscryptmanager.ui.components.InfoRow
import io.github.skofqq.dnscryptmanager.ui.components.NoticeCard
import io.github.skofqq.dnscryptmanager.ui.components.PrimaryPill
import io.github.skofqq.dnscryptmanager.ui.theme.Boxy
import io.github.skofqq.dnscryptmanager.ui.theme.Tints

/**
 * The "Module version" entry in Settings, laid out like Boxy's settings rows: icon tile,
 * title, the version line, a badge when update.json offers something newer.
 */
@Composable
fun ModuleVersionRow(state: ModuleUpdateUiState, onClick: () -> Unit) {
    val current = state.installed?.version
    val value = when {
        state.phase == UpdatePhase.INSTALLED ->
            stringResource(R.string.update_row_reboot_pending, state.release?.version.orEmpty())
        state.updateAvailable ->
            stringResource(R.string.update_row_available, current.orEmpty(), state.release?.version.orEmpty())
        state.phase == UpdatePhase.CHECKING && current == null -> stringResource(R.string.update_checking)
        else -> current ?: stringResource(R.string.value_unknown)
    }
    val highlight = state.updateAvailable || state.phase == UpdatePhase.INSTALLED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(
            painterResource(R.drawable.ic_system_update),
            tint = if (highlight) Boxy.colors.accent else Boxy.colors.text,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.update_row_title),
                style = MaterialTheme.typography.titleMedium,
                color = Boxy.colors.text,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = if (highlight) Boxy.colors.accent else Boxy.colors.text2,
            )
        }
        when {
            state.phase == UpdatePhase.INSTALLED -> Badge(stringResource(R.string.update_badge_reboot), Tints.amber)
            state.updateAvailable -> Badge(stringResource(R.string.update_badge_new), Tints.green)
            else -> Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Boxy.colors.text2.copy(alpha = 0.75f),
            )
        }
    }
}

/** Current and latest version, the changelog, and the install with its live log. */
@Composable
fun ModuleUpdateSheet(
    state: ModuleUpdateUiState,
    onDismiss: () -> Unit,
    onCheck: () -> Unit,
    onInstall: () -> Unit,
    onReboot: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    // A half-finished install must not be swiped away by accident.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !state.busy },
    )
    ModalBottomSheet(
        onDismissRequest = { if (!state.busy) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = Boxy.colors.page,
        contentColor = Boxy.colors.text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    stringResource(R.string.update_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = Boxy.colors.text,
                )
                Text(
                    text = stringResource(
                        R.string.update_current_version,
                        state.installed?.version ?: stringResource(R.string.value_unknown),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Boxy.colors.text2,
                )
            }

            when (state.phase) {
                UpdatePhase.IDLE, UpdatePhase.CHECKING -> SheetGroup {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.update_checking), color = Boxy.colors.text2)
                    }
                }

                UpdatePhase.UP_TO_DATE -> {
                    VersionsGroup(state)
                    NoticeCard(
                        title = stringResource(R.string.update_latest),
                        body = stringResource(R.string.update_install_note),
                        tint = Tints.green,
                        icon = painterResource(R.drawable.ic_shield),
                    )
                    SheetButtons(primary = stringResource(R.string.update_check_again), onPrimary = onCheck)
                }

                UpdatePhase.CHECK_FAILED -> {
                    ErrorNotice(state.error)
                    SheetButtons(primary = stringResource(R.string.update_retry), onPrimary = onCheck)
                }

                UpdatePhase.AVAILABLE, UpdatePhase.INSTALL_FAILED -> {
                    VersionsGroup(state)
                    Changelog(state, onOpenUrl)
                    if (state.phase == UpdatePhase.INSTALL_FAILED) {
                        ErrorNotice(state.error)
                        InstallLog(state.log)
                    }
                    Text(
                        stringResource(R.string.update_install_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = Boxy.colors.text2,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    SheetButtons(
                        primary = stringResource(
                            if (state.phase == UpdatePhase.INSTALL_FAILED) R.string.update_retry
                            else R.string.update_install
                        ),
                        onPrimary = onInstall,
                        secondary = stringResource(R.string.update_later),
                        onSecondary = onDismiss,
                    )
                }

                UpdatePhase.DOWNLOADING -> {
                    VersionsGroup(state)
                    SheetGroup {
                        Text(
                            text = if (state.progress >= 0f) {
                                stringResource(R.string.update_downloading_percent, (state.progress * 100).toInt())
                            } else {
                                stringResource(R.string.update_downloading)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = Boxy.colors.text,
                        )
                        Spacer(Modifier.height(10.dp))
                        if (state.progress >= 0f) {
                            LinearWavyProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                UpdatePhase.INSTALLING -> {
                    SheetGroup {
                        Text(
                            stringResource(R.string.update_installing),
                            style = MaterialTheme.typography.titleMedium,
                            color = Boxy.colors.text,
                        )
                        Spacer(Modifier.height(10.dp))
                        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    InstallLog(state.log)
                }

                UpdatePhase.INSTALLED -> {
                    NoticeCard(
                        title = stringResource(R.string.update_installed, state.release?.version.orEmpty()),
                        body = stringResource(R.string.update_reboot_hint),
                        tint = Tints.green,
                        icon = painterResource(R.drawable.ic_system_update),
                    )
                    InstallLog(state.log)
                    if (state.error != null) ErrorNotice(state.error)
                    SheetButtons(
                        primary = stringResource(R.string.update_reboot),
                        onPrimary = onReboot,
                        primaryEnabled = !state.rebooting,
                        secondary = stringResource(R.string.update_later),
                        onSecondary = onDismiss,
                    )
                }
            }
        }
    }
}

/** Card-like group inside a sheet, as in Boxy. */
@Composable
private fun SheetGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Boxy.colors.card)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        content = content,
    )
}

/** Installed and latest version side by side, with the badge Boxy puts on a newer one. */
@Composable
private fun VersionsGroup(state: ModuleUpdateUiState) {
    val release = state.release
    SheetGroup {
        InfoRow(
            label = stringResource(R.string.update_installed_version),
            value = state.installed?.version ?: stringResource(R.string.value_unknown),
        )
        if (release != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InfoRow(
                    label = stringResource(R.string.update_latest_version),
                    value = release.version,
                    valueColor = if (state.updateAvailable) Tints.green.fg else Boxy.colors.text,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                if (state.updateAvailable) {
                    Badge(stringResource(R.string.update_badge_new), Tints.green)
                } else {
                    Badge(stringResource(R.string.update_badge_latest), Tints.gray)
                }
            }
            InfoRow(label = stringResource(R.string.update_version_code), value = release.versionCode.toString())
        }
    }
}

@Composable
private fun Changelog(state: ModuleUpdateUiState, onOpenUrl: (String) -> Unit) {
    val release = state.release ?: return
    var expanded by remember { mutableStateOf(false) }
    if (state.changelog == null && release.changelogUrl == null) return
    SheetGroup {
        Text(
            stringResource(R.string.update_whats_new),
            style = MaterialTheme.typography.titleMedium,
            color = Boxy.colors.text,
        )
        state.changelog?.let { text ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = Boxy.colors.text2,
                maxLines = if (expanded) Int.MAX_VALUE else CHANGELOG_PREVIEW_LINES,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
        release.changelogUrl?.let { url ->
            Spacer(Modifier.height(10.dp))
            ActionPill(
                stringResource(R.string.update_open_changelog),
                Tints.blue,
                enabled = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpenUrl(url) },
            )
        }
    }
}

/** Two buttons at the bottom of a sheet: neutral secondary, accent primary. */
@Composable
private fun SheetButtons(
    primary: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean = true,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (secondary != null) {
            PrimaryPill(
                secondary,
                Modifier.weight(1f),
                color = Boxy.colors.card,
                contentColor = Boxy.colors.text,
                onClick = onSecondary,
            )
        }
        PrimaryPill(primary, Modifier.weight(1f), enabled = primaryEnabled, onClick = onPrimary)
    }
}

/** The installer's own words, newest line kept in view. */
@Composable
private fun InstallLog(lines: List<String>) {
    if (lines.isEmpty()) return
    val scroll = rememberScrollState()
    LaunchedEffect(lines.size) { scroll.animateScrollTo(scroll.maxValue) }
    SheetGroup {
        Text(
            stringResource(R.string.update_install_log),
            style = MaterialTheme.typography.titleMedium,
            color = Boxy.colors.text,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = lines.joinToString("\n"),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = Boxy.colors.text,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Boxy.colors.surface2)
                .verticalScroll(scroll)
                .padding(12.dp),
        )
    }
}

@Composable
private fun ErrorNotice(error: UpdateError?) {
    NoticeCard(
        title = stringResource(R.string.update_error_title),
        body = errorMessage(error),
        tint = Tints.red,
        icon = painterResource(R.drawable.ic_warning),
    )
}

@Composable
private fun errorMessage(error: UpdateError?): String = when (error) {
    null -> stringResource(R.string.update_error_unknown)
    UpdateError.NoNetwork -> stringResource(R.string.update_error_network)
    is UpdateError.Http -> stringResource(R.string.update_error_http, error.code)
    UpdateError.BadUpdateJson -> stringResource(R.string.update_error_json)
    UpdateError.NoRoot -> stringResource(R.string.error_no_root)
    UpdateError.NotInstalled -> stringResource(R.string.update_error_not_installed)
    UpdateError.BadPackage -> stringResource(R.string.update_error_package)
    is UpdateError.WrongModule -> stringResource(R.string.update_error_wrong_module, error.id ?: "?", ModuleContract.MODULE_ID)
    is UpdateError.MissingBinary -> stringResource(R.string.update_error_arch, error.arch)
    UpdateError.NoRootManager -> stringResource(R.string.update_error_manager)
    is UpdateError.InstallFailed -> stringResource(R.string.update_error_install, error.code)
    UpdateError.Timeout -> stringResource(R.string.error_timeout)
    is UpdateError.Other -> error.message
}

private const val CHANGELOG_PREVIEW_LINES = 12
