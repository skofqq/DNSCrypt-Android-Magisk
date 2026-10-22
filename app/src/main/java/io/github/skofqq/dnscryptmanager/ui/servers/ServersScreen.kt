package io.github.skofqq.dnscryptmanager.ui.servers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.skofqq.dnscryptmanager.R
import io.github.skofqq.dnscryptmanager.data.ActiveServer
import io.github.skofqq.dnscryptmanager.data.ModuleContract
import io.github.skofqq.dnscryptmanager.data.ResolverEntry
import io.github.skofqq.dnscryptmanager.data.StampProtocol
import io.github.skofqq.dnscryptmanager.ui.components.ActionPill
import io.github.skofqq.dnscryptmanager.ui.components.BoxyChip
import io.github.skofqq.dnscryptmanager.ui.components.BoxyDialog
import io.github.skofqq.dnscryptmanager.ui.components.CardInset
import io.github.skofqq.dnscryptmanager.ui.components.HeaderAction
import io.github.skofqq.dnscryptmanager.ui.components.NoticeCard
import io.github.skofqq.dnscryptmanager.ui.components.PageInset
import io.github.skofqq.dnscryptmanager.ui.components.PendingBar
import io.github.skofqq.dnscryptmanager.ui.components.SearchField
import io.github.skofqq.dnscryptmanager.ui.components.SectionCard
import io.github.skofqq.dnscryptmanager.ui.components.SubPageHeader
import io.github.skofqq.dnscryptmanager.ui.components.TextInset
import io.github.skofqq.dnscryptmanager.ui.components.TopFade
import io.github.skofqq.dnscryptmanager.ui.theme.Boxy
import io.github.skofqq.dnscryptmanager.ui.theme.Tint
import io.github.skofqq.dnscryptmanager.ui.theme.Tints

/**
 * Resolver selection, a sub-page of Settings laid out like Boxy's DNSCrypt servers page:
 * mode switch, filter chips, then one card per resolver with a checkbox.
 */
@Composable
fun ServersScreen(
    uiState: ServersUiState,
    supported: Boolean,
    onBack: () -> Unit,
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
    var searchOpen by remember { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }

    // Filtering runs over the already parsed list, never over the raw markdown.
    val filtered = remember(uiState.catalog, uiState.query, uiState.protocols, uiState.requireDnssec,
        uiState.requireNoLog, uiState.requireNoFilter) {
        uiState.catalog.filter { entry ->
            (uiState.protocols.isEmpty() || entry.protocol in uiState.protocols) &&
                (!uiState.requireDnssec || entry.dnssec) &&
                (!uiState.requireNoLog || entry.noLog) &&
                (!uiState.requireNoFilter || entry.noFilter) &&
                (uiState.query.isBlank() || entry.matches(uiState.query.trim()))
        }
    }
    val manual = !uiState.draftAuto
    val subtitle = when {
        !supported -> null
        uiState.draftAuto -> stringResource(R.string.servers_mode_auto)
        uiState.catalog.isEmpty() -> stringResource(R.string.servers_mode_manual)
        else -> stringResource(R.string.servers_found, filtered.size, uiState.catalog.size)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SubPageHeader(
                title = stringResource(R.string.servers_title),
                subtitle = subtitle,
                onBack = onBack,
            ) {
                if (supported && manual && uiState.catalog.isNotEmpty()) {
                    HeaderAction(
                        icon = painterResource(R.drawable.ic_search),
                        contentDescription = stringResource(R.string.servers_search_label),
                        tint = if (searchOpen) Boxy.colors.accent else Boxy.colors.text,
                        onClick = {
                            searchOpen = !searchOpen
                            if (!searchOpen) onQuery("")
                        },
                    )
                }
                if (supported) {
                    HeaderAction(
                        icon = painterResource(R.drawable.ic_refresh),
                        contentDescription = stringResource(R.string.action_refresh),
                        enabled = !uiState.loading,
                        onClick = onRefresh,
                    )
                }
            }

            when {
                !supported -> UnsupportedNotice(onOpenReleases)
                uiState.loading && uiState.catalog.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                else -> Box(Modifier.weight(1f)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = PageInset,
                            end = PageInset,
                            top = 4.dp,
                            bottom = if (uiState.changeCount > 0) 180.dp else 32.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "mode") {
                            ModeCard(
                                auto = uiState.draftAuto,
                                active = uiState.activeServer?.let { ActiveServer.parse(it) },
                                onSetAuto = onSetAuto,
                            )
                        }

                        if (manual && uiState.cacheMissing) {
                            item(key = "cache") { CacheMissingCard(uiState.error, onRefresh) }
                        }

                        if (manual && !uiState.cacheMissing) {
                            if (uiState.isStale) {
                                item(key = "stale") {
                                    Text(
                                        text = stringResource(R.string.servers_cache_stale),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Boxy.colors.text2,
                                        modifier = Modifier.padding(horizontal = TextInset - PageInset),
                                    )
                                }
                            }
                            if (searchOpen) {
                                item(key = "search") {
                                    SearchField(
                                        query = uiState.query,
                                        hint = stringResource(R.string.servers_search_label),
                                        onQuery = onQuery,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            item(key = "chips") {
                                FilterChips(
                                    uiState = uiState,
                                    onToggleProtocol = onToggleProtocol,
                                    onToggleDnssec = onToggleDnssec,
                                    onToggleNoLog = onToggleNoLog,
                                    onToggleNoFilter = onToggleNoFilter,
                                )
                            }
                            item(key = "chosen") {
                                ChosenCard(uiState, onRemove, onFocus, onMove)
                            }
                            items(filtered, key = { "r:" + it.name }) { entry ->
                                ResolverCard(
                                    entry = entry,
                                    checked = entry.name in uiState.draftSelection,
                                    onToggle = { checked ->
                                        if (checked) onAdd(entry.name) else onRemove(entry.name)
                                    },
                                )
                            }
                        }
                    }
                    TopFade()
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.changeCount > 0,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        ) {
            Column {
                if (uiState.applying) {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 32.dp))
                }
                PendingBar(
                    label = pluralStringResource(R.plurals.servers_pending, uiState.changeCount, uiState.changeCount),
                    applying = uiState.applying,
                    applyText = stringResource(R.string.settings_apply),
                    onDiscard = onDiscard,
                    onApply = { if (uiState.emptyManualSelection) confirmEmpty = true else onApply() },
                )
            }
        }
    }

    if (confirmEmpty) {
        BoxyDialog(
            title = stringResource(R.string.servers_empty_title),
            text = stringResource(R.string.servers_empty_message),
            onDismiss = { confirmEmpty = false },
            dismissText = stringResource(R.string.action_cancel),
            confirmText = stringResource(R.string.servers_mode_auto),
            onConfirm = {
                confirmEmpty = false
                onSetAuto(true)
                onApply()
            },
        )
    }
}

