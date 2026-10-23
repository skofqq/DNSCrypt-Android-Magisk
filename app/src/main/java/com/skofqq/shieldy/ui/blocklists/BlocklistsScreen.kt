package com.skofqq.shieldy.ui.blocklists

import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.skofqq.shieldy.R
import com.skofqq.shieldy.data.Blocklist
import com.skofqq.shieldy.data.BlocklistPresets
import com.skofqq.shieldy.ui.components.ActionPill
import com.skofqq.shieldy.ui.components.BoxyDialog
import com.skofqq.shieldy.ui.components.CardInset
import com.skofqq.shieldy.ui.components.LocalBarPadding
import com.skofqq.shieldy.ui.components.NavRow
import com.skofqq.shieldy.ui.components.NoticeCard
import com.skofqq.shieldy.ui.components.PageInset
import com.skofqq.shieldy.ui.components.SectionCard
import com.skofqq.shieldy.ui.components.SubPageHeader
import com.skofqq.shieldy.ui.components.SwitchRow
import com.skofqq.shieldy.ui.theme.Tints
import java.text.NumberFormat

/** Blocklists sub-page: which lists feed blocked-names.txt and when they refresh. */
@Composable
fun BlocklistsScreen(
    uiState: BlocklistsUiState,
    onBack: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onAdd: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onUpdateNow: () -> Unit,
    onAutoUpdate: (Boolean) -> Unit,
    onUnmeteredOnly: (Boolean) -> Unit,
    onChargingOnly: (Boolean) -> Unit,
    onRestartNow: () -> Unit,
    onDismissRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Blocklist?>(null) }
    val settings = uiState.settings
    val numbers = remember { NumberFormat.getIntegerInstance() }

    Column(modifier.fillMaxSize()) {
        SubPageHeader(
            title = stringResource(R.string.blocklists_title),
            subtitle = settings.mergedEntries?.let { stringResource(R.string.blocklists_total, numbers.format(it)) },
            onBack = onBack,
        )
        // Above the list, not in it: an item inserted before the first visible one opens
        // off screen, and this offer has to be seen.
        AnimatedVisibility(
            visible = uiState.restartOffer,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            NoticeCard(
                title = stringResource(R.string.exclusions_restart_title),
                body = stringResource(R.string.blocklists_restart_body),
                tint = Tints.amber,
                icon = painterResource(R.drawable.ic_refresh),
                modifier = Modifier.padding(start = PageInset, end = PageInset, bottom = 10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionPill(stringResource(R.string.update_later), Tints.gray, true, Modifier.weight(1f), onDismissRestart)
                    ActionPill(stringResource(R.string.exclusions_restart_now), Tints.amber, true, Modifier.weight(1f), onRestartNow)
                }
            }
        }
        Box(Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = PageInset,
                    end = PageInset,
                    top = 4.dp,
                    bottom = LocalBarPadding.current + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "lists") {
                    SectionCard(
                        title = stringResource(R.string.blocklists_lists_header),
                        subtitle = stringResource(R.string.blocklists_lists_sub),
                    ) {
                        settings.lists.forEach { list ->
                            SwitchRow(
                                icon = painterResource(if (BlocklistPresets.isPreset(list.id)) R.drawable.ic_shield else R.drawable.ic_globe),
                                title = list.name,
                                subtitle = listStatus(list, numbers),
                                checked = list.enabled,
                                enabled = !uiState.updating,
                                onCheckedChange = { onToggle(list.id, it) },
                            )
                        }
                        val custom = settings.lists.filterNot { BlocklistPresets.isPreset(it.id) }
                        custom.forEach { list ->
                            NavRow(
                                icon = painterResource(R.drawable.ic_delete),
                                title = stringResource(R.string.blocklists_remove, list.name),
                                iconTint = Tints.red.fg,
                                enabled = !uiState.updating,
                                onClick = { removing = list },
                            )
                        }
                        NavRow(
                            icon = painterResource(R.drawable.ic_download),
                            title = stringResource(R.string.blocklists_add),
                            subtitle = stringResource(R.string.blocklists_add_sub),
                            showDivider = false,
                            enabled = !uiState.updating,
                            onClick = { adding = true },
                        )
                    }
                }

                item(key = "updates") {
                    SectionCard(
                        title = stringResource(R.string.blocklists_updates_header),
                        subtitle = stringResource(R.string.blocklists_updates_sub),
                    ) {
                        NavRow(
                            icon = painterResource(R.drawable.ic_refresh),
                            title = stringResource(if (uiState.updating) R.string.blocklists_updating else R.string.blocklists_update_now),
                            subtitle = if (settings.enabledCount == 0) stringResource(R.string.blocklists_none_enabled) else null,
                            enabled = !uiState.updating,
                            onClick = onUpdateNow,
                        )
                        if (uiState.updating) {
                            LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = CardInset, vertical = 6.dp))
                        }
                        SwitchRow(
                            icon = painterResource(R.drawable.ic_schedule),
                            title = stringResource(R.string.blocklists_auto),
                            subtitle = stringResource(R.string.blocklists_auto_sub),
                            checked = settings.autoUpdate,
                            onCheckedChange = onAutoUpdate,
                        )
                        SwitchRow(
                            icon = painterResource(R.drawable.ic_hotspot),
                            title = stringResource(R.string.blocklists_unmetered),
                            checked = settings.unmeteredOnly,
                            enabled = settings.autoUpdate,
                            onCheckedChange = onUnmeteredOnly,
                        )
                        SwitchRow(
                            icon = painterResource(R.drawable.ic_power),
                            title = stringResource(R.string.blocklists_charging),
                            checked = settings.chargingOnly,
                            enabled = settings.autoUpdate,
                            showDivider = false,
                            onCheckedChange = onChargingOnly,
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        AddListDialog(
            onAdd = { name, url ->
                adding = false
                onAdd(name, url)
            },
            onDismiss = { adding = false },
        )
    }
    removing?.let { list ->
        BoxyDialog(
            title = stringResource(R.string.blocklists_remove, list.name),
            text = list.url,
            onDismiss = { removing = null },
            dismissText = stringResource(R.string.action_cancel),
            confirmText = stringResource(R.string.blocklists_remove_confirm),
            confirmTint = Tints.red,
            onConfirm = {
                removing = null
                onRemove(list.id)
            },
        )
    }
}

