package io.github.skofqq.dnscryptmanager.ui.config

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.skofqq.dnscryptmanager.R
import io.github.skofqq.dnscryptmanager.data.ConfigBackup
import io.github.skofqq.dnscryptmanager.ui.theme.MonospaceTextStyle

@Composable
fun ConfigScreen(
    uiState: ConfigUiState,
    onTextChanged: (String) -> Unit,
    onSave: () -> Unit,
    onBackup: () -> Unit,
    onRestore: (ConfigBackup) -> Unit,
    mirrorSupported: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        return
    }

    var field by remember(uiState.loadToken) {
        mutableStateOf(TextFieldValue(uiState.text))
    }
    var query by remember { mutableStateOf("") }
    var matchIndex by remember { mutableIntStateOf(0) }
    var showBackups by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<ConfigBackup?>(null) }

    val matches = remember(field.text, query) {
        if (query.length < 2) emptyList() else findMatches(field.text, query)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.config_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (uiState.dirty) {
                    Text(
                        text = stringResource(R.string.config_unsaved),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            SearchBar(
                query = query,
                matches = matches.size,
                matchIndex = matchIndex,
                onQuery = {
                    query = it
                    matchIndex = 0
                },
                onJump = { forward ->
                    if (matches.isNotEmpty()) {
                        matchIndex = (matchIndex + if (forward) 1 else matches.size - 1) % matches.size
                        val start = matches[matchIndex]
                        field = field.copy(selection = TextRange(start, start + query.length))
                    }
                },
            )

            if (uiState.busy) {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (uiState.error != null) {
                Text(
                    text = stringResource(R.string.error_prefix, uiState.error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            TextField(
                value = field,
                onValueChange = {
                    field = it
                    onTextChanged(it.text)
                },
                textStyle = MonospaceTextStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }

        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
        ) {
            FilledIconButton(onClick = onSave, enabled = !uiState.busy) {
                Icon(
                    painter = painterResource(R.drawable.ic_save),
                    contentDescription = stringResource(R.string.config_save_restart),
                )
            }
            IconButton(onClick = onBackup, enabled = !uiState.busy) {
                Icon(
                    painter = painterResource(R.drawable.ic_backup),
                    contentDescription = stringResource(R.string.config_create_backup),
                )
            }
            IconButton(onClick = { showBackups = true }, enabled = !uiState.busy) {
                Icon(
                    painter = painterResource(R.drawable.ic_restore),
                    contentDescription = stringResource(R.string.config_backups),
                )
            }
            // Since module 2.3 there is no shared-storage copy to exchange with.
            if (mirrorSupported) {
                IconButton(onClick = onExport, enabled = !uiState.busy) {
                    Icon(
                        painter = painterResource(R.drawable.ic_article),
                        contentDescription = stringResource(R.string.config_export),
                    )
                }
                IconButton(onClick = onImport, enabled = !uiState.busy) {
                    Icon(
                        painter = painterResource(R.drawable.ic_terminal),
                        contentDescription = stringResource(R.string.config_import),
                    )
                }
            }
        }
    }

    if (showBackups) {
        BackupSheet(
            backups = uiState.backups,
            onDismiss = { showBackups = false },
            onPick = { pendingRestore = it },
        )
    }

    pendingRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.confirm_restore_title)) },
            text = { Text(stringResource(R.string.confirm_restore_message, backup.name)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    showBackups = false
                    onRestore(backup)
                }) { Text(stringResource(R.string.action_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    matches: Int,
    matchIndex: Int,
    onQuery: (String) -> Unit,
    onJump: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            label = { Text(stringResource(R.string.config_search_hint)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.action_clear),
                        )
                    }
                }
            },
            modifier = Modifier.weight(1f),
        )
        if (query.length >= 2) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (matches == 0) {
                    stringResource(R.string.config_no_matches)
                } else {
                    stringResource(R.string.config_match_count, matchIndex + 1, matches)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = { onJump(false) }, enabled = matches > 0) {
                Icon(
                    painterResource(R.drawable.ic_arrow_up),
                    contentDescription = stringResource(R.string.action_previous_match),
                )
            }
            IconButton(onClick = { onJump(true) }, enabled = matches > 0) {
                Icon(
                    painterResource(R.drawable.ic_arrow_down),
                    contentDescription = stringResource(R.string.action_next_match),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupSheet(
    backups: List<ConfigBackup>,
    onDismiss: () -> Unit,
    onPick: (ConfigBackup) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = stringResource(R.string.backups_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))
            if (backups.isEmpty()) {
                Text(
                    text = stringResource(R.string.backups_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 32.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 32.dp),
                ) {
                    items(backups, key = { it.path }) { backup ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = backup.name,
                                    style = MonospaceTextStyle,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (backup.isPending) {
                                    Text(
                                        text = stringResource(R.string.backup_pending),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                            }
                            TextButton(onClick = { onPick(backup) }) {
                                Text(stringResource(R.string.action_restore))
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** Case-insensitive occurrences of [query] in [text]. */
fun findMatches(text: String, query: String): List<Int> {
    if (query.isEmpty()) return emptyList()
    val result = ArrayList<Int>()
    var index = text.indexOf(query, startIndex = 0, ignoreCase = true)
    while (index >= 0) {
        result.add(index)
        index = text.indexOf(query, startIndex = index + 1, ignoreCase = true)
    }
    return result
}
