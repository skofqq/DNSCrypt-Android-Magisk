package io.github.skofqq.dnscryptmanager.ui.servers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import io.github.skofqq.dnscryptmanager.R
import io.github.skofqq.dnscryptmanager.data.ActiveServer
import io.github.skofqq.dnscryptmanager.ui.components.BoxyCard
import io.github.skofqq.dnscryptmanager.ui.components.CardTitle
import io.github.skofqq.dnscryptmanager.ui.theme.Boxy
import io.github.skofqq.dnscryptmanager.ui.theme.Tints
import io.github.skofqq.dnscryptmanager.data.ModuleContract
import io.github.skofqq.dnscryptmanager.data.ResolverEntry
import io.github.skofqq.dnscryptmanager.data.StampProtocol

@Composable
fun ServersScreen(
    uiState: ServersUiState,
    supported: Boolean,
    onQuery: (String) -> Unit,
    onSetAuto: (Boolean) -> Unit,
    onToggleProtocol: (StampProtocol) -> Unit,
    onToggleDnssec: () -> Unit,
    onToggleNoLog: () -> Unit,
    onToggleNoFilter: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onFocus: (String) -> Unit,
    onMove: (Boolean) -> Unit,
    onDiscard: () -> Unit,
    onApply: () -> Unit,
    onRefresh: () -> Unit,
    onOpenReleases: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!supported) {
        UnsupportedNotice(
            onOpenReleases = onOpenReleases,
            modifier = modifier,
        )
        return
    }

    if (uiState.loading && uiState.catalog.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        return
    }

    var confirmEmpty by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            ActiveServerCard(uiState.activeServer)

            ModeSelector(auto = uiState.draftAuto, onSetAuto = onSetAuto)

            if (uiState.draftAuto) {
                Text(
                    text = stringResource(R.string.servers_auto_explained),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (uiState.cacheMissing) {
                CacheMissingCard(error = uiState.error, onRefresh = onRefresh)
            } else {
                if (uiState.isStale) {
                    Text(
                        text = stringResource(R.string.servers_cache_stale),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                FilterRow(
                    uiState = uiState,
                    onToggleProtocol = onToggleProtocol,
                    onToggleDnssec = onToggleDnssec,
                    onToggleNoLog = onToggleNoLog,
                    onToggleNoFilter = onToggleNoFilter,
                )

                ResolverPicker(uiState = uiState, onQuery = onQuery, onAdd = onAdd)

                SelectedServers(
                    uiState = uiState,
                    onRemove = onRemove,
                    onFocus = onFocus,
                    onMove = onMove,
                )
            }

            Spacer(Modifier.height(96.dp))
        }

        AnimatedVisibility(
            visible = uiState.changeCount > 0,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ApplyBar(
                changes = uiState.changeCount,
                applying = uiState.applying,
                onDiscard = onDiscard,
                onApply = {
                    if (uiState.emptyManualSelection) confirmEmpty = true else onApply()
                },
            )
        }
    }

    if (confirmEmpty) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(stringResource(R.string.servers_empty_title)) },
            text = { Text(stringResource(R.string.servers_empty_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    onSetAuto(true)
                    onApply()
                }) { Text(stringResource(R.string.servers_mode_auto)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun UnsupportedNotice(onOpenReleases: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_dns),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.servers_unsupported_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                R.string.servers_unsupported_body,
                ModuleContract.MIN_SERVERS_VERSION,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onOpenReleases) {
            Text(stringResource(R.string.action_open_releases))
        }
    }
}

@Composable
private fun ActiveServerCard(active: String?) {
    val parsed = active?.let { ActiveServer.parse(it) }
    // Boxy's latency colours: green is fast, amber is usable, red is slow.
    val rttTint = parsed?.rttMs?.let {
        when {
            it < 100 -> Tints.green
            it < 300 -> Tints.amber
            else -> Tints.red
        }
    }
    BoxyCard {
        Column {
            CardTitle(
                stringResource(R.string.servers_active_title),
                badge = parsed?.rttMs?.let { stringResource(R.string.servers_active_rtt, it) },
                tint = rttTint,
            )
            Spacer(Modifier.height(8.dp))
            if (parsed != null) {
                Text(
                    text = parsed.name,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp),
                    color = Boxy.colors.text,
                )
                parsed.liveServers?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = pluralStringResource(R.plurals.servers_active_live, it, it),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Boxy.colors.surface2)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = Boxy.colors.text,
                    )
                }
            } else {
                // Unknown wording: show the log line as it is rather than nothing.
                Text(
                    text = active ?: stringResource(R.string.value_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (active != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (active == null) {
                Text(
                    text = stringResource(R.string.servers_active_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ModeSelector(auto: Boolean, onSetAuto: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = auto,
            onClick = { onSetAuto(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text(stringResource(R.string.servers_mode_auto)) }
        SegmentedButton(
            selected = !auto,
            onClick = { onSetAuto(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text(stringResource(R.string.servers_mode_manual)) }
    }
}

@Composable
private fun CacheMissingCard(error: String?, onRefresh: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.servers_cache_missing_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.servers_cache_missing_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            if (error != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRefresh) {
                Text(stringResource(R.string.action_refresh))
            }
        }
    }
}

@Composable
private fun FilterRow(
    uiState: ServersUiState,
    onToggleProtocol: (StampProtocol) -> Unit,
    onToggleDnssec: () -> Unit,
    onToggleNoLog: () -> Unit,
    onToggleNoFilter: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(StampProtocol.DNSCRYPT, StampProtocol.DOH, StampProtocol.ODOH_TARGET).forEach { protocol ->
            FilterChip(
                selected = protocol in uiState.protocols,
                onClick = { onToggleProtocol(protocol) },
                label = { Text(protocolLabel(protocol)) },
            )
        }
        FilterChip(
            selected = uiState.requireDnssec,
            onClick = onToggleDnssec,
            label = { Text(stringResource(R.string.servers_flag_dnssec)) },
        )
        FilterChip(
            selected = uiState.requireNoLog,
            onClick = onToggleNoLog,
            label = { Text(stringResource(R.string.servers_flag_nolog)) },
        )
        FilterChip(
            selected = uiState.requireNoFilter,
            onClick = onToggleNoFilter,
            label = { Text(stringResource(R.string.servers_flag_nofilter)) },
        )
    }
}

@Composable
private fun ResolverPicker(
    uiState: ServersUiState,
    onQuery: (String) -> Unit,
    onAdd: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    // Filtering runs over the already parsed list, never over the raw markdown.
    val filtered by remember(uiState) {
        derivedStateOf {
            uiState.catalog.asSequence()
                .filter { entry ->
                    (uiState.protocols.isEmpty() || entry.protocol in uiState.protocols) &&
                        (!uiState.requireDnssec || entry.dnssec) &&
                        (!uiState.requireNoLog || entry.noLog) &&
                        (!uiState.requireNoFilter || entry.noFilter) &&
                        (uiState.query.isBlank() || entry.matches(uiState.query.trim()))
                }
                .toList()
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = {
                onQuery(it)
                expanded = true
            },
            singleLine = true,
            label = { Text(stringResource(R.string.servers_search_label)) },
            supportingText = {
                Text(stringResource(R.string.servers_found, filtered.size, uiState.catalog.size))
            },
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        painter = painterResource(
                            if (expanded) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down
                        ),
                        contentDescription = stringResource(R.string.servers_search_label),
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                // An editable anchor does not expand on its own: tapping the field only
                // focuses it, so the list is opened here.
                .onFocusChanged { if (it.isFocused) expanded = true }
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            // A menu measures its content with IntrinsicSize.Max, and a lazy list cannot
            // answer an intrinsic query, so the rows are laid out eagerly and capped.
            val shown = filtered.take(MAX_MENU_ROWS)
            if (shown.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.config_no_matches)) },
                    onClick = { expanded = false },
                )
            }
            shown.forEach { entry ->
                key(entry.name) {
                    DropdownMenuItem(
                        text = {
                            ResolverRow(entry, selected = entry.name in uiState.draftSelection)
                        },
                        onClick = {
                            onAdd(entry.name)
                            expanded = false
                        },
                    )
                }
            }
            if (filtered.size > shown.size) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(
                                R.string.servers_more_results,
                                shown.size,
                                filtered.size,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = {},
                    enabled = false,
                )
            }
        }
    }
}

/** How many rows the popup lays out at once; the search narrows the rest. */
private const val MAX_MENU_ROWS = 60

@Composable
private fun ResolverRow(entry: ResolverEntry, selected: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = protocolLabel(entry.protocol),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        val flags = buildList {
            if (entry.dnssec) add(stringResource(R.string.servers_flag_dnssec))
            if (entry.noLog) add(stringResource(R.string.servers_flag_nolog))
            if (entry.noFilter) add(stringResource(R.string.servers_flag_nofilter))
        }
        if (flags.isNotEmpty()) {
            Text(
                text = flags.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        if (entry.summary.isNotEmpty()) {
            Text(
                text = entry.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SelectedServers(
    uiState: ServersUiState,
    onRemove: (String) -> Unit,
    onFocus: (String) -> Unit,
    onMove: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.servers_selected_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { onMove(true) },
                enabled = uiState.focusedServer != null &&
                    uiState.draftSelection.indexOf(uiState.focusedServer) > 0,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_up),
                    contentDescription = stringResource(R.string.servers_move_up),
                )
            }
            IconButton(
                onClick = { onMove(false) },
                enabled = uiState.focusedServer != null &&
                    uiState.draftSelection.indexOf(uiState.focusedServer) <
                    uiState.draftSelection.lastIndex,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_down),
                    contentDescription = stringResource(R.string.servers_move_down),
                )
            }
        }

        if (uiState.draftSelection.isEmpty()) {
            Text(
                text = stringResource(R.string.servers_selected_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Text(
            text = stringResource(R.string.servers_order_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            uiState.draftSelection.forEachIndexed { index, name ->
                val known = uiState.isKnown(name)
                InputChip(
                    selected = uiState.focusedServer == name,
                    onClick = { onFocus(name) },
                    label = { Text("${index + 1}. $name") },
                    leadingIcon = if (known) null else {
                        {
                            Icon(
                                painter = painterResource(R.drawable.ic_warning),
                                contentDescription = stringResource(R.string.servers_unknown_chip),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    trailingIcon = {
                        IconButton(onClick = { onRemove(name) }, modifier = Modifier.size(20.dp)) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.action_clear),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                )
            }
        }

        if (uiState.unknownSelected.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.servers_unknown_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ApplyBar(
    changes: Int,
    applying: Boolean,
    onDiscard: () -> Unit,
    onApply: () -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            if (applying) {
                androidx.compose.material3.LinearWavyProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = androidx.compose.ui.res.pluralStringResource(
                        R.plurals.servers_pending, changes, changes,
                    ),
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
}

@Composable
private fun protocolLabel(protocol: StampProtocol): String = when (protocol) {
    StampProtocol.PLAIN -> stringResource(R.string.protocol_plain)
    StampProtocol.DNSCRYPT -> stringResource(R.string.protocol_dnscrypt)
    StampProtocol.DOH -> stringResource(R.string.protocol_doh)
    StampProtocol.DOT -> stringResource(R.string.protocol_dot)
    StampProtocol.DOQ -> stringResource(R.string.protocol_doq)
    StampProtocol.ODOH_TARGET -> stringResource(R.string.protocol_odoh)
    StampProtocol.DNSCRYPT_RELAY, StampProtocol.ODOH_RELAY -> stringResource(R.string.protocol_relay)
    StampProtocol.UNKNOWN -> stringResource(R.string.protocol_unknown)
}