@Composable
private fun UnsupportedNotice(onOpenReleases: () -> Unit) {
    Column(Modifier.padding(horizontal = PageInset)) {
        NoticeCard(
            title = stringResource(R.string.servers_unsupported_title),
            body = stringResource(R.string.servers_unsupported_body, ModuleContract.MIN_SERVERS_VERSION),
            tint = Tints.amber,
            icon = painterResource(R.drawable.ic_dns),
        ) {
            ActionPill(
                stringResource(R.string.action_open_releases),
                Tints.amber,
                enabled = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenReleases,
            )
        }
    }
}

/** Auto / manual as two pills in a tray, with the server the proxy settled on underneath. */
@Composable
private fun ModeCard(auto: Boolean, active: ActiveServer?, onSetAuto: (Boolean) -> Unit) {
    SectionCard(title = null) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Boxy.colors.surface2)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ModePill(stringResource(R.string.servers_mode_auto), auto, Modifier.weight(1f)) { onSetAuto(true) }
            ModePill(stringResource(R.string.servers_mode_manual), !auto, Modifier.weight(1f)) { onSetAuto(false) }
        }
        if (active != null) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.padding(horizontal = CardInset), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.servers_now, active.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Boxy.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                active.rttMs?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.servers_rtt_short, it),
                        style = MaterialTheme.typography.labelLarge,
                        color = rttTint(it).fg,
                    )
                }
            }
        }
        if (auto) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.servers_auto_explained),
                style = MaterialTheme.typography.bodySmall,
                color = Boxy.colors.text2,
                modifier = Modifier.padding(horizontal = CardInset),
            )
        }
    }
}

@Composable
private fun ModePill(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Boxy.colors.card else Boxy.colors.surface2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) Boxy.colors.accent else Boxy.colors.text2,
            maxLines = 1,
        )
    }
}

/** Boxy's latency colours: green is fast, amber is usable, red is slow. */
@Composable
fun rttTint(ms: Int): Tint = when {
    ms < 100 -> Tints.green
    ms < 300 -> Tints.amber
    else -> Tints.red
}

@Composable
private fun CacheMissingCard(error: String?, onRefresh: () -> Unit) {
    NoticeCard(
        title = stringResource(R.string.servers_cache_missing_title),
        body = listOfNotNull(stringResource(R.string.servers_cache_missing_body), error).joinToString("\n"),
        tint = Tints.amber,
        icon = painterResource(R.drawable.ic_warning),
    ) {
        ActionPill(
            stringResource(R.string.action_refresh),
            Tints.amber,
            enabled = true,
            modifier = Modifier.fillMaxWidth(),
            onClick = onRefresh,
        )
    }
}

