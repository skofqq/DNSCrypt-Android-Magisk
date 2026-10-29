package com.skofqq.shieldy.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.skofqq.shieldy.R
import com.skofqq.shieldy.data.ConfigBackup
import com.skofqq.shieldy.ui.components.ActionPill
import com.skofqq.shieldy.ui.components.BoxyDialog
import com.skofqq.shieldy.ui.components.HeaderAction
import com.skofqq.shieldy.ui.components.NoticeCard
import com.skofqq.shieldy.ui.components.PageInset
import com.skofqq.shieldy.ui.components.SearchField
import com.skofqq.shieldy.ui.components.SubPageHeader
import com.skofqq.shieldy.ui.theme.Boxy
import com.skofqq.shieldy.ui.theme.MonospaceTextStyle
import com.skofqq.shieldy.ui.theme.Tints

/**
 * The config editor, a sub-page of Settings. Save, search and the backup tools sit in
 * the header like Boxy's editor, so nothing floats over the text.
 */
@Composable
fun ConfigScreen(
    uiState: ConfigUiState,
    onBack: () -> Unit,
    onTextChanged: (String) -> Unit,
    onSave: () -> Unit,
    onBackup: () -> Unit,
    onRestore: (ConfigBackup) -> Unit,
    mirrorSupported: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var field by remember(uiState.loadToken) {
        mutableStateOf(TextFieldValue(uiState.text))
    }
    var searchOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var matchIndex by remember { mutableIntStateOf(0) }
    var showBackups by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<ConfigBackup?>(null) }

    val tomlColors = TomlColors(
        comment = Boxy.colors.text2.copy(alpha = 0.7f),
        header = Boxy.colors.accent,
        key = Tints.purple.fg,
        string = Tints.green.fg,
        number = Tints.amber.fg,
        bool = Tints.amber.fg,
    )
    val highlighter = remember(tomlColors) { TomlVisualTransformation(tomlColors) }

    val matches = remember(field.text, query) {
        if (query.length < 2) emptyList() else findMatches(field.text, query)
    }
    val sizeKb = (uiState.text.toByteArray().size + 512) / 1024
    val subtitle = buildString {
        append(stringResource(R.string.config_title))
        if (!uiState.loading) append(" · ").append(stringResource(R.string.config_size_kb, sizeKb))
        if (uiState.dirty) append(" · ").append(stringResource(R.string.config_unsaved))
    }

    Column(modifier = modifier.fillMaxSize()) {
        SubPageHeader(
            title = stringResource(R.string.nav_config),
            subtitle = subtitle,
            onBack = onBack,
        ) {
            HeaderAction(
                icon = painterResource(R.drawable.ic_search),
                contentDescription = stringResource(R.string.config_search_hint),
                tint = if (searchOpen) Boxy.colors.accent else Boxy.colors.text,
                onClick = {
                    searchOpen = !searchOpen
                    if (!searchOpen) query = ""
                },
            )
            HeaderAction(
                icon = painterResource(R.drawable.ic_save),
                contentDescription = stringResource(R.string.config_save_restart),
                tint = if (uiState.dirty) Boxy.colors.accent else Boxy.colors.text,
                enabled = !uiState.busy && !uiState.loading,
                onClick = onSave,
            )
            Box {
                HeaderAction(
                    icon = painterResource(R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.action_more),
                    enabled = !uiState.busy && !uiState.loading,
                    onClick = { menuOpen = true },
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = Boxy.colors.card,
                ) {
                    MenuItem(R.drawable.ic_backup, stringResource(R.string.config_create_backup)) {
                        menuOpen = false
                        onBackup()
                    }
                    MenuItem(R.drawable.ic_restore, stringResource(R.string.config_backups)) {
                        menuOpen = false
                        showBackups = true
                    }
                    // Since module 2.3 there is no shared-storage copy to exchange with.
                    if (mirrorSupported) {
                        MenuItem(R.drawable.ic_upload, stringResource(R.string.config_export)) {
                            menuOpen = false
                            onExport()
                        }
                        MenuItem(R.drawable.ic_download, stringResource(R.string.config_import)) {
                            menuOpen = false
                            onImport()
                        }
                    }
                }
            }
        }

        if (uiState.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Column
        }

        if (searchOpen) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = PageInset).padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchField(
                    query = query,
                    hint = stringResource(R.string.config_search_hint),
                    onQuery = {
                        query = it
                        matchIndex = 0
                    },
                    modifier = Modifier.weight(1f),
                    trailingText = when {
                        query.length < 2 -> null
                        matches.isEmpty() -> stringResource(R.string.config_no_matches)
                        else -> stringResource(R.string.config_match_count, matchIndex + 1, matches.size)
                    },
                )
                if (query.length >= 2) {
                    JumpButton(R.drawable.ic_arrow_up, R.string.action_previous_match, matches.isNotEmpty()) {
                        matchIndex = (matchIndex + matches.size - 1) % matches.size
                        val start = matches[matchIndex]
                        field = field.copy(selection = TextRange(start, start + query.length))
                    }
                    JumpButton(R.drawable.ic_arrow_down, R.string.action_next_match, matches.isNotEmpty()) {
                        matchIndex = (matchIndex + 1) % matches.size
                        val start = matches[matchIndex]
                        field = field.copy(selection = TextRange(start, start + query.length))
                    }
                }
            }
        }

        if (uiState.busy) {
            LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = PageInset, vertical = 4.dp))
        }

        if (uiState.error != null) {
            NoticeCard(
                title = stringResource(R.string.config_error_title),
                body = uiState.error,
                tint = Tints.red,
                icon = painterResource(R.drawable.ic_warning),
                modifier = Modifier.padding(horizontal = PageInset).padding(bottom = 10.dp),
            )
        }

        TextField(
            value = field,
            onValueChange = {
                field = it
                onTextChanged(it.text)
            },
            textStyle = MonospaceTextStyle.copy(color = Boxy.colors.text),
            visualTransformation = highlighter,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = PageInset)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(24.dp)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Boxy.colors.card,
                unfocusedContainerColor = Boxy.colors.card,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
    }

    if (showBackups) {
        BackupSheet(
            backups = uiState.backups,
            onDismiss = { showBackups = false },
            onPick = { pendingRestore = it },
        )
    }

    pendingRestore?.let { backup ->
        BoxyDialog(
            title = stringResource(R.string.confirm_restore_title),
            text = stringResource(R.string.confirm_restore_message, backup.name),
            onDismiss = { pendingRestore = null },
            dismissText = stringResource(R.string.action_cancel),
            confirmText = stringResource(R.string.action_restore),
            confirmTint = Tints.amber,
            onConfirm = {
                pendingRestore = null
                showBackups = false
                onRestore(backup)
            },
        )
    }
}

@Composable
private fun MenuItem(icon: Int, text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, color = Boxy.colors.text) },
        leadingIcon = { Icon(painterResource(icon), null, tint = Boxy.colors.text) },
        onClick = onClick,
    )
}

@Composable
private fun JumpButton(icon: Int, description: Int, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(painterResource(icon), contentDescription = stringResource(description), tint = Boxy.colors.text)
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = Boxy.colors.page,
        contentColor = Boxy.colors.text,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.backups_title),
                style = MaterialTheme.typography.titleLarge,
                color = Boxy.colors.text,
            )
            Spacer(Modifier.height(12.dp))
            if (backups.isEmpty()) {
                Text(
                    text = stringResource(R.string.backups_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Boxy.colors.text2,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(backups, key = { it.path }) { backup ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Boxy.colors.card)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = backup.name, style = MonospaceTextStyle, color = Boxy.colors.text)
                                if (backup.isPending) {
                                    Text(
                                        text = stringResource(R.string.backup_pending),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Tints.amber.fg,
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            ActionPill(
                                stringResource(R.string.action_restore),
                                Tints.blue,
                                enabled = true,
                                modifier = Modifier.width(120.dp),
                                onClick = { onPick(backup) },
                            )
                        }
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