@Composable
private fun listStatus(list: Blocklist, numbers: NumberFormat): String = when {
    list.error != null && list.entries == null -> stringResource(R.string.blocklists_failed, list.error)
    list.entries == null || list.updatedAt == null -> stringResource(R.string.blocklists_never)
    else -> {
        val ago = DateUtils.getRelativeTimeSpanString(list.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        stringResource(R.string.blocklists_entries, numbers.format(list.entries), ago) +
            (list.error?.let { "\n" + stringResource(R.string.blocklists_failed, it) } ?: "")
    }
}

@Composable
private fun AddListDialog(onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    val urlValid = url.startsWith("https://") && url.length > "https://".length + 3 && ' ' !in url
    BoxyDialog(
        title = stringResource(R.string.blocklists_add),
        onDismiss = onDismiss,
        dismissText = stringResource(R.string.action_cancel),
        confirmText = stringResource(R.string.blocklists_add_confirm),
        onConfirm = {
            if (urlValid) onAdd(name.trim().ifEmpty { url.substringAfter("://").substringBefore('/') }, url.trim())
        },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            label = { Text(stringResource(R.string.blocklists_add_name)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            singleLine = true,
            isError = !urlValid,
            label = { Text(stringResource(R.string.blocklists_add_url)) },
            supportingText = { if (!urlValid) Text(stringResource(R.string.blocklists_add_invalid)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}
