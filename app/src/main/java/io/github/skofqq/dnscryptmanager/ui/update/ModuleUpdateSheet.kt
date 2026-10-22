package io.github.skofqq.dnscryptmanager.ui.update

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.skofqq.dnscryptmanager.R
import io.github.skofqq.dnscryptmanager.data.ModuleContract
import io.github.skofqq.dnscryptmanager.data.UpdateError
import io.github.skofqq.dnscryptmanager.ui.components.InfoRow

/**
 * The "Module version" entry in Settings. Like Boxy's About card, the value turns into
 * "current → latest" when update.json offers something newer.
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_system_update),
            contentDescription = null,
            tint = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.update_row_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    ModalBottomSheet(onDismissRequest = { if (!state.busy) onDismiss() }, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.update_sheet_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                text = stringResource(
                    R.string.update_current_version,
                    state.installed?.version ?: stringResource(R.string.value_unknown),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (state.phase) {
                UpdatePhase.IDLE, UpdatePhase.CHECKING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.update_checking), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                UpdatePhase.UP_TO_DATE -> {
                    Text(
                        stringResource(R.string.update_latest),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedButton(onClick = onCheck) { Text(stringResource(R.string.update_check_again)) }
                }

                UpdatePhase.CHECK_FAILED -> {
                    ErrorText(state.error)
                    OutlinedButton(onClick = onCheck) { Text(stringResource(R.string.update_retry)) }
                }

                UpdatePhase.AVAILABLE, UpdatePhase.INSTALL_FAILED -> {
                    ReleaseCard(state, onOpenUrl)
                    if (state.phase == UpdatePhase.INSTALL_FAILED) {
                        ErrorText(state.error)
                        InstallLog(state.log)
                    }
                    Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(
                                if (state.phase == UpdatePhase.INSTALL_FAILED) R.string.update_retry
                                else R.string.update_install
                            )
                        )
                    }
                    Text(
                        stringResource(R.string.update_install_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                UpdatePhase.DOWNLOADING -> {
                    ReleaseCard(state, onOpenUrl)
                    Text(
                        text = if (state.progress >= 0f) {
                            stringResource(R.string.update_downloading_percent, (state.progress * 100).toInt())
                        } else {
                            stringResource(R.string.update_downloading)
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (state.progress >= 0f) {
                        LinearWavyProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                UpdatePhase.INSTALLING -> {
                    Text(stringResource(R.string.update_installing), style = MaterialTheme.typography.labelLarge)
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    InstallLog(state.log)
                }

                UpdatePhase.INSTALLED -> {
                    Text(
                        stringResource(R.string.update_installed, state.release?.version.orEmpty()),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.update_reboot_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    InstallLog(state.log)
                    if (state.error != null) ErrorText(state.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onReboot, enabled = !state.rebooting) {
                            Text(stringResource(R.string.update_reboot))
                        }
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseCard(state: ModuleUpdateUiState, onOpenUrl: (String) -> Unit) {
    val release = state.release ?: return
    var expanded by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            InfoRow(
                label = stringResource(R.string.update_available),
                value = release.version,
                valueColor = MaterialTheme.colorScheme.primary,
            )
            InfoRow(label = stringResource(R.string.update_version_code), value = release.versionCode.toString())
            state.changelog?.let { text ->
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.update_whats_new),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else CHANGELOG_PREVIEW_LINES,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            }
            release.changelogUrl?.let { url ->
                TextButton(onClick = { onOpenUrl(url) }) { Text(stringResource(R.string.update_open_changelog)) }
            }
        }
    }
}

/** The installer's own words, newest line kept in view. */
@Composable
private fun InstallLog(lines: List<String>) {
    if (lines.isEmpty()) return
    val scroll = rememberScrollState()
    LaunchedEffect(lines.size) { scroll.animateScrollTo(scroll.maxValue) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = lines.joinToString("\n"),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
                .heightIn(max = 220.dp)
                .verticalScroll(scroll)
                .padding(12.dp),
        )
    }
}

@Composable
private fun ErrorText(error: UpdateError?) {
    Text(
        text = errorMessage(error),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
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