@Composable
private fun FilterChips(
    uiState: ServersUiState,
    onToggleProtocol: (StampProtocol) -> Unit,
    onToggleDnssec: () -> Unit,
    onToggleNoLog: () -> Unit,
    onToggleNoFilter: () -> Unit,
) {
    // Bleeds to the screen edges so chips scroll out instead of being cut at the card line.
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val bleed = PageInset.roundToPx()
                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth = constraints.maxWidth + 2 * bleed,
                        maxWidth = constraints.maxWidth + 2 * bleed,
                    )
                )
                layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed, 0) }
            },
        contentPadding = PaddingValues(horizontal = PageInset),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(listOf(StampProtocol.DNSCRYPT, StampProtocol.DOH, StampProtocol.ODOH_TARGET)) { protocol ->
            BoxyChip(protocolLabel(protocol), protocol in uiState.protocols) { onToggleProtocol(protocol) }
        }
        item { BoxyChip(stringResource(R.string.servers_flag_nolog), uiState.requireNoLog, Tints.green.fg, onClick = onToggleNoLog) }
        item { BoxyChip(stringResource(R.string.servers_flag_dnssec), uiState.requireDnssec, Tints.teal.fg, onClick = onToggleDnssec) }
        item { BoxyChip(stringResource(R.string.servers_flag_nofilter), uiState.requireNoFilter, Tints.purple.fg, onClick = onToggleNoFilter) }
    }
}

/** The chosen servers in order, each with its own reorder and remove buttons. */
@Composable
private fun ChosenCard(
    uiState: ServersUiState,
    onRemove: (String) -> Unit,
    onFocus: (String) -> Unit,
    onMove: (Boolean) -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.servers_selected_title),
        subtitle = stringResource(
            if (uiState.draftSelection.isEmpty()) R.string.servers_selected_empty else R.string.servers_order_hint
        ),
    ) {
        uiState.draftSelection.forEachIndexed { index, name ->
            val known = uiState.isKnown(name)
            Row(
                Modifier.fillMaxWidth().padding(start = CardInset, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Boxy.colors.surface2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = Boxy.colors.text)
                }
                Spacer(Modifier.width(12.dp))
                if (!known) {
                    Icon(
                        painterResource(R.drawable.ic_warning),
                        stringResource(R.string.servers_unknown_chip),
                        Modifier.size(16.dp),
                        tint = Tints.amber.fg,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Boxy.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                SmallIcon(R.drawable.ic_arrow_up, R.string.servers_move_up, enabled = index > 0) {
                    onFocus(name)
                    onMove(true)
                }
                SmallIcon(R.drawable.ic_arrow_down, R.string.servers_move_down, enabled = index < uiState.draftSelection.lastIndex) {
                    onFocus(name)
                    onMove(false)
                }
                SmallIcon(R.drawable.ic_close, R.string.action_clear, enabled = true) { onRemove(name) }
            }
        }
        if (uiState.unknownSelected.isNotEmpty()) {
            Text(
                text = stringResource(R.string.servers_unknown_note),
                style = MaterialTheme.typography.bodySmall,
                color = Boxy.colors.text2,
                modifier = Modifier.padding(horizontal = CardInset, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SmallIcon(icon: Int, description: Int, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(50)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            stringResource(description),
            Modifier.size(20.dp),
            tint = Boxy.colors.text.copy(alpha = if (enabled) 1f else 0.3f),
        )
    }
}

/** One resolver: checkbox, name, two lines of description and its properties as pills. */
@Composable
private fun ResolverCard(entry: ResolverEntry, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Boxy.colors.card)
            .clickable { onToggle(!checked) }
            .padding(start = 6.dp, end = CardInset, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(checkedColor = Boxy.colors.accent),
        )
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.titleMedium,
                color = Boxy.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.summary.isNotEmpty()) {
                Text(
                    entry.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = Boxy.colors.text2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TagPill(protocolLabel(entry.protocol), Tints.blue)
                if (entry.noLog) TagPill(stringResource(R.string.servers_flag_nolog), Tints.green)
                if (entry.dnssec) TagPill(stringResource(R.string.servers_flag_dnssec), Tints.teal)
                if (entry.noFilter) TagPill(stringResource(R.string.servers_flag_nofilter), Tints.purple)
            }
        }
    }
}

@Composable
private fun TagPill(text: String, tint: Tint) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(tint.bg).padding(horizontal = 10.dp, vertical = 3.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = tint.fg,
        maxLines = 1,
    )
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
